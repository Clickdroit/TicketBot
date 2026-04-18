package fr.sakura.bot.listeners;

import fr.sakura.bot.commands.CommandManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        logger.info("Bot connecte en tant que {}", event.getJDA().getSelfUser().getName());
        logger.info("Verification mono-serveur de {} guild(s)", event.getJDA().getGuilds().size());

        // On attend la confirmation de la purge globale avant d'enregistrer
        // les commandes guild, pour éviter tout doublon temporaire côté Discord
        event.getJDA().updateCommands().queue(
                success -> {
                    logger.info("Commandes globales purgees");
                    for (Guild guild : event.getJDA().getGuilds()) {
                        checkAndLeaveUnauthorizedGuilds(guild);
                    }
                },
                error -> logger.error("Echec de purge des commandes globales", error)
        );
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        logger.warn("Invitation detectee sur la guilde {} ({})", event.getGuild().getName(), event.getGuild().getId());
        checkAndLeaveUnauthorizedGuilds(event.getGuild());
    }

    private void checkAndLeaveUnauthorizedGuilds(Guild guild) {
        if (!guild.getId().equals(guildId)) {
            logger.warn("Serveur non autorise detecte: {} ({}) - depart automatique", guild.getName(), guild.getId());
            guild.leave().queue(
                    success -> logger.info("Depart reussi du serveur non autorise {} ({})", guild.getName(), guild.getId()),
                    error   -> logger.error("Echec du depart du serveur non autorise {} ({})", guild.getName(), guild.getId(), error)
            );
        } else {
            logger.info("Serveur autorise confirme: {} ({})", guild.getName(), guild.getId());
            commandManager.registerCommands(guild);
        }
    }
}