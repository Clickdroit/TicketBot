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

    private final Map<String, Long> lastWarnTime = new ConcurrentHashMap<>();
    
    private static final Pattern LINK_PATTERN = Pattern.compile("https?://\\S+|discord\\.gg/\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GIF_PATTERN = Pattern.compile("tenor\\.com|giphy\\.com", Pattern.CASE_INSENSITIVE);

    public AutoModListener(ModerationLogListener moderationLogListener, SettingsManager settingsManager, SpamDetector spamDetector) {
        this.moderationLogListener = moderationLogListener;
        this.settingsManager = settingsManager;
        this.spamDetector = spamDetector;
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
                if (spamDetector.check(guildId, member.getId(), settingsManager)) {
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
        int strikesToTimeout = settingsManager.getAutomodStrikesToTimeout(guildId);
        
        if (strikesToTimeout > 0 && currentStrikes >= strikesToTimeout) {
            int timeoutMinutes = settingsManager.getAutomodTimeoutMinutes(guildId);
            member.timeoutFor(timeoutMinutes, TimeUnit.MINUTES).reason("Anti-spam (AutoMod)").queue(
                    success -> {
                        event.getChannel().sendMessage("⏳ " + member.getAsMention() + " a été mis en sourdine pendant " + timeoutMinutes + " minutes pour spam répétitif.").queue();
                        moderationLogListener.logAction(event.getGuild(), "TIMEOUT", null, member, "Spam répétitif (AutoMod)", "> **Strikes :** " + strikesToTimeout);
                    },
                    error -> logger.error("Echec timeout AutoMod", error)
            );
            spamDetector.resetStrikes(guildId, userId);
        } else if (now - lastNotice > noticeCooldown) {
            event.getChannel().sendMessage("⚠️ " + member.getAsMention() + ", merci de ralentir l'envoi de messages.").queue();
            lastWarnTime.put(memberKey, now);
            moderationLogListener.logAction(event.getGuild(), "AUTOMOD_WARN", null, member, "Spam détecté", "> **Strike :** " + currentStrikes + "/" + strikesToTimeout);
        }
    }
}
