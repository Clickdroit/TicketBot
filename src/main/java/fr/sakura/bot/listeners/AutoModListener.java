package fr.sakura.bot.listeners;

import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class AutoModListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AutoModListener.class);

    private final ModerationLogListener moderationLogListener;
    private final SettingsManager settingsManager;

    private final Map<String, Long> lastWarnTime = new ConcurrentHashMap<>();
    
    // Anti-spam data
    private final Map<String, UserSpamData> spamTracker = new ConcurrentHashMap<>();

    // Pattern simple pour les liens (http/https/discord.gg)
    private static final Pattern LINK_PATTERN = Pattern.compile("https?://\\S+|discord\\.gg/\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GIF_PATTERN = Pattern.compile("tenor\\.com|giphy\\.com", Pattern.CASE_INSENSITIVE);

    private record UserSpamData(long lastMessageTime, int messageCount, int strikes, long lastStrikeTime) {}

    public AutoModListener(ModerationLogListener moderationLogListener, SettingsManager settingsManager) {
        this.moderationLogListener = moderationLogListener;
        this.settingsManager = settingsManager;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;

        Member member = event.getMember();
        if (member == null || member.hasPermission(net.dv8tion.jda.api.Permission.MESSAGE_MANAGE)) return;

        MDC.put("guildId", event.getGuild().getId());
        MDC.put("userId", event.getAuthor().getId());

        try {
            String content = event.getMessage().getContentRaw();
            String guildId = event.getGuild().getId();

            // ── Anti-Spam ─────────────────────────────────────────────────────────────
            if (settingsManager.isAntiSpamEnabled(guildId)) {
                handleSpamCheck(event, member, guildId);
            }

            // ── Anti-Liens ─────────────────────────────────────────────────────────────
            if (settingsManager.isAntiLinkEnabled(guildId)) {
                if (LINK_PATTERN.matcher(content).find()) {
                    // Vérifier si c'est un GIF et si c'est autorisé
                    boolean isGif = GIF_PATTERN.matcher(content).find();
                    if (isGif && settingsManager.isGifLinksAllowed(guildId)) {
                        return;
                    }

                    event.getMessage().delete().queue();
                    
                    long now = System.currentTimeMillis();
                    long last = lastWarnTime.getOrDefault(member.getId(), 0L);
                    long noticeCooldown = settingsManager.getAutomodNoticeCooldownSeconds(guildId) * 1000L;
                    
                    if (now - last > noticeCooldown) {
                        event.getChannel().sendMessage("⚠️ " + member.getAsMention() + ", les liens ne sont pas autorisés ici.").queue();
                        lastWarnTime.put(member.getId(), now);
                        
                        moderationLogListener.logAction(event.getGuild(), "AUTOMOD_WARN", null, member, "Envoi de lien non autorisé", "> **Contenu :** " + content);
                    }
                    
                    logger.info("AutoMod: Lien supprimé guildId={}, userId={}", guildId, member.getId());
                }
            }
        } finally {
            MDC.remove("guildId");
            MDC.remove("userId");
        }
    }

    private void handleSpamCheck(MessageReceivedEvent event, Member member, String guildId) {
        long now = System.currentTimeMillis();
        String userId = member.getId();
        
        long windowMs = settingsManager.getSpamWindowMs(guildId);
        int limit = settingsManager.getSpamLimit(guildId);
        int strikeResetMs = settingsManager.getAutomodStrikeResetMinutes(guildId) * 60 * 1000;

        UserSpamData data = spamTracker.getOrDefault(userId, new UserSpamData(now, 0, 0, 0));
        
        // Reset strikes if enough time passed
        int currentStrikes = data.strikes();
        if (data.lastStrikeTime() > 0 && now - data.lastStrikeTime() > strikeResetMs) {
            currentStrikes = 0;
        }

        int currentCount = data.messageCount();
        if (now - data.lastMessageTime() < windowMs) {
            currentCount++;
        } else {
            currentCount = 1;
        }

        if (currentCount > limit) {
            currentCount = 0; // Reset count after detection to avoid double triggers
            currentStrikes++;
            
            event.getMessage().delete().queue();
            
            long lastNotice = lastWarnTime.getOrDefault(userId, 0L);
            long noticeCooldown = settingsManager.getAutomodNoticeCooldownSeconds(guildId) * 1000L;

            int strikesToTimeout = settingsManager.getAutomodStrikesToTimeout(guildId);
            
            if (currentStrikes >= strikesToTimeout) {
                int timeoutMinutes = settingsManager.getAutomodTimeoutMinutes(guildId);
                member.timeoutFor(timeoutMinutes, TimeUnit.MINUTES).reason("Anti-spam (AutoMod)").queue(
                        success -> {
                            event.getChannel().sendMessage("⏳ " + member.getAsMention() + " a été mis en sourdine pendant " + timeoutMinutes + " minutes pour spam répétitif.").queue();
                            moderationLogListener.logAction(event.getGuild(), "TIMEOUT", null, member, "Spam répétitif (AutoMod)", "> **Strikes :** " + strikesToTimeout);
                        },
                        error -> logger.error("Echec timeout AutoMod guildId={}, userId={}", guildId, userId, error)
                );
                currentStrikes = 0; // Reset after sanction
            } else if (now - lastNotice > noticeCooldown) {
                event.getChannel().sendMessage("⚠️ " + member.getAsMention() + ", merci de ralentir l'envoi de messages.").queue();
                lastWarnTime.put(userId, now);
                moderationLogListener.logAction(event.getGuild(), "AUTOMOD_WARN", null, member, "Spam détecté", "> **Strike :** " + currentStrikes + "/" + strikesToTimeout);
            }
            
            data = new UserSpamData(now, currentCount, currentStrikes, now);
        } else {
            data = new UserSpamData(now, currentCount, currentStrikes, data.lastStrikeTime());
        }
        
        spamTracker.put(userId, data);
    }
}
