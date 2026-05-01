package fr.sakura.bot.listeners;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fr.sakura.bot.core.model.AutoModRuleEntry;
import fr.sakura.bot.core.service.SpamDetector;
import fr.sakura.bot.core.service.TempBanService;
import fr.sakura.bot.core.store.AutoModRuleStore;
import fr.sakura.bot.core.util.MdcContext;
import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Listener pour la modération automatique (anti-spam, anti-liens, règles personnalisées).
 */
public class AutoModListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AutoModListener.class);

    private final ModerationLogListener moderationLogListener;
    private final SettingsManager settingsManager;
    private final SpamDetector spamDetector;
    private final TempBanService tempBanService;
    private final AutoModRuleStore ruleStore;

    private final Map<String, Long> lastWarnTime = new ConcurrentHashMap<>();
    private final Cache<String, List<AutoModRuleEntry>> rulesCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();
    
    private static final Pattern LINK_PATTERN = Pattern.compile("https?://\\S+|discord\\.gg/\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GIF_PATTERN = Pattern.compile("tenor\\.com|giphy\\.com", Pattern.CASE_INSENSITIVE);

    public AutoModListener(ModerationLogListener moderationLogListener, SettingsManager settingsManager, SpamDetector spamDetector, TempBanService tempBanService, AutoModRuleStore ruleStore) {
        this.moderationLogListener = moderationLogListener;
        this.settingsManager = settingsManager;
        this.spamDetector = spamDetector;
        this.tempBanService = tempBanService;
        this.ruleStore = ruleStore;
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

            // ── Règles Personnalisées ──────────────────────────────────────────────────
            if (checkCustomRules(event, member, content, guildId)) {
                return; // Si une règle a matché et supprimé le message, on s'arrête là
            }

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

                    event.getMessage().delete().queue(null, err -> {});
                    
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

    private boolean checkCustomRules(MessageReceivedEvent event, Member member, String content, String guildId) {
        List<AutoModRuleEntry> rules = rulesCache.get(guildId, ruleStore::getRules);
        if (rules == null || rules.isEmpty()) return false;

        String lowerContent = content.toLowerCase();

        for (AutoModRuleEntry rule : rules) {
            boolean match = false;
            if ("WORD".equals(rule.type())) {
                match = lowerContent.contains(rule.pattern().toLowerCase());
            } else if ("REGEX".equals(rule.type())) {
                try {
                    match = Pattern.compile(rule.pattern(), Pattern.CASE_INSENSITIVE).matcher(content).find();
                } catch (Exception e) {
                    logger.error("Regex invalide dans AutoMod ruleId={}", rule.id());
                }
            }

            if (match) {
                applyRuleAction(event, member, rule, content);
                return true;
            }
        }
        return false;
    }

    private void applyRuleAction(MessageReceivedEvent event, Member member, AutoModRuleEntry rule, String content) {
        event.getMessage().delete().queue(null, err -> {});

        String action = rule.action();
        String reason = "Règle AutoMod #" + rule.id() + " (" + rule.type() + ")";

        if ("WARN".equals(action)) {
            event.getChannel().sendMessage("⚠️ " + member.getAsMention() + ", votre message a été supprimé car il enfreint une règle du serveur.").queue();
            moderationLogListener.logAction(event.getGuild(), "AUTOMOD_DELETE", null, member, reason, "> **Contenu :** " + content);
        } else if ("TIMEOUT".equals(action)) {
            member.timeoutFor(10, TimeUnit.MINUTES).reason(reason).queue(
                    success -> event.getChannel().sendMessage("⏳ " + member.getAsMention() + " a été mis en sourdine pendant 10 minutes (Règle AutoMod).").queue(),
                    error -> logger.error("Echec timeout AutoMod ruleId={}", rule.id(), error)
            );
            moderationLogListener.logAction(event.getGuild(), "TIMEOUT", null, member, reason, "> **Action :** Timeout 10m\n> **Contenu :** " + content);
        } else {
            // DEFAULT: DELETE
            moderationLogListener.logAction(event.getGuild(), "AUTOMOD_DELETE", null, member, reason, "> **Contenu :** " + content);
        }
    }

    private void handleSpamReaction(MessageReceivedEvent event, Member member, String guildId) {
        event.getMessage().delete().queue(null, err -> {});
        
        long now = System.currentTimeMillis();
        String userId = member.getId();
        String memberKey = guildId + ":" + userId;
        long lastNotice = lastWarnTime.getOrDefault(memberKey, 0L);
        long noticeCooldown = (long) settingsManager.getAutomodNoticeCooldownSeconds(guildId) * 1000L;

        int currentStrikes = spamDetector.getStrikes(guildId, userId);
        
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
