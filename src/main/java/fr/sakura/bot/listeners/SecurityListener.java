package fr.sakura.bot.listeners;

import fr.sakura.bot.commands.CommandManager;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Déclenche l'initialisation post-connexion du bot pour toutes les guildes (multi-serveur).
 */
public class SecurityListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SecurityListener.class);

    private final CommandManager commandManager;

    public SecurityListener(CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        logger.info("Bot connecté en tant que {}", event.getJDA().getSelfUser().getName());
        logger.info("Enregistrement des commandes globales (mode multi-serveur)...");

        // Enregistrement des commandes globales pour tous les serveurs
        commandManager.registerGlobalCommands(event.getJDA());
    }
}
