package fr.sakura.bot.protect;

import fr.sakura.bot.database.ProtectSettingsManager;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PhishingProtectionListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(PhishingProtectionListener.class);
    private static final long WARNING_COOLDOWN_MS = 15_000L;

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

        StringBuilder contentToScan = new StringBuilder(event.getMessage().getContentRaw());
        for (MessageEmbed embed : event.getMessage().getEmbeds()) {
            if (embed.getUrl() != null) contentToScan.append(" ").append(embed.getUrl());
            if (embed.getDescription() != null) contentToScan.append(" ").append(embed.getDescription());
            if (embed.getTitle() != null) contentToScan.append(" ").append(embed.getTitle());
            if (embed.getAuthor() != null && embed.getAuthor().getUrl() != null) contentToScan.append(" ").append(embed.getAuthor().getUrl());
            if (embed.getFooter() != null && embed.getFooter().getText() != null) contentToScan.append(" ").append(embed.getFooter().getText());
        }

        Collection<String> allowlist = protectSettingsManager.getPhishingAllowlist(guildId);
        phishingService.detectAsync(contentToScan.toString(), allowlist).thenAccept(result -> {
            if (!result.phishingFound()) return;

            logger.warn("Protect phishing decision guildId={}, userId={}, domain={}, reason={}",
                    guildId, member.getId(), result.matchedDomain(), result.reason());

            event.getMessage().delete().queue(null, err -> logger.debug("Message already deleted or missing permission"));

            String key = guildId + ":" + member.getId();
            long now = System.currentTimeMillis();
            long last = warningCooldown.getOrDefault(key, 0L);
            if (now - last >= WARNING_COOLDOWN_MS) {
                warningCooldown.put(key, now);
                event.getChannel().sendMessage("âš ï¸ " + member.getAsMention() + ", lien bloquÃ© par Sakura Protect (risque phishing).")
                        .queue();
            }

            moderationLogListener.logAction(event.getGuild(), "AUTOMOD_WARN", null, member,
                    "Sakura Protect: lien de phishing dÃ©tectÃ©",
                    "> **URL :** " + result.matchedUrl() + "\n> **Domaine :** " + result.matchedDomain() + "\n> **RÃ¨gle :** " + result.reason());
        });
    }
}
