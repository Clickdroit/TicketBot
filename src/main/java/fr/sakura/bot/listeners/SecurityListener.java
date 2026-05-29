package fr.sakura.bot.listeners;

import fr.sakura.bot.commands.CommandManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Assure la sécurité du bot (mono-serveur) et déclenche l'initialisation post-connexion.
 */
public class SecurityListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SecurityListener.class);

    private final String guildId;
    private final CommandManager commandManager;

    public SecurityListener(String guildId, CommandManager commandManager) {
        this.guildId = guildId;
        this.commandManager = commandManager;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        logger.info("Bot connecté en tant que {}", event.getJDA().getSelfUser().getName());
        logger.info("Vérification mono-serveur de {} guild(s)", event.getJDA().getGuilds().size());

        final boolean[] initialized = {false};
        
        // Timeout de sécurité (30s) si Discord est trop lent à répondre à updateCommands()
        event.getJDA().getGatewayPool().schedule(() -> {
            if (!initialized[0]) {
                logger.error("ALERTE : L'initialisation des commandes Discord n'a pas abouti après 30s. Vérifiez l'état de l'API Discord.");
            }
        }, 30, TimeUnit.SECONDS);

        // On attend la confirmation de la purge globale avant d'enregistrer
        // les commandes guild, pour éviter tout doublon temporaire côté Discord
        event.getJDA().updateCommands().queue(
                success -> {
                    initialized[0] = true;
                    logger.info("Commandes globales purgées avec succès");
                    for (Guild guild : event.getJDA().getGuilds()) {
                        if (checkAndLeaveUnauthorizedGuilds(guild)) {
                            commandManager.registerCommands(guild);
                        }
                    }
                },
                error -> {
                    initialized[0] = true;
                    logger.error("Échec critique lors de la purge des commandes globales", error);
                }
        );
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        logger.warn("Invitation détectée sur la guilde {} ({})", event.getGuild().getName(), event.getGuild().getId());
        checkAndLeaveUnauthorizedGuilds(event.getGuild());
    }

    private boolean checkAndLeaveUnauthorizedGuilds(Guild guild) {
        if (!guild.getId().equals(guildId)) {
            logger.warn("Serveur non autorisé détecté : {} ({}) - départ automatique", guild.getName(), guild.getId());
            guild.leave().queue(
                    success -> logger.info("Départ réussi du serveur non autorisé {} ({})", guild.getName(), guild.getId()),
                    error   -> logger.error("Échec du départ du serveur non autorisé {} ({})", guild.getName(), guild.getId(), error)
            );
            return false;
        }
        logger.info("Serveur autorisé confirmé : {} ({})", guild.getName(), guild.getId());
        return true;
    }
}
