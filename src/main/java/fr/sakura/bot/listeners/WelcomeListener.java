package fr.sakura.bot.listeners;

import fr.sakura.bot.utils.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public class WelcomeListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(WelcomeListener.class);
    private static final DateTimeFormatter HOUR_MINUTE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FOOTER_DATE_FORMATTER  = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Pattern IMAGE_EXTENSION_PATTERN =
            Pattern.compile("(?i).+\\.(gif|png|jpe?g|webp)(\\?.*)?$");

    /** GIF de bienvenue utilisé si WELCOME_IMAGE_URL n'est pas défini dans .env */
    private static final String DEFAULT_WELCOME_GIF_URL =
            "https://i.pinimg.com/originals/7d/0a/f4/7d0af406e9952e26c1611dbbc611a0fc.gif";

    private final String welcomeChannelId;
    private final String welcomeImageUrl;

    public WelcomeListener(String welcomeChannelId, String welcomeImageUrl) {
        this.welcomeChannelId = welcomeChannelId;
        this.welcomeImageUrl = welcomeImageUrl;
    }

    private String resolveWelcomeImageUrl() {
        String configuredUrl = sanitizeUrl(welcomeImageUrl);
        if (isEmbeddableImageUrl(configuredUrl)) {
            return configuredUrl;
        }

        if (configuredUrl != null) {
            logger.warn("WELCOME_IMAGE_URL invalide/non supportee pour un embed Discord: {}", configuredUrl);
        }

        String fallbackUrl = sanitizeUrl(DEFAULT_WELCOME_GIF_URL);
        if (isEmbeddableImageUrl(fallbackUrl)) {
            return fallbackUrl;
        }

        logger.error("Aucune URL d'image de bienvenue exploitable n'a ete trouvee");
        return null;
    }

    private String sanitizeUrl(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }

        String sanitized = rawUrl.trim();
        if (sanitized.isEmpty()) {
            return null;
        }

        // Tolerate quoted env values: "https://..." or <https://...>
        if ((sanitized.startsWith("\"") && sanitized.endsWith("\""))
                || (sanitized.startsWith("'") && sanitized.endsWith("'"))) {
            sanitized = sanitized.substring(1, sanitized.length() - 1).trim();
        }
        if (sanitized.startsWith("<") && sanitized.endsWith(">")) {
            sanitized = sanitized.substring(1, sanitized.length() - 1).trim();
        }

        return sanitized.isEmpty() ? null : sanitized;
    }

    private boolean isEmbeddableImageUrl(String url) {
        if (url == null) {
            return false;
        }

        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
                return false;
            }
            return IMAGE_EXTENSION_PATTERN.matcher(url).matches();
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        if (welcomeChannelId == null || welcomeChannelId.isEmpty()) {
            logger.debug("Message de bienvenue ignore: WELCOME_CHANNEL_ID non configure");
            return;
        }

        TextChannel channel = event.getGuild().getTextChannelById(welcomeChannelId);
        if (channel == null) {
            logger.warn("Salon de bienvenue introuvable: {}", welcomeChannelId);
            return;
        }

        logger.info("Nouveau membre: {} ({}) dans {} ({})",
                event.getUser().getName(),
                event.getUser().getId(),
                event.getGuild().getName(),
                event.getGuild().getId());

        int memberCount = event.getGuild().getMemberCount();

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("🌸", "Bienvenue !");
        embed.setDescription("Bienvenue " + event.getMember().getAsMention() + " sur **" + event.getGuild().getName() + "** !");

        if (event.getMember().getUser().getAvatarUrl() != null) {
            embed.setThumbnail(event.getMember().getUser().getAvatarUrl());
        }

        String imageUrl = resolveWelcomeImageUrl();
        if (imageUrl != null) {
            embed.setImage(imageUrl);
        }

        String arrivalTime = event.getMember().getTimeJoined().toLocalTime().format(HOUR_MINUTE_FORMATTER);
        String fullDate    = event.getMember().getTimeJoined().format(FOOTER_DATE_FORMATTER);
        EmbedStyle.setFooter(
                embed,
                "📥 Arrivée à " + arrivalTime + " • Membre n°" + memberCount + " • " + fullDate,
                event.getGuild().getIconUrl()
        );

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> logger.info("Message de bienvenue envoye pour {} dans #{}",
                        event.getUser().getId(), channel.getName()),
                error -> logger.error("Echec envoi message de bienvenue pour {}", event.getUser().getId(), error)
        );
    }
}
