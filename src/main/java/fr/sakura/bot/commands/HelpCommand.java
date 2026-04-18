package fr.sakura.bot.commands;

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
        String helpMessage = "\uD83C\uDF38 **Liste des commandes de Sakura Bot :**\n"
                + "`/ping` - Vérifie si le bot répond et affiche sa latence.\n"
                + "`/help` - Affiche ce message d'aide.\n"
                + "`/avatar [membre]` - Affiche l'avatar d'un membre.\n"
                + "`/userinfo [membre]` - Affiche les infos d'un membre.\n"
                + "`/serverinfo` - Affiche les infos du serveur.\n"
                + "`/clear <montant>` - [MOD] Supprime les messages récents.\n"
                + "`/kick <membre>` - [MOD] Expulse un membre.\n"
                + "`/ban <membre>` - [MOD] Bannit un membre.\n"
                + "`/timeout <membre> <minutes>` - [MOD] Mute temporairement un membre.\n"
                + "`/unban <user_id>` - [MOD] Retire un bannissement.\n"
                + "`/warn <membre> <raison>` - [MOD] Ajoute un avertissement (JSON).\n"
                + "`/warnings <membre>` - [MOD] Liste les avertissements d'un membre.\n"
                + "`/clearwarnings <membre>` - [MOD] Supprime les avertissements d'un membre.";
        event.reply(helpMessage).setEphemeral(true).queue();
        logger.info("/help envoye userId={}", event.getUser().getId());
    }
}
