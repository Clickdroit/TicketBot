package fr.sakura.bot.commands;

import fr.sakura.bot.utils.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelpCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(HelpCommand.class);

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche la liste des commandes disponibles");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        logger.debug("Execution /help par userId={}", event.getUser().getId());

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("🌸", "Guide des commandes Sakura");
        embed.setDescription("Voici les commandes principales disponibles sur ce serveur.");
        embed.addField("Informations", "`/ping`, `/help`, `/avatar`, `/userinfo`, `/serverinfo`", false);
        embed.addField("Modération", "`/clear`, `/kick`, `/ban`, `/timeout`, `/unban`, `/warn`, `/warnings`, `/clearwarnings`", false);
        embed.addField("Auto-mod & config", "`/config antispam|antilink|giflinks|spamlimit|spamwindow|strikes|timeout|strikereset|noticecooldown|xpcooldown|xpminlen|xpminalnum|xpgain`", false);
        embed.addField("XP & niveaux", "`/rank`, `/leaderboard`, `/xpadmin set|add|reset|roleset|roleremove|rolelist`", false);
        embed.addField("Tickets & staff", "`/ticketpanel`, `/lock`, `/unlock`, `/slowmode`, `/say`", false);
        if (event.getJDA().getSelfUser().getEffectiveAvatarUrl() != null) {
            embed.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl());
        }
        EmbedStyle.setFooter(embed, "Demandé par " + event.getUser().getName());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        logger.info("/help envoye userId={}", event.getUser().getId());
    }
}
