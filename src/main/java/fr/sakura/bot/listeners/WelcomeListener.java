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

        String joinTime = EmbedStyle.formatInfoDate(event.getMember().getTimeJoined());
        int memberCount = event.getGuild().getMemberCount();

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("🌸", "Bienvenue !");
        embed.setDescription("Bienvenue " + event.getMember().getAsMention() + " sur **" + event.getGuild().getName() + "** !");

        if (event.getMember().getUser().getAvatarUrl() != null) {
            embed.setThumbnail(event.getMember().getUser().getAvatarUrl());
        }

        // Image optionnelle : ignorée si non configurée dans le .env
        if (welcomeImageUrl != null && !welcomeImageUrl.isBlank()) {
            embed.setImage(welcomeImageUrl);
        } else {
            logger.debug("Aucune image de bienvenue configuree (WELCOME_IMAGE_URL absent)");
        }

        String arrivalTime = event.getMember().getTimeJoined().toLocalTime().format(HOUR_MINUTE_FORMATTER);
        EmbedStyle.setFooter(
                embed,
                "Arrivée à " + arrivalTime + " • Membre n°" + memberCount + " • " + joinTime
        );

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> logger.info("Message de bienvenue envoye pour {} dans #{}",
                        event.getUser().getId(), channel.getName()),
                error -> logger.error("Echec envoi message de bienvenue pour {}", event.getUser().getId(), error)
        );
    }
}
