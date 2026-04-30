package fr.sakura.bot.core.service;

import fr.sakura.bot.core.model.TempBanEntry;
import fr.sakura.bot.core.store.TempBanStore;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service gérant les bannissements temporaires et leur expiration.
 */
public class TempBanService {

    private static final Logger logger = LoggerFactory.getLogger(TempBanService.class);

    private final TempBanStore store;
    private final ScheduledExecutorService scheduler;
    private JDA jda;

    public TempBanService(TempBanStore store) {
        this.store = store;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "temp-ban-checker");
            t.setDaemon(true);
            return t;
        });
    }

    public void start(JDA jda) {
        this.jda = jda;
        this.scheduler.scheduleAtFixedRate(this::checkExpiredBans, 1, 1, TimeUnit.MINUTES);
        logger.info("TempBanService démarré (vérification toutes les minutes)");
    }

    public void addTempBan(Guild guild, User user, long durationMs, String reason) {
        long unbanTime = System.currentTimeMillis() + durationMs;
        TempBanEntry entry = new TempBanEntry(guild.getId(), user.getId(), unbanTime, reason);
        store.addTempBan(entry);
        
        guild.ban(user, 0, TimeUnit.DAYS).reason("[TempBan] " + reason).queue(
            success -> logger.info("Membre {} banni temporairement de {} (Expire à {})", user.getId(), guild.getId(), unbanTime),
            error -> {
                logger.error("Échec du ban pour {} sur {}", user.getId(), guild.getId(), error);
                store.removeTempBan(guild.getId(), user.getId());
            }
        );
    }

    private void checkExpiredBans() {
        if (jda == null) return;

        List<TempBanEntry> expired = store.getExpiredBans(System.currentTimeMillis());
        if (expired.isEmpty()) return;

        logger.info("Traitement de {} bannissements temporaires expirés", expired.size());

        for (TempBanEntry entry : expired) {
            Guild guild = jda.getGuildById(entry.guildId());
            if (guild == null) {
                store.removeTempBan(entry.guildId(), entry.userId());
                continue;
            }

            guild.unban(User.fromId(entry.userId())).reason("Bannissement temporaire expiré").queue(
                success -> {
                    logger.info("Utilisateur {} débanni de {} (expiration)", entry.userId(), entry.guildId());
                    store.removeTempBan(entry.guildId(), entry.userId());
                },
                error -> {
                    logger.warn("Échec du débannissement de {} sur {} (peut-être déjà débanni)", entry.userId(), entry.guildId());
                    store.removeTempBan(entry.guildId(), entry.userId());
                }
            );
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
