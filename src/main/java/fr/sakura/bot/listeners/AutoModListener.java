package fr.sakura.bot.listeners;

import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class AutoModListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AutoModListener.class);

    private final ModerationLogListener moderationLogListener;
    private final SettingsManager settingsManager;

    private final Map<String, Long> lastWarnTime = new ConcurrentHashMap<>();

    // Pattern simple pour les liens (http/https/discord.gg)
    private static final Pattern LINK_PATTERN = Pattern.compile("https?://\\S+|discord\\.gg/\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GIF_PATTERN = Pattern.compile("tenor\\.com|giphy\\.com", Pattern.CASE_INSENSITIVE);

    public AutoModListener(ModerationLogListener moderationLogListener, SettingsManager settingsManager) {
        this.moderationLogListener = moderationLogListener;
        this.settingsManager = settingsManager;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;

        Member member = event.getMember();
        if (member == null || member.hasPermission(net.dv8tion.jda.api.Permission.MESSAGE_MANAGE)) return;

        String content = event.getMessage().getContentRaw();
        String guildId = event.getGuild().getId();

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
                
                if (now - last > 5000) {
                    event.getChannel().sendMessage("⚠️ " + member.getAsMention() + ", les liens ne sont pas autorisés ici.").queue();
                    lastWarnTime.put(member.getId(), now);
                    
                    moderationLogListener.logAction(event.getGuild(), "AUTOMOD_WARN", null, member, "Envoi de lien non autorisé", "> **Contenu :** " + content);
                }
                
                logger.info("AutoMod: Lien supprimé guildId={}, userId={}", guildId, member.getId());
            }
        }
    }
}
