package fr.sakura.bot.commands.info;

import fr.sakura.bot.commands.ICommand;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * Commande simple pour obtenir l'ID d'une entité Discord.
 */
public class IdCommand implements ICommand {

    @Override
    public String getName() {
        return "id";
    }

    @Override
    public String getCategory() {
        return "Informations";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Obtient l'ID d'un utilisateur, salon, rôle ou emoji")
                .addOption(OptionType.MENTIONABLE, "cible", "L'entité dont vous voulez l'ID", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping targetOption = event.getOption("cible");
        if (targetOption == null) return;

        IMentionable target = targetOption.getAsMentionable();
        event.reply("🆔 ID de " + target.getAsMention() + " : `" + target.getId() + "`").queue();
    }
}
