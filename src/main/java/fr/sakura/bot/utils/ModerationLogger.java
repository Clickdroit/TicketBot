package fr.sakura.bot.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Utilitaire pour envoyer des logs de modération dans un salon dédié.
 */
public class ModerationLogger {

    private static final Logger logger = LoggerFactory.getLogger(ModerationLogger.class);

    private final String logChannelId;

    public ModerationLogger(String logChannelId) {
        this.logChannelId = logChannelId;
        logger.info("ModerationLogger initialise (enabled={}, channelId={})", isEnabled(), logChannelId);
    }

    public String getLogChannelId() {
        return logChannelId;
    }

    public boolean isEnabled() {
        return logChannelId != null && !logChannelId.isEmpty();
    }

    /**
     * Resolve le salon de logs configure dans la guilde.
     */
    public TextChannel resolveLogChannel(Guild guild) {
        if (!isEnabled()) {
            logger.debug("Logs moderation desactives: LOG_CHANNEL_ID non configure");
            return null;
        }

        if (guild == null) {
            logger.warn("Impossible de resoudre le salon de logs: guild null");
            return null;
        }

        TextChannel channel = guild.getTextChannelById(logChannelId);
        if (channel == null) {
            logger.warn("Salon de logs moderation introuvable: guildId={}, channelId={}", guild.getId(), logChannelId);
        }
        return channel;
    }

    /**
     * Ecrit un log de moderation en resolvant automatiquement le salon configure.
     */
    public void logInGuild(Guild guild, String action, Member moderator, Member target, String reason, String extra) {
        TextChannel channel = resolveLogChannel(guild);
        log(channel, action, moderator, target, reason, extra);
    }

    /**
     * Envoie un log de modération dans le salon configuré.
     *
     */
    private record ActionStyle(Color color, String emoji) {}

    private static final Map<String, ActionStyle> ACTION_STYLES = Map.ofEntries(
            Map.entry("KICK",                 new ActionStyle(new Color(255, 165, 0),   "👢")),
            Map.entry("BAN",                  new ActionStyle(new Color(220, 20, 60),   "🔨")),
            Map.entry("CLEAR",                new ActionStyle(new Color(30, 144, 255),  "🧹")),
            Map.entry("TIMEOUT",              new ActionStyle(new Color(148, 0, 211),   "⏳")),
            Map.entry("UNBAN",                new ActionStyle(new Color(60, 179, 113),  "🔓")),
            Map.entry("WARN",                 new ActionStyle(new Color(255, 215, 0),   "⚠️")),
            Map.entry("CLEARWARN",            new ActionStyle(new Color(70, 130, 180),  "🧾")),
            Map.entry("MESSAGE_EDIT",         new ActionStyle(new Color(123, 104, 238), "✏️")),
            Map.entry("MESSAGE_DELETE",       new ActionStyle(new Color(255, 99, 71),   "🗑️")),
            Map.entry("VOICE_CONNECT",        new ActionStyle(new Color(46, 204, 113),  "🔊")),
            Map.entry("VOICE_DISCONNECT",     new ActionStyle(new Color(241, 196, 15),  "🔇")),
            Map.entry("VOICE_MOD_DISCONNECT", new ActionStyle(new Color(231, 76, 60),   "🚫")),
            Map.entry("VOICE_MOVE",           new ActionStyle(new Color(52, 152, 219),  "🔁")),
            Map.entry("VOICE_SELF_MUTE",      new ActionStyle(new Color(155, 89, 182),  "🎧")),
            Map.entry("VOICE_SELF_UNMUTE",    new ActionStyle(new Color(155, 89, 182),  "🎧")),
            Map.entry("VOICE_SELF_DEAFEN",    new ActionStyle(new Color(155, 89, 182),  "🎧")),
            Map.entry("VOICE_SELF_UNDEAFEN",  new ActionStyle(new Color(155, 89, 182),  "🎧")),
            Map.entry("VOICE_GUILD_MUTE",     new ActionStyle(new Color(230, 126, 34),  "🛡️")),
            Map.entry("VOICE_GUILD_UNMUTE",   new ActionStyle(new Color(230, 126, 34),  "🛡️")),
            Map.entry("VOICE_GUILD_DEAFEN",   new ActionStyle(new Color(230, 126, 34),  "🛡️")),
            Map.entry("VOICE_GUILD_UNDEAFEN", new ActionStyle(new Color(230, 126, 34),  "🛡️"))
    );

    private static final ActionStyle DEFAULT_STYLE = new ActionStyle(new Color(128, 128, 128), "📋");

    private String truncateField(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max - 3) + "...";
    }

    public void log(TextChannel channel, String action, Member moderator, Member target, String reason, String extra) {
        if (channel == null) {
            logger.warn("Log de moderation ignore: channel null pour action={}", action);
            return;
        }

        String actionKey = action != null ? action.toUpperCase() : "UNKNOWN";
        ActionStyle style = ACTION_STYLES.getOrDefault(actionKey, DEFAULT_STYLE);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm:ss");
        String timestamp = OffsetDateTime.now().format(formatter);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(truncateField(style.emoji() + " Action de Modération : " + actionKey, 256));
        embed.setColor(style.color());

        embed.addField("👮 Modérateur", moderator != null ? moderator.getAsMention() : "Inconnu / Système", true);

        if (target != null) {
            embed.addField("🎯 Cible", truncateField(target.getUser().getName() + " (<@" + target.getId() + ">)", 1024), true);
        }

        if (reason != null && !reason.isBlank()) {
            embed.addField("📝 Raison", truncateField(reason, 1024), false);
        }

        if (extra != null && !extra.isBlank()) {
            embed.addField("ℹ️ Détails", truncateField(extra, 1024), false);
        }

        embed.setFooter(timestamp);

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> logger.debug("Log moderation envoye: action={}, channelId={}", actionKey, channel.getId()),
                error  -> logger.error("Echec envoi log moderation: action={}, channelId={}", actionKey, channel.getId(), error)
        );
    }
}
