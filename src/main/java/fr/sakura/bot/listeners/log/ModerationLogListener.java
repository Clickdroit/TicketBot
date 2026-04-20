package fr.sakura.bot.listeners.log;

import fr.sakura.bot.utils.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.time.Instant;
import java.util.Map;

/**
 * Listener et service pour les logs de modération (Style Sakura).
 * Centralise les actions comme Kick, Ban, Warn, Timeout, etc.
 */
public class ModerationLogListener extends BaseLogListener {

    public record ActionStyle(Color color, String emoji, String label) {}

    public static final Map<String, ActionStyle> ACTION_STYLES = Map.ofEntries(
            Map.entry("KICK",                 new ActionStyle(new Color(255, 140,   0), "👢", "Expulsion")),
            Map.entry("BAN",                  new ActionStyle(new Color(200,  30,  60), "🔨", "Bannissement")),
            Map.entry("CLEAR",                new ActionStyle(new Color( 80, 160, 230), "🧹", "Nettoyage")),
            Map.entry("TIMEOUT",              new ActionStyle(new Color(160,  60, 200), "⏳", "Timeout")),
            Map.entry("UNBAN",                new ActionStyle(new Color( 80, 200, 130), "🔓", "Débannissement")),
            Map.entry("WARN",                 new ActionStyle(new Color(255, 200,  50), "⚠️", "Avertissement")),
            Map.entry("AUTOMOD_WARN",         new ActionStyle(new Color(255, 160,  60), "🤖", "AutoMod")),
            Map.entry("CLEARWARN",            new ActionStyle(new Color(100, 160, 210), "🧾", "Reset warnings")),
            Map.entry("LOCK",                 new ActionStyle(new Color(219,  98, 152), "🔒", "Salon verrouillé")),
            Map.entry("UNLOCK",               new ActionStyle(new Color(130, 210, 150), "🔓", "Salon déverrouillé")),
            Map.entry("SLOWMODE",             new ActionStyle(new Color( 80, 160, 220), "🐢", "Slowmode")),
            Map.entry("SAY",                  new ActionStyle(new Color(198, 168, 230), "🗣️", "Annonce staff")),
            Map.entry("EMBED",                new ActionStyle(new Color(255, 168, 204), "🖼️", "Embed staff")),
            Map.entry("TICKET_CREATE",        new ActionStyle(new Color(255, 168, 204), "🎫", "Ticket ouvert")),
            Map.entry("TICKET_CLAIM",         new ActionStyle(new Color( 80, 200, 130), "✅", "Ticket pris")),
            Map.entry("TICKET_CLOSE",         new ActionStyle(new Color(219,  98, 152), "🔒", "Ticket fermé"))
    );

    public ModerationLogListener(String logChannelId) {
        super(logChannelId);
    }

    /**
     * Log une action de modération en utilisant les styles prédéfinis (Cible Member).
     */
    public void logAction(@NotNull Guild guild, @NotNull String actionKey, @Nullable Member moderator, @NotNull Member target, @Nullable String reason, @Nullable String extra) {
        logAction(guild, actionKey, moderator, target.getUser(), reason, extra);
    }

    /**
     * Log une action de modération en utilisant les styles prédéfinis (Cible User).
     */
    public void logAction(@NotNull Guild guild, @NotNull String actionKey, @Nullable Member moderator, @NotNull User target, @Nullable String reason, @Nullable String extra) {
        ActionStyle style = ACTION_STYLES.getOrDefault(actionKey.toUpperCase(), new ActionStyle(EmbedStyle.SAKURA_PINK, "🌸", "Action"));
        
        sendLogToChannel(guild, embed -> {
            embed.setColor(style.color());
            embed.setTitle(style.emoji() + "  ✦  " + style.label());
            
            StringBuilder desc = new StringBuilder();
            desc.append(EmbedStyle.SEPARATOR).append("\n");
            desc.append(style.emoji()).append(" **").append(style.label().toUpperCase()).append("**\n");
            desc.append(EmbedStyle.SEPARATOR).append("\n\n");
            
            if (moderator != null) {
                desc.append(EmbedStyle.detailLine("Modérateur", moderator.getAsMention() + " (`" + moderator.getUser().getName() + "`)")).append("\n");
            } else {
                desc.append(EmbedStyle.detailLine("Modérateur", "🤖 *Système Automatique*")).append("\n");
            }
            
            desc.append(EmbedStyle.detailLine("Cible", target.getAsMention() + " (`" + target.getName() + "`)")).append("\n");
            
            if (reason != null && !reason.isBlank()) {
                desc.append("\n").append(EmbedStyle.SEP_LIGHT).append("\n");
                desc.append(EmbedStyle.detailLine("Raison", reason)).append("\n");
            }
            
            if (extra != null && !extra.isBlank()) {
                desc.append("\n").append(extra);
            }

            embed.setDescription(desc.toString());
            embed.setThumbnail(target.getEffectiveAvatarUrl());
            embed.setTimestamp(Instant.now());
            
            // Footer dynamique Sakura
            String footerText = "Cible : " + target.getId();
            if (moderator != null) footerText += "  ✦  Mod : " + moderator.getId();
            EmbedStyle.setFooter(embed, footerText);
        });
    }
}
