package fr.sakura.bot.listeners;

import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.core.util.MdcContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Gère l'envoi des messages de bienvenue personnalisés.
 */
public class WelcomeListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(WelcomeListener.class);

    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
            "(?i)^https://[\\w\\-.]+(:[0-9]+)?(/[\\w\\-./_%~:@!$&'()*+,;=?#]*)?$"
    );

    private static final String DEFAULT_WELCOME_GIF =
            "https://media2.giphy.com/media/v1.Y2lkPTc5MGI3NjExZjhwMjNzNXUzc3l6bHMyOTA5cmw1eXgyZ3l4Y3IxeHRhZzB1YW0yaCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/AdtvZ8gu9gZ32/giphy.gif";

    private final SettingsManager settingsManager;
    private final String defaultWelcomeChannelId;
    private final String defaultWelcomeImageUrl;

    public WelcomeListener(SettingsManager settingsManager, String welcomeChannelId, String welcomeImageUrl) {
        this.settingsManager = settingsManager;
        this.defaultWelcomeChannelId = welcomeChannelId;
        this.defaultWelcomeImageUrl = welcomeImageUrl;
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        try (var ignored = MdcContext.of("guildId", event.getGuild().getId(), "userId", event.getUser().getId())) {
            String guildId = event.getGuild().getId();
            
            // Priorité : Base de données > .env
            String welcomeChannelId = settingsManager.getWelcomeChannelId(guildId)
                    .or(() -> Optional.ofNullable(defaultWelcomeChannelId))
                    .orElse(null);

            if (welcomeChannelId == null || welcomeChannelId.isBlank()) {
                logger.debug("Message de bienvenue ignoré : aucun salon configuré");
                return;
            }

            TextChannel channel = event.getGuild().getTextChannelById(welcomeChannelId);
            if (channel == null) {
                logger.warn("Salon de bienvenue introuvable : channelId={}", welcomeChannelId);
                return;
            }

            var user   = event.getUser();
            var guild  = event.getGuild();
            var member = event.getMember();
            int memberNumber = guild.getMemberCount();

            // Résolution de l'image (DB > .env > Default GIF)
            String configuredImage = settingsManager.getWelcomeImageUrl(guildId)
                    .or(() -> Optional.ofNullable(defaultWelcomeImageUrl))
                    .orElse(null);
            
            String bannerUrl = resolveImageUrl(configuredImage);

            EmbedBuilder embed = EmbedStyle.newInfoEmbed("🌸", "Bienvenue sur " + guild.getName() + " !");

            embed.setDescription(
                    "✨ " + member.getAsMention() + " vient de rejoindre le serveur !\n\n" +
                            "Nous sommes ravis de t'accueillir parmi nous. N'hésite pas à te présenter et à lire les règles du serveur. 🎉"
            );

            String avatarUrl = user.getEffectiveAvatarUrl();
            if (avatarUrl != null) {
                embed.setThumbnail(avatarUrl + "?size=256");
            }

            if (bannerUrl != null) {
                embed.setImage(bannerUrl);
            }

            EmbedStyle.setFooter(
                    embed,
                    "Membre n°" + memberNumber + " • Bienvenu(e) !",
                    guild.getIconUrl()
            );

            channel.sendMessage("<@&1496923591349112903>")
                    .setEmbeds(embed.build())
                    .queue(
                            success -> logger.info("Message de bienvenue envoyé"),
                            error -> logger.error("Échec envoi message de bienvenue", error)
                    );
        }
    }

    private String resolveImageUrl(String rawUrl) {
        String cleaned = sanitize(rawUrl);
        if (isValidHttpsUrl(cleaned)) {
            return cleaned;
        }

        if (cleaned != null) {
            logger.warn("URL d'image de bienvenue invalide ou non-HTTPS : \"{}\"", cleaned);
        }

        String fallback = sanitize(DEFAULT_WELCOME_GIF);
        if (isValidHttpsUrl(fallback)) {
            return fallback;
        }

        return null;
    }

    private String sanitize(String raw) {
        if (raw == null) return null;
        String s = raw.strip();
        if (s.isEmpty()) return null;

        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1).strip();
        }
        if (s.startsWith("<") && s.endsWith(">")) {
            s = s.substring(1, s.length() - 1).strip();
        }

        return s.isEmpty() ? null : s;
    }

    public static boolean isValidHttpsUrl(String url) {
        if (url == null) return false;
        try {
            URI uri = new URI(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && IMAGE_URL_PATTERN.matcher(url).matches();
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
