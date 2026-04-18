package fr.sakura.bot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public interface ICommand {
    /**
     * @return Le nom de la commande (ex: "ping")
     */
    String getName();

    /**
     * @return La structure de la commande pour l'enregistrer sur Discord
     */
    SlashCommandData getCommandData();

    /**
     * Code exécuté lorsque la commande est appelée
     * @param event L'événement d'interaction
     */
    void execute(SlashCommandInteractionEvent event);
}
