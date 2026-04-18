package fr.sakura.bot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;

public class AvatarCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(AvatarCommand.class);

    @Override
    public String getName() {
        return "avatar";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche l'avatar d'un membre en taille réelle")
                .addOptions(new OptionData(OptionType.USER, "membre", "Le membre dont afficher l'avatar", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        logger.debug("Execution /avatar par userId={}", event.getUser().getId());
        Member target = event.getOption("membre") != null
                ? event.getOption("membre").getAsMember()
                : event.getMember();

        if (target == null) {
            logger.warn("/avatar cible introuvable userId={}", event.getUser().getId());
            event.reply("❌ Utilisateur introuvable.").setEphemeral(true).queue();
            return;
        }

        String avatarUrl = target.getUser().getEffectiveAvatarUrl() + "?size=1024";

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("\uD83D\uDDBC️ Avatar de " + target.getUser().getName());
        embed.setColor(new Color(255, 183, 197));
        embed.setImage(avatarUrl);
        embed.setFooter("Clique sur l'image pour la voir en taille originale");

        event.replyEmbeds(embed.build()).queue();
        logger.info("/avatar envoye cibleId={} demandeurId={}", target.getId(), event.getUser().getId());
    }
}
