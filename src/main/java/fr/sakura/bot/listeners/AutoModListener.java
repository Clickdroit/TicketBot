package fr.sakura.bot.listeners;

import fr.sakura.bot.core.service.SpamDetector;
import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import fr.sakura.bot.core.util.MdcContext;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Listener pour la modération automatique (anti-spam, anti-liens).
 */
public class AutoModListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AutoModListener.class);

    private final ModerationLogListener moderationLogListener;
    private final SettingsManager settingsManager;
    private final SpamDetector spamDetector;
    private final fr.sakura.bot.core.service.TempBanService tempBanService;

    private final Map<String, Long> lastWarnTime = new ConcurrentHashMap<>();
    
    private static final Pattern LINK_PATTERN = Pattern.compile("https?://\\S+|discord\\.gg/\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GIF_PATTERN = Pattern.compile("tenor\\.com|giphy\\.com", Pattern.CASE_INSENSITIVE);

    public AutoModListener(ModerationLogListener moderationLogListener, SettingsManager settingsManager, SpamDetector spamDetector, fr.sakura.bot.core.service.TempBanService tempBanService) {
        this.moderationLogListener = moderationLogListener;
        this.settingsManager = settingsManager;
        this.spamDetector = spamDetector;
        this.tempBanService = tempBanService;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;

        Member member = event.getMember();
        if (member == null || member.hasPermission(net.dv8tion.jda.api.Permission.MESSAGE_MANAGE)) return;

        try (var ignored = MdcContext.of("guildId", event.getGuild().getId(), "userId", event.getAuthor().getId())) {
            String content = event.getMessage().getContentRaw();
            String guildId = event.getGuild().getId();
            String memberKey = guildId + ":" + member.getId();

            // ── Anti-Spam ─────────────────────────────────────────────────────────────
            if (settingsManager.isAntiSpamEnabled(guildId)) {
                if (spamDetector.check(event, settingsManager)) {
                    handleSpamReaction(event, member, guildId);
                }
            }

            // ── Anti-Liens ─────────────────────────────────────────────────────────────
            if (settingsManager.isAntiLinkEnabled(guildId)) {
                if (LINK_PATTERN.matcher(content).find()) {
                    boolean isGif = GIF_PATTERN.matcher(content).find();
                    if (isGif && settingsManager.isGifLinksAllowed(guildId)) {
                        return;
                    }

                    event.getMessage().delete().queue();
                    
                    long now = System.currentTimeMillis();
                    long last = lastWarnTime.getOrDefault(memberKey, 0L);
                    long noticeCooldown = (long) settingsManager.getAutomodNoticeCooldownSeconds(guildId) * 1000L;
                    
                    if (now - last > noticeCooldown) {
                        event.getChannel().sendMessage("⚠️ " + member.getAsMention() + ", les liens ne sont pas autorisés ici.").queue();
                        lastWarnTime.put(memberKey, now);
                        
                        moderationLogListener.logAction(event.getGuild(), "AUTOMOD_WARN", null, member, "Envoi de lien non autorisé", "> **Contenu :** " + content);
                    }
                    
                    logger.info("AutoMod: Lien supprimé");
                }
            }
        }
    }

    private void handleSpamReaction(MessageReceivedEvent event, Member member, String guildId) {
        event.getMessage().delete().queue();
        
        long now = System.currentTimeMillis();
        String userId = member.getId();
        String memberKey = guildId + ":" + userId;
        long lastNotice = lastWarnTime.getOrDefault(memberKey, 0L);
        long noticeCooldown = (long) settingsManager.getAutomodNoticeCooldownSeconds(guildId) * 1000L;

        int currentStrikes = spamDetector.getStrikes(guildId, userId);
        
        // Escalade progressive :
        // Strike 1-2 : Warn
        // Strike 3 : Timeout 10 min
        // Strike 4 : Timeout 1h
        // Strike 5+ : TempBan 24h
        
        if (currentStrikes >= 5) {
            tempBanService.addTempBan(event.getGuild(), member.getUser(), TimeUnit.DAYS.toMillis(1), "Spam intensif (AutoMod Escalation)");
            event.getChannel().sendMessage("🚫 " + member.getAsMention() + " a été banni temporairement (24h) pour spam intensif.").queue();
            spamDetector.resetStrikes(guildId, userId);
        } else if (currentStrikes == 4) {
            member.timeoutFor(1, TimeUnit.HOURS).reason("Spam répété (AutoMod Escalation)").queue(
                    success -> event.getChannel().sendMessage("⏳ " + member.getAsMention() + " a été mis en sourdine pendant 1 heure.").queue(),
                    error -> logger.error("Echec timeout (1h) AutoMod", error)
            );
            moderationLogListener.logAction(event.getGuild(), "TIMEOUT", null, member, "Spam répété (AutoMod)", "> **Action :** Timeout 1h");
        } else if (currentStrikes == 3) {
            member.timeoutFor(10, TimeUnit.MINUTES).reason("Spam détecté (AutoMod Escalation)").queue(
                    success -> event.getChannel().sendMessage("⏳ " + member.getAsMention() + " a été mis en sourdine pendant 10 minutes.").queue(),
                    error -> logger.error("Echec timeout (10m) AutoMod", error)
            );
            moderationLogListener.logAction(event.getGuild(), "TIMEOUT", null, member, "Spam détecté (AutoMod)", "> **Action :** Timeout 10m");
        } else if (now - lastNotice > noticeCooldown) {
            event.getChannel().sendMessage("⚠️ " + member.getAsMention() + ", merci de ralentir l'envoi de messages.").queue();
            lastWarnTime.put(memberKey, now);
            moderationLogListener.logAction(event.getGuild(), "AUTOMOD_WARN", null, member, "Spam détecté", "> **Strike :** " + currentStrikes);
        }
    }
}
