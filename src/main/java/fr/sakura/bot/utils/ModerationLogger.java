package fr.sakura.bot.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.Locale;
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

    private record ActionStyle(Color color, String emoji, String label) {}

    private static final Map<String, ActionStyle> ACTION_STYLES = Map.ofEntries(
            Map.entry("KICK",                 new ActionStyle(new Color(255, 165, 0),   "👢", "Expulsion")),
            Map.entry("BAN",                  new ActionStyle(new Color(220, 20, 60),   "🔨", "Bannissement")),
            Map.entry("CLEAR",                new ActionStyle(new Color(30, 144, 255),  "🧹", "Nettoyage")),
            Map.entry("TIMEOUT",              new ActionStyle(new Color(148, 0, 211),   "⏳", "Timeout")),
            Map.entry("UNBAN",                new ActionStyle(new Color(60, 179, 113),  "🔓", "Débannissement")),
            Map.entry("WARN",                 new ActionStyle(new Color(255, 215, 0),   "⚠️", "Avertissement")),
            Map.entry("CLEARWARN",            new ActionStyle(new Color(70, 130, 180),  "🧾", "Nettoyage des avertissements")),
            Map.entry("MESSAGE_EDIT",         new ActionStyle(new Color(123, 104, 238), "✏️", "Message modifié")),
            Map.entry("MESSAGE_DELETE",       new ActionStyle(new Color(255, 99, 71),   "🗑️", "Message supprimé")),
            Map.entry("VOICE_CONNECT",        new ActionStyle(new Color(46, 204, 113),  "🔊", "Connexion vocale")),
            Map.entry("VOICE_DISCONNECT",     new ActionStyle(new Color(241, 196, 15),  "🔇", "Déconnexion vocale")),
            Map.entry("VOICE_MOD_DISCONNECT", new ActionStyle(new Color(231, 76, 60),   "🚫", "Déconnexion vocale modérée")),
            Map.entry("VOICE_MOVE",           new ActionStyle(new Color(52, 152, 219),  "🔁", "Déplacement vocal")),
            Map.entry("VOICE_SELF_MUTE",      new ActionStyle(new Color(155, 89, 182),  "🎧", "Auto-mute")),
            Map.entry("VOICE_SELF_UNMUTE",    new ActionStyle(new Color(155, 89, 182),  "🎧", "Auto-unmute")),
            Map.entry("VOICE_SELF_DEAFEN",    new ActionStyle(new Color(155, 89, 182),  "🎧", "Auto-deafen")),
            Map.entry("VOICE_SELF_UNDEAFEN",  new ActionStyle(new Color(155, 89, 182),  "🎧", "Auto-undeafen")),
            Map.entry("VOICE_GUILD_MUTE",     new ActionStyle(new Color(230, 126, 34),  "🛡️", "Mute serveur")),
            Map.entry("VOICE_GUILD_UNMUTE",   new ActionStyle(new Color(230, 126, 34),  "🛡️", "Unmute serveur")),
            Map.entry("VOICE_GUILD_DEAFEN",   new ActionStyle(new Color(230, 126, 34),  "🛡️", "Deafen serveur")),
            Map.entry("VOICE_GUILD_UNDEAFEN", new ActionStyle(new Color(230, 126, 34),  "🛡️", "Undeafen serveur")),
            Map.entry("TICKET_CREATE",        new ActionStyle(new Color(52, 152, 219),  "🎫", "Ticket créé")),
            Map.entry("TICKET_CLAIM",         new ActionStyle(new Color(46, 204, 113),  "✅", "Ticket pris en charge")),
            Map.entry("TICKET_CLOSE",         new ActionStyle(new Color(231, 76, 60),   "🔒", "Ticket fermé")),
            Map.entry("LOCK",                 new ActionStyle(new Color(231, 76, 60),   "🔒", "Salon verrouillé")),
            Map.entry("UNLOCK",               new ActionStyle(new Color(46, 204, 113),  "🔓", "Salon déverrouillé")),
            Map.entry("SLOWMODE",             new ActionStyle(new Color(52, 152, 219),  "🐢", "Slowmode modifié")),
            Map.entry("SAY",                  new ActionStyle(new Color(155, 89, 182),  "🗣️", "Annonce staff"))
    );

    private static final ActionStyle DEFAULT_STYLE = new ActionStyle(new Color(128, 128, 128), "📋", "Action" );

    public void log(TextChannel channel, String action, Member moderator, Member target, String reason, String extra) {
        if (channel == null) {
            logger.warn("Log de moderation ignore: channel null pour action={}", action);
            return;
        }

        String actionKey = action != null ? action.toUpperCase(Locale.ROOT) : "UNKNOWN";
        ActionStyle style = ACTION_STYLES.getOrDefault(actionKey, DEFAULT_STYLE);

        String timestamp = EmbedStyle.moderationTimestampNow();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmbedStyle.truncate(style.emoji() + " " + style.label(), 256));
        embed.setColor(style.color());

        User thumbnailUser = target != null ? target.getUser() : (moderator != null ? moderator.getUser() : null);
        if (thumbnailUser != null && thumbnailUser.getEffectiveAvatarUrl() != null) {
            embed.setThumbnail(thumbnailUser.getEffectiveAvatarUrl());
        }

        boolean isSelfAction = moderator != null && target != null && moderator.getId().equals(target.getId());
        if (isSelfAction) {
            embed.addField("👤 Utilisateur", EmbedStyle.truncate(moderator.getAsMention(), 1024), true);
        } else {
            embed.addField("👮 Modérateur", EmbedStyle.truncate(moderator != null ? moderator.getAsMention() : "Système", 1024), true);
            if (target != null) {
                embed.addField("👤 Cible", EmbedStyle.truncate(target.getAsMention(), 1024), true);
            }
        }

        if (reason != null && !reason.isBlank()) {
            embed.addField("📝 Raison", EmbedStyle.truncate(reason, 1024), false);
        }

        if (extra != null && !extra.isBlank()) {
            embed.addField("📋 Détails", EmbedStyle.truncate(extra, 1024), false);
        }

        String footerIds = buildFooterIds(moderator, target);
        EmbedStyle.setFooter(embed, footerIds + " • " + timestamp);

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> logger.debug("Log moderation envoye: action={}, channelId={}", actionKey, channel.getId()),
                error  -> logger.error("Echec envoi log moderation: action={}, channelId={}", actionKey, channel.getId(), error)
        );
    }

    private static String buildFooterIds(Member moderator, Member target) {
        if (moderator != null && target != null && !moderator.getId().equals(target.getId())) {
            return "👤 ID cible: " + target.getId() + " • 👮 ID mod: " + moderator.getId();
        } else if (moderator != null) {
            return "👤 ID: " + moderator.getId();
        }
        return "Journal modération";
    }
}
