package fr.sakura.bot.listeners;

import fr.sakura.bot.utils.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;

public class WelcomeListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(WelcomeListener.class);
    private static final DateTimeFormatter HOUR_MINUTE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FOOTER_DATE_FORMATTER  = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** GIF de bienvenue utilisé si WELCOME_IMAGE_URL n'est pas défini dans .env */
    private static final String DEFAULT_WELCOME_GIF_URL =
            "https://cdn.discordapp.com/attachments/1403497537914146908/1456657012787122261/1CDB944C-1FB2-4C6E-8620-73F2FF63770E.gif";

    private final String welcomeChannelId;
    private final String welcomeImageUrl;

    public WelcomeListener(String welcomeChannelId, String welcomeImageUrl) {
        this.welcomeChannelId = welcomeChannelId;
        this.welcomeImageUrl = welcomeImageUrl;
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

        // GIF : URL configurée ou GIF par défaut
        String imageUrl = (welcomeImageUrl != null && !welcomeImageUrl.isBlank())
                ? welcomeImageUrl
                : DEFAULT_WELCOME_GIF_URL;
        embed.setImage(imageUrl);

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
