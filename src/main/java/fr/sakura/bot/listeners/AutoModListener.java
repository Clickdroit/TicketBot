package fr.sakura.bot.listeners;

import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.utils.ModerationLogger;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class AutoModListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AutoModListener.class);

    private final ModerationLogger moderationLogger;
    private final SettingsManager settingsManager;

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern GIF_PATTERN = Pattern.compile(".*(tenor\\.com|giphy\\.com|\\.gif)(\\?.*)?$", Pattern.CASE_INSENSITIVE);

    private final Map<String, Deque<Long>> spamWindows = new ConcurrentHashMap<>();
    private final Map<String, StrikeState> strikeStates = new ConcurrentHashMap<>();
    private final Map<String, Long> lastNoticeByRuleMember = new ConcurrentHashMap<>();
    private final List<Rule> rules;

    private static final class StrikeState {
        private int count;
        private long lastUpdateMs;
    }

    private record RuleResult(boolean triggered, String ruleKey, String logReason, String logDetails, String strikeReason, String publicNotice) {
        static RuleResult none() {
            return new RuleResult(false, null, null, null, null, null);
        }
    }

    @FunctionalInterface
    private interface Rule {
        RuleResult evaluate(MessageReceivedEvent event, Member member, String guildId, String memberKey, String content);
    }

    /** Entries older than this are purged from memory. */
    private static final long STALE_ENTRY_MS = 60 * 60 * 1000L; // 1 hour

    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "automod-cleanup");
        t.setDaemon(true);
        return t;
    });

    public AutoModListener(ModerationLogger moderationLogger, SettingsManager settingsManager) {
        this.moderationLogger = moderationLogger;
        this.settingsManager = settingsManager;
        this.rules = buildRulePipeline();

        // Purge stale entries every 30 minutes to prevent memory leaks
        cleanupExecutor.scheduleAtFixedRate(this::purgeStaleEntries, 30, 30, TimeUnit.MINUTES);
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.getMember() == null) {
            return;
        }

        Member member = event.getMember();
        String guildId = event.getGuild().getId();
        String content = event.getMessage().getContentRaw();
        String memberKey = guildId + ":" + member.getId();

        if (member.hasPermission(Permission.ADMINISTRATOR)
                || member.hasPermission(Permission.MANAGE_SERVER)
                || member.hasPermission(Permission.MESSAGE_MANAGE)) {
            return;
        }

        for (Rule rule : rules) {
            RuleResult result = rule.evaluate(event, member, guildId, memberKey, content);
            if (!result.triggered()) {
                continue;
            }

            event.getMessage().delete().queue(
                    success -> {
                        moderationLogger.logInGuild(
                                event.getGuild(),
                                "WARN",
                                null,
                                member,
                                result.logReason(),
                                result.logDetails() + "\nRegle: " + result.ruleKey()
                        );

                        sendNoticeWithCooldown(event, member, result.ruleKey(), result.publicNotice());
                        applyStrikeAndEscalate(event, member, memberKey, result.strikeReason(), result.ruleKey());
                    },
                    error -> logger.warn("AutoMod: suppression impossible rule={} msgId={} userId={}", result.ruleKey(), event.getMessageId(), member.getId(), error)
            );
            return;
        }
    }

    private List<Rule> buildRulePipeline() {
        List<Rule> pipeline = new ArrayList<>();
        pipeline.add(this::evaluateLinkRule);
        pipeline.add(this::evaluateSpamRule);
        return pipeline;
    }

    private RuleResult evaluateLinkRule(MessageReceivedEvent event, Member member, String guildId, String memberKey, String content) {
        if (!settingsManager.isAntiLinkEnabled(guildId)) {
            return RuleResult.none();
        }

        if (!URL_PATTERN.matcher(content).find()) {
            return RuleResult.none();
        }

        boolean isGif = GIF_PATTERN.matcher(content).find();
        boolean gifAllowed = settingsManager.isGifLinksAllowed(guildId);
        if (gifAllowed && isGif) {
            return RuleResult.none();
        }

        return new RuleResult(
                true,
                "anti_link",
                "Lien non autorisé supprimé",
                "Contenu bloqué: " + truncate(content, 300),
                "Lien interdit",
                member.getAsMention() + " ❌ Les liens ne sont pas autorisés ici (GIF autorisés selon config)."
        );
    }

    private RuleResult evaluateSpamRule(MessageReceivedEvent event, Member member, String guildId, String memberKey, String content) {
        if (!settingsManager.isAntiSpamEnabled(guildId)) {
            return RuleResult.none();
        }

        int spamLimit = settingsManager.getSpamLimit(guildId);
        long spamWindowMs = settingsManager.getSpamWindowMs(guildId);
        if (!isSpamming(memberKey, spamLimit, spamWindowMs)) {
            return RuleResult.none();
        }

        return new RuleResult(
                true,
                "anti_spam",
                "Spam détecté",
                "Limite: " + spamLimit + " msg / " + (spamWindowMs / 1000) + "s",
                "Spam détecté",
                member.getAsMention() + " ⚠️ Ralentis le rythme des messages, s'il te plaît."
        );
    }

    private boolean isSpamming(String memberKey, int spamLimit, long spamWindowMs) {
        long now = Instant.now().toEpochMilli();
        Deque<Long> queue = spamWindows.computeIfAbsent(memberKey, k -> new ArrayDeque<>());
        queue.addLast(now);

        while (!queue.isEmpty() && now - queue.peekFirst() > spamWindowMs) {
            queue.pollFirst();
        }

        return queue.size() >= spamLimit;
    }

    private void sendNoticeWithCooldown(MessageReceivedEvent event, Member member, String ruleKey, String notice) {
        if (notice == null || notice.isBlank()) {
            return;
        }

        int cooldownSeconds = settingsManager.getAutomodNoticeCooldownSeconds(event.getGuild().getId());
        long cooldownMs = cooldownSeconds * 1000L;
        long now = Instant.now().toEpochMilli();

        String key = event.getGuild().getId() + ":" + member.getId() + ":" + ruleKey;
        long last = lastNoticeByRuleMember.getOrDefault(key, 0L);
        if (now - last < cooldownMs) {
            return;
        }

        lastNoticeByRuleMember.put(key, now);
        event.getChannel().sendMessage(notice).queue();
    }

    private void applyStrikeAndEscalate(MessageReceivedEvent event, Member member, String memberKey, String reason, String ruleKey) {
        long now = Instant.now().toEpochMilli();
        String guildId = event.getGuild().getId();
        StrikeState state = strikeStates.computeIfAbsent(memberKey, k -> new StrikeState());

        int strikeResetMinutes = settingsManager.getAutomodStrikeResetMinutes(guildId);
        long strikeResetMs = strikeResetMinutes * 60_000L;

        if (now - state.lastUpdateMs > strikeResetMs) {
            state.count = 0;
        }

        state.count++;
        state.lastUpdateMs = now;

        int threshold = settingsManager.getAutomodStrikesToTimeout(guildId);
        int timeoutMinutes = settingsManager.getAutomodTimeoutMinutes(guildId);

        if (state.count < threshold) {
            logger.info("AutoMod strike guildId={}, userId={}, rule={}, count={}/{}", guildId, member.getId(), ruleKey, state.count, threshold);
            return;
        }

        if (timeoutMinutes <= 0 || member.isTimedOut()) {
            return;
        }

        Member selfMember = event.getGuild().getSelfMember();
        if (!selfMember.hasPermission(Permission.MODERATE_MEMBERS) || !selfMember.canInteract(member)) {
            logger.warn("AutoMod: timeout impossible (permissions/hierarchie) targetId={}", member.getId());
            return;
        }

        member.timeoutFor(Duration.ofMinutes(timeoutMinutes)).reason("AutoMod: " + reason + " (" + ruleKey + ")").queue(
                success -> {
                    moderationLogger.logInGuild(
                            event.getGuild(),
                            "TIMEOUT",
                            null,
                            member,
                            "Seuil AutoMod atteint",
                            "Regle: " + ruleKey + " • Strikes=" + state.count + " • Duree=" + timeoutMinutes + " min"
                    );
                    event.getChannel().sendMessage(member.getAsMention() + " ⏳ Timeout automatique appliqué (" + timeoutMinutes + " min).")
                            .queue();
                    logger.info("AutoMod: timeout applique targetId={} strikes={} rule={}", member.getId(), state.count, ruleKey);
                    state.count = 0;
                },
                error -> logger.warn("AutoMod: echec timeout targetId={} rule={}", member.getId(), ruleKey, error)
        );
    }

    private String truncate(String value, int max) {
        if (value.length() <= max) return value;
        return value.substring(0, max) + "...";
    }

    private void purgeStaleEntries() {
        long now = Instant.now().toEpochMilli();
        int removedSpam = 0, removedStrikes = 0, removedNotices = 0;

        var spamIt = spamWindows.entrySet().iterator();
        while (spamIt.hasNext()) {
            var entry = spamIt.next();
            Deque<Long> queue = entry.getValue();
            while (!queue.isEmpty() && now - queue.peekFirst() > STALE_ENTRY_MS) {
                queue.pollFirst();
            }
            if (queue.isEmpty()) {
                spamIt.remove();
                removedSpam++;
            }
        }

        var strikeIt = strikeStates.entrySet().iterator();
        while (strikeIt.hasNext()) {
            var entry = strikeIt.next();
            if (now - entry.getValue().lastUpdateMs > STALE_ENTRY_MS) {
                strikeIt.remove();
                removedStrikes++;
            }
        }

        var noticeIt = lastNoticeByRuleMember.entrySet().iterator();
        while (noticeIt.hasNext()) {
            var entry = noticeIt.next();
            if (now - entry.getValue() > STALE_ENTRY_MS) {
                noticeIt.remove();
                removedNotices++;
            }
        }

        if (removedSpam + removedStrikes + removedNotices > 0) {
            logger.debug("AutoMod cleanup: spam={}, strikes={}, notices={}", removedSpam, removedStrikes, removedNotices);
        }
    }
}
