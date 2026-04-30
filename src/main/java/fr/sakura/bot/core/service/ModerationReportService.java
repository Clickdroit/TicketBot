package fr.sakura.bot.core.service;

import fr.sakura.bot.core.store.WarningStore;
import fr.sakura.bot.database.SettingsManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service gérant le rapport de modération hebdomadaire.
 */
public class ModerationReportService {

    private static final Logger logger = LoggerFactory.getLogger(ModerationReportService.class);

    private final WarningStore warningStore;
    private final SettingsManager settingsManager;
    private final ScheduledExecutorService scheduler;

    public ModerationReportService(WarningStore warningStore, SettingsManager settingsManager) {
        this.warningStore = warningStore;
        this.settingsManager = settingsManager;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mod-report-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    public void start(JDA jda) {
        // Planifier pour dimanche minuit (simplifié : toutes les semaines à partir de maintenant)
        scheduler.scheduleAtFixedRate(() -> sendWeeklyReport(jda), 1, 7, TimeUnit.DAYS);
        logger.info("ModerationReportService démarré");
    }

    private void sendWeeklyReport(JDA jda) {
        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        String sinceIso = since.toString();

        for (Guild guild : jda.getGuilds()) {
            settingsManager.getLogChannelId(guild.getId()).ifPresent(logChannelId -> {
                TextChannel channel = guild.getTextChannelById(logChannelId);
                if (channel == null) return;

                WarningStore.WeeklyStats stats = warningStore.getWeeklyStats(guild.getId(), sinceIso);
                if (stats.total() == 0) return;

                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("📊 Rapport de Modération Hebdomadaire")
                        .setDescription("Résumé des activités du " + sinceIso.substring(0, 10) + " au " + Instant.now().toString().substring(0, 10))
                        .setColor(Color.CYAN)
                        .setTimestamp(Instant.now());

                eb.addField("📈 Total des avertissements", String.valueOf(stats.total()), false);

                if (!stats.topUsers().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    stats.topUsers().forEach((userId, count) -> sb.append("- <@").append(userId).append("> : **").append(count).append("**\n"));
                    eb.addField("👤 Membres les plus sanctionnés", sb.toString(), true);
                }

                if (!stats.topReasons().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    stats.topReasons().forEach((reason, count) -> sb.append("- ").append(reason).append(" : **").append(count).append("**\n"));
                    eb.addField("📝 Top infractions", sb.toString(), true);
                }

                channel.sendMessageEmbeds(eb.build()).queue();
            });
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
