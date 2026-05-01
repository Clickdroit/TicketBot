package fr.sakura.bot.core.service;

import fr.sakura.bot.core.model.TempRoleEntry;
import fr.sakura.bot.core.store.TempRoleStore;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service gérant l'attribution temporaire de rôles.
 */
public class TempRoleService {

    private static final Logger logger = LoggerFactory.getLogger(TempRoleService.class);

    private final TempRoleStore store;
    private final ScheduledExecutorService scheduler;
    private JDA jda;

    public TempRoleService(TempRoleStore store) {
        this.store = store;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "temp-role-checker");
            t.setDaemon(true);
            return t;
        });
    }

    public void start(JDA jda) {
        this.jda = jda;
        this.scheduler.scheduleAtFixedRate(this::checkExpiredRoles, 1, 1, TimeUnit.MINUTES);
        logger.info("TempRoleService démarré");
    }

    public void addTempRole(Guild guild, Member member, Role role, long durationMs) {
        long expiryTime = System.currentTimeMillis() + durationMs;
        TempRoleEntry entry = new TempRoleEntry(guild.getId(), member.getId(), role.getId(), expiryTime);
        
        // Supprimer d'éventuels doublons avant d'ajouter
        store.removeTempRole(guild.getId(), member.getId(), role.getId());
        store.addTempRole(entry);

        guild.addRoleToMember(member, role).reason("[TempRole] Attribué pour " + (durationMs / 60000) + " min").queue(
                success -> logger.info("Rôle {} ajouté temporairement à {} sur {}", role.getId(), member.getId(), guild.getId()),
                error -> {
                    logger.error("Échec ajout rôle temporaire {} à {}", role.getId(), member.getId(), error);
                    store.removeTempRole(guild.getId(), member.getId(), role.getId());
                }
        );
    }

    private void checkExpiredRoles() {
        if (jda == null) return;

        List<TempRoleEntry> expired = store.getExpiredRoles(System.currentTimeMillis());
        if (expired.isEmpty()) return;

        for (TempRoleEntry entry : expired) {
            Guild guild = jda.getGuildById(entry.guildId());
            if (guild == null) {
                store.removeTempRole(entry.id());
                continue;
            }

            Role role = guild.getRoleById(entry.roleId());
            if (role == null) {
                store.removeTempRole(entry.id());
                continue;
            }

            guild.retrieveMemberById(entry.userId()).queue(
                    member -> guild.removeRoleFromMember(member, role).reason("Rôle temporaire expiré").queue(
                            success -> {
                                logger.info("Rôle temporaire {} retiré de {} sur {}", entry.roleId(), entry.userId(), entry.guildId());
                                store.removeTempRole(entry.id());
                            },
                            error -> {
                                logger.warn("Échec retrait rôle temporaire {} de {}", entry.roleId(), entry.userId());
                                store.removeTempRole(entry.id());
                            }
                    ),
                    error -> {
                        logger.warn("Membre {} introuvable pour retrait rôle temporaire", entry.userId());
                        store.removeTempRole(entry.id());
                    }
            );
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
