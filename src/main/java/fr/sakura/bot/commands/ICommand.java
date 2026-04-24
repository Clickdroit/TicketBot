package fr.sakura.bot.commands;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

public interface ICommand {
    /**
     * @return Le nom de la commande (ex: "ping")
     */
    String getName();

    /**
     * @return La structure de la commande pour l'enregistrer sur Discord
     */
    CommandData getCommandData();

    /**
     * @return La catégorie de la commande
     */
    default String getCategory() {
        return "Général";
    }

    /**
     * Code exécuté lorsque la commande Slash est appelée
     * @param event L'événement d'interaction
     */
    default void execute(SlashCommandInteractionEvent event) {}

    /**
     * Gère l'auto-complétion pour cette commande
     * @param event L'événement d'auto-complétion
     */
    default void onAutoComplete(CommandAutoCompleteInteractionEvent event) {}

    /**
     * Gère l'interaction via menu contextuel utilisateur
     * @param event L'événement de contexte utilisateur
     */
    default void onUserContext(UserContextInteractionEvent event) {}

    /**
     * Gère l'interaction via menu contextuel message
     * @param event L'événement de contexte message
     */
    default void onMessageContext(MessageContextInteractionEvent event) {}
}
