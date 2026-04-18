package fr.sakura.bot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.format.DateTimeFormatter;

public class ServerInfoCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(ServerInfoCommand.class);

    @Override
    public String getName() {
        return "serverinfo";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche les informations du serveur");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            logger.warn("/serverinfo appelee hors serveur par userId={}", event.getUser().getId());
            return;
        }

        logger.debug("Execution /serverinfo par userId={} sur guildId={}", event.getUser().getId(), guild.getId());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("\uD83C\uDF38 " + guild.getName());
        embed.setColor(new Color(255, 183, 197)); // Rose sakura
        embed.setThumbnail(guild.getIconUrl());

        embed.addField("\uD83D\uDC51 Propriétaire", guild.getOwner() != null ? guild.getOwner().getAsMention() : "Inconnu", true);
        embed.addField("\uD83D\uDC65 Membres", String.valueOf(guild.getMemberCount()), true);
        embed.addField("\uD83D\uDCAC Salons", String.valueOf(guild.getChannels().size()), true);
        embed.addField("\uD83C\uDFAD Rôles", String.valueOf(guild.getRoles().size()), true);
        embed.addField("\uD83D\uDE00 Emojis", String.valueOf(guild.getEmojis().size()), true);
        embed.addField("\uD83D\uDD12 Niveau de vérification", guild.getVerificationLevel().name(), true);
        embed.addField("\uD83D\uDCC5 Créé le", guild.getTimeCreated().format(formatter), false);

        if (guild.getBannerUrl() != null) {
            embed.setImage(guild.getBannerUrl() + "?size=1024");
        }

        embed.setFooter("ID : " + guild.getId());

        event.replyEmbeds(embed.build()).queue();
        logger.info("/serverinfo envoye pour guildId={} demandeurId={}", guild.getId(), event.getUser().getId());
    }
}
