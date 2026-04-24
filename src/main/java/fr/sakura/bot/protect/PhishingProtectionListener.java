package fr.sakura.bot.protect;

import fr.sakura.bot.database.ProtectSettingsManager;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PhishingProtectionListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(PhishingProtectionListener.class);

    private final ProtectSettingsManager protectSettingsManager;
    private final ModerationLogListener moderationLogListener;
    private final PhishingService phishingService;

    private final Map<String, Long> warningCooldown = new ConcurrentHashMap<>();

    public PhishingProtectionListener(ProtectSettingsManager protectSettingsManager,
                                      ModerationLogListener moderationLogListener,
                                      PhishingService phishingService) {
        this.protectSettingsManager = protectSettingsManager;
        this.moderationLogListener = moderationLogListener;
        this.phishingService = phishingService;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;

        String guildId = event.getGuild().getId();
        if (!protectSettingsManager.isAntiPhishingEnabled(guildId)) return;

        Member member = event.getMember();
        if (member == null) return;

        PhishingService.DetectionResult result = phishingService.detect(
                event.getMessage().getContentRaw(),
                protectSettingsManager.getPhishingAllowlist(guildId)
        );

        if (!result.phishingFound()) return;

        logger.warn("Protect phishing decision guildId={}, userId={}, domain={}, reason={}",
                guildId, member.getId(), result.matchedDomain(), result.reason());

        event.getMessage().delete().queue();

        String key = guildId + ":" + member.getId();
        long now = System.currentTimeMillis();
        long last = warningCooldown.getOrDefault(key, 0L);
        if (now - last >= 15_000) {
            warningCooldown.put(key, now);
            event.getChannel().sendMessage("⚠️ " + member.getAsMention() + ", lien bloqué par Sakura Protect (risque phishing).")
                    .queue();
        }

        moderationLogListener.logAction(event.getGuild(), "AUTOMOD_WARN", null, member,
                "Sakura Protect: lien de phishing détecté",
                "> **URL :** " + result.matchedUrl() + "\n> **Domaine :** " + result.matchedDomain() + "\n> **Règle :** " + result.reason());
    }
}
