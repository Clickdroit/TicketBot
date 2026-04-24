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

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class AutoModListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AutoModListener.class);
    
    private final ModerationLogger moderationLogger;
    private final SettingsManager settingsManager;
    private final fr.sakura.bot.database.ProtectSettingsManager protectSettingsManager;
    private final fr.sakura.bot.protect.PhishingService phishingService;

    // Pattern URL simplifie
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    // Domaines de GIF autorises ou urls finissant par .gif
    private static final Pattern GIF_PATTERN = Pattern.compile(".*(tenor\\.com|giphy\\.com|\\.gif)(\\?.*)?$", Pattern.CASE_INSENSITIVE);

    private final Map<String, Deque<Long>> spamWindows = new ConcurrentHashMap<>();
    private final Map<String, StrikeState> strikeStates = new ConcurrentHashMap<>();
    private static final long STRIKE_RESET_MS = 10 * 60 * 1000L;

    private static final class StrikeState {
        private int count;
        private long lastUpdateMs;
    }

    public AutoModListener(ModerationLogger moderationLogger, SettingsManager settingsManager, fr.sakura.bot.database.ProtectSettingsManager protectSettingsManager, fr.sakura.bot.protect.PhishingService phishingService) {
        this.moderationLogger = moderationLogger;
        this.settingsManager = settingsManager;
        this.protectSettingsManager = protectSettingsManager;
        this.phishingService = phishingService;
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

        // Ignorer les admins pour l'Automod
        if (member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            return;
        }

        // Ignorer les salons spécifiques (jeux, etc.)
        if (settingsManager.getIgnoredChannels(guildId).contains(event.getChannel().getId())) {
            return;
        }

        // 0) Anti-Phishing (Sakura Protect)
        if (protectSettingsManager.isAntiPhishingEnabled(guildId) && phishingService.isPhishing(content)) {
            event.getMessage().delete().queue(
                    success -> {
                        moderationLogger.logInGuild(event.getGuild(), "PROTECT", null, member, "Sakura Protect: Phishing detecté", "Contenu bloqué: " + truncate(content, 300));
                        event.getChannel().sendMessage(member.getAsMention() + " ❌ Ce lien est considéré comme malveillant et a été supprimé.").queue();
                        applyStrikeAndEscalate(event, member, memberKey, "Phishing détecté");
                    },
                    error -> logger.warn("AutoMod: Impossible de supprimer message phishing", error)
            );
            return;
        }

        // 1) Anti-liens configurable
        if (settingsManager.isAntiLinkEnabled(guildId) && URL_PATTERN.matcher(content).find()) {
            boolean isGif = GIF_PATTERN.matcher(content).find();
            boolean gifAllowed = settingsManager.isGifLinksAllowed(guildId);

            if (!(gifAllowed && isGif)) {
                event.getMessage().delete().queue(
                        success -> {
                            moderationLogger.logInGuild(event.getGuild(), "WARN", null, member, "Systeme: Lien interdit supprime", "Contenu bloque: " + truncate(content, 300));
                            event.getChannel().sendMessage(member.getAsMention() + " ❌ Les liens ne sont pas autorisés ici (sauf les GIFs).").queue();
                            logger.info("AutoMod: Lien supprime msgId={} userId={}", event.getMessageId(), member.getId());
                            applyStrikeAndEscalate(event, member, memberKey, "Lien interdit");
                        },
                        error -> logger.warn("AutoMod: Impossible de supprimer message lien", error)
                );
                return; // Bloque l'execution du reste si le message est deja supprime
            }
        }

        // 2) Anti-spam configurable
        if (settingsManager.isAntiSpamEnabled(guildId)) {
            int spamLimit = settingsManager.getSpamLimit(guildId);
            long spamWindowMs = settingsManager.getSpamWindowMs(guildId);
            if (isSpamming(memberKey, spamLimit, spamWindowMs)) {
                event.getMessage().delete().queue(
                        success -> {
                            moderationLogger.logInGuild(event.getGuild(), "WARN", null, member, "Systeme: Spam detecte", "Limite: " + spamLimit + " msg / " + (spamWindowMs / 1000) + "s");
                            event.getChannel().sendMessage(member.getAsMention() + " ⚠️ Arrête de spammer !").queue();
                            applyStrikeAndEscalate(event, member, memberKey, "Spam detecte");
                        },
                        error -> {}
                );
            }
        }
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

    private void applyStrikeAndEscalate(MessageReceivedEvent event, Member member, String memberKey, String reason) {
        long now = Instant.now().toEpochMilli();
        StrikeState state = strikeStates.computeIfAbsent(memberKey, k -> new StrikeState());

        if (now - state.lastUpdateMs > STRIKE_RESET_MS) {
            state.count = 0;
        }

        state.count++;
        state.lastUpdateMs = now;

        String guildId = event.getGuild().getId();
        int threshold = settingsManager.getAutomodStrikesToTimeout(guildId);
        int timeoutMinutes = settingsManager.getAutomodTimeoutMinutes(guildId);

        if (state.count < threshold) {
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

        member.timeoutFor(Duration.ofMinutes(timeoutMinutes)).reason("AutoMod: " + reason).queue(
                success -> {
                    moderationLogger.logInGuild(
                            event.getGuild(),
                            "TIMEOUT",
                            null,
                            member,
                            "AutoMod: seuil d'infractions atteint",
                            "Strikes=" + state.count + ", duree=" + timeoutMinutes + " min"
                    );
                    event.getChannel().sendMessage(member.getAsMention() + " ⏳ Timeout automatique appliqué (" + timeoutMinutes + " min).")
                            .queue();
                    logger.info("AutoMod: timeout applique targetId={} strikes={}", member.getId(), state.count);
                    state.count = 0;
                },
                error -> logger.warn("AutoMod: echec timeout targetId={}", member.getId(), error)
        );
    }

    private String truncate(String value, int max) {
        if (value.length() <= max) return value;
        return value.substring(0, max) + "...";
    }
}
