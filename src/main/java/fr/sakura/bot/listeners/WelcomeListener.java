package fr.sakura.bot.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.format.DateTimeFormatter;

public class WelcomeListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(WelcomeListener.class);

    private final String welcomeChannelId;

    public WelcomeListener(String welcomeChannelId) {
        this.welcomeChannelId = welcomeChannelId;
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

        // URL de l'image centrale (à remplacer par celle que tu souhaites, ex: Giyu Tomioka)
        String imageUrl = "https://media.giphy.com/media/fTN0rPZuY9tT40Xn1c/giphy.gif"; 

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Bienvenue !");
        embed.setDescription("Bienvenue " + event.getMember().getAsMention() + " sur **" + event.getGuild().getName() + "** !");
        embed.setColor(new Color(43, 45, 49)); // Couleur de fond sombre Discord par défaut ou personnalisée
        
        if (event.getMember().getUser().getAvatarUrl() != null) {
            embed.setThumbnail(event.getMember().getUser().getAvatarUrl());
        }

        embed.setImage(imageUrl);

        // Formatage de l'heure d'arrivée
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String joinTime = event.getMember().getTimeJoined().format(formatter);
        int memberCount = event.getGuild().getMemberCount();

        embed.setFooter("Arrivée à " + event.getMember().getTimeJoined().format(DateTimeFormatter.ofPattern("HH:mm")) 
                + " • Membre n°" + memberCount 
                + " • " + joinTime, 
                event.getMember().getUser().getEffectiveAvatarUrl());

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> logger.info("Message de bienvenue envoye pour {} dans #{}",
                        event.getUser().getId(), channel.getName()),
                error -> logger.error("Echec envoi message de bienvenue pour {}", event.getUser().getId(), error)
        );
    }
}
