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

public class ModerationLogger {

    private static final Logger logger = LoggerFactory.getLogger(ModerationLogger.class);

    private final String logChannelId;

    public ModerationLogger(String logChannelId) {
        this.logChannelId = logChannelId;
        logger.info("ModerationLogger initialisé (enabled={}, channelId={})", isEnabled(), logChannelId);
    }

    public String getLogChannelId() { return logChannelId; }

    public boolean isEnabled() {
        return logChannelId != null && !logChannelId.isEmpty();
    }

    public TextChannel resolveLogChannel(Guild guild) {
        if (!isEnabled()) return null;
        if (guild == null) { logger.warn("Impossible de résoudre le salon de logs : guild null"); return null; }
        TextChannel channel = guild.getTextChannelById(logChannelId);
        if (channel == null) logger.warn("Salon de logs introuvable : guildId={}, channelId={}", guild.getId(), logChannelId);
        return channel;
    }

    /** Point d'entrée général — utilisé par toutes les commandes de modération. */
    public void logInGuild(Guild guild, String action, Member moderator, Member target, String reason, String extra) {
        TextChannel channel = resolveLogChannel(guild);
        log(channel, action, moderator, target, reason, extra);
    }

    // ── Logs spécialisés messages ────────────────────────────────────────────────

    /**
     * Log dédié pour l'édition de message — affiche avant/après.
     *
     * @param guild      Guild concernée
     * @param author     Membre qui a modifié le message (peut être null)
     * @param channelId  ID du salon
     * @param messageId  ID du message
     * @param before     Contenu avant modification (null = non disponible)
     * @param after      Contenu après modification
     */
    public void logMessageEdit(Guild guild, Member author,
                               String channelId, String messageId,
                               String before, String after) {
        TextChannel channel = resolveLogChannel(guild);
        if (channel == null) return;

        ActionStyle style = ACTION_STYLES.get("MESSAGE_EDIT");
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(style.color());
        embed.setTitle(style.emoji() + "  " + style.label());

        if (author != null) {
            embed.setThumbnail(author.getUser().getEffectiveAvatarUrl() + "?size=64");
        }

        StringBuilder desc = new StringBuilder();
        desc.append("━━━━━━━━━━━━━━━━━━\n");
        desc.append("**ÉDITION DE MESSAGE**\n");
        desc.append("━━━━━━━━━━━━━━━━━━\n\n");

        if (author != null) {
            desc.append("> **Auteur :** ").append(author.getAsMention());
        }
        desc.append("> **Salon :** <#").append(channelId).append(">\n");
        desc.append("> **Lien :** [Aller au message](https://discord.com/channels/")
                .append(guild.getId()).append("/").append(channelId).append("/").append(messageId).append(")\n");

        // Bloc "Avant"
        desc.append("\n📋 **» Avant**\n");
        if (before != null && !before.isBlank()) {
            desc.append("```\n").append(sanitizeCodeblock(before, 400)).append("\n```");
        } else {
            desc.append("```\n*(non disponible)*\n```");
        }

        // Bloc "Après"
        desc.append("\n✏️ **» Après**\n");
        if (after != null && !after.isBlank()) {
            desc.append("```\n").append(sanitizeCodeblock(after, 400)).append("\n```");
        } else {
            desc.append("```\n*(vide)*\n```");
        }

        embed.setDescription(desc.toString());

        // Footer
        StringBuilder footer = new StringBuilder();
        footer.append("Message : ").append(messageId);
        if (author != null) footer.append("  •  Auteur : ").append(author.getId());
        footer.append("  •  ").append(EmbedStyle.moderationTimestampNow());
        embed.setFooter(footer.toString());

        channel.sendMessageEmbeds(embed.build()).queue(
                ok    -> logger.debug("Log MESSAGE_EDIT envoyé channelId={}", channel.getId()),
                error -> logger.error("Échec log MESSAGE_EDIT channelId={}", channel.getId(), error)
        );
    }

    /**
     * Log dédié pour la suppression de message.
     *
     * @param guild     Guild concernée
     * @param author    Membre auteur du message supprimé (null si inconnu)
     * @param channelId ID du salon
     * @param messageId ID du message
     * @param content   Contenu du message supprimé (null si non disponible)
     */
    public void logMessageDelete(Guild guild, Member author,
                                 String channelId, String messageId,
                                 String content) {
        TextChannel channel = resolveLogChannel(guild);
        if (channel == null) return;

        ActionStyle style = ACTION_STYLES.get("MESSAGE_DELETE");
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(style.color());
        embed.setTitle(style.emoji() + "  " + style.label());

        if (author != null) {
            embed.setThumbnail(author.getUser().getEffectiveAvatarUrl() + "?size=64");
        }

        StringBuilder desc = new StringBuilder();
        desc.append("━━━━━━━━━━━━━━━━━━\n");
        desc.append("**MESSAGE SUPPRIMÉ**\n");
        desc.append("━━━━━━━━━━━━━━━━━━\n\n");

        if (author != null) {
            desc.append("> **Auteur :** ").append(author.getAsMention());
        } else {
            desc.append("> **Auteur :** *inconnu*\n");
        }
        desc.append("> **Salon :** <#").append(channelId).append(">\n");

        // Contenu
        desc.append("\n💬 **» Contenu**\n");
        if (content != null && !content.isBlank()) {
            desc.append("```\n").append(sanitizeCodeblock(content, 800)).append("\n```");
        } else {
            desc.append("```\n*(message trop ancien ou non intercepté)*\n```");
        }

        embed.setDescription(desc.toString());

        StringBuilder footer = new StringBuilder();
        footer.append("Message : ").append(messageId);
        if (author != null) footer.append("  •  Auteur : ").append(author.getId());
        footer.append("  •  ").append(EmbedStyle.moderationTimestampNow());
        embed.setFooter(footer.toString());

        channel.sendMessageEmbeds(embed.build()).queue(
                ok    -> logger.debug("Log MESSAGE_DELETE envoyé channelId={}", channel.getId()),
                error -> logger.error("Échec log MESSAGE_DELETE channelId={}", channel.getId(), error)
        );
    }

    // ── Styles par action ────────────────────────────────────────────────────────

    private record ActionStyle(Color color, String emoji, String label) {}

    private static final Map<String, ActionStyle> ACTION_STYLES = Map.ofEntries(
            Map.entry("KICK",                 new ActionStyle(new Color(255, 165,   0), "👢", "Expulsion")),
            Map.entry("BAN",                  new ActionStyle(new Color(220,  20,  60), "🔨", "Bannissement")),
            Map.entry("CLEAR",                new ActionStyle(new Color( 30, 144, 255), "🧹", "Nettoyage")),
            Map.entry("TIMEOUT",              new ActionStyle(new Color(148,   0, 211), "⏳", "Timeout")),
            Map.entry("UNBAN",                new ActionStyle(new Color( 60, 179, 113), "🔓", "Débannissement")),
            Map.entry("WARN",                 new ActionStyle(new Color(255, 215,   0), "⚠️", "Avertissement")),
            Map.entry("CLEARWARN",            new ActionStyle(new Color( 70, 130, 180), "🧾", "Reset des avertissements")),
            Map.entry("MESSAGE_EDIT",         new ActionStyle(new Color(123, 104, 238), "✏️", "Message modifié")),
            Map.entry("MESSAGE_DELETE",       new ActionStyle(new Color(255,  99,  71), "🗑️", "Message supprimé")),
            Map.entry("VOICE_CONNECT",        new ActionStyle(new Color( 46, 204, 113), "🔊", "Connexion vocale")),
            Map.entry("VOICE_DISCONNECT",     new ActionStyle(new Color(241, 196,  15), "🔇", "Déconnexion vocale")),
            Map.entry("VOICE_MOD_DISCONNECT", new ActionStyle(new Color(231,  76,  60), "🚫", "Déconnexion modérée")),
            Map.entry("VOICE_MOVE",           new ActionStyle(new Color( 52, 152, 219), "🔁", "Déplacement vocal")),
            Map.entry("VOICE_SELF_MUTE",      new ActionStyle(new Color(155,  89, 182), "🎙️", "Auto-mute")),
            Map.entry("VOICE_SELF_UNMUTE",    new ActionStyle(new Color(155,  89, 182), "🎙️", "Auto-unmute")),
            Map.entry("VOICE_SELF_DEAFEN",    new ActionStyle(new Color(155,  89, 182), "🎧", "Auto-deafen")),
            Map.entry("VOICE_SELF_UNDEAFEN",  new ActionStyle(new Color(155,  89, 182), "🎧", "Auto-undeafen")),
            Map.entry("VOICE_GUILD_MUTE",     new ActionStyle(new Color(230, 126,  34), "🛡️", "Mute serveur")),
            Map.entry("VOICE_GUILD_UNMUTE",   new ActionStyle(new Color(230, 126,  34), "🛡️", "Unmute serveur")),
            Map.entry("VOICE_GUILD_DEAFEN",   new ActionStyle(new Color(230, 126,  34), "🛡️", "Deafen serveur")),
            Map.entry("VOICE_GUILD_UNDEAFEN", new ActionStyle(new Color(230, 126,  34), "🛡️", "Undeafen serveur")),
            Map.entry("TICKET_CREATE",        new ActionStyle(new Color( 52, 152, 219), "🎫", "Ticket ouvert")),
            Map.entry("TICKET_CLAIM",         new ActionStyle(new Color( 46, 204, 113), "✅", "Ticket pris en charge")),
            Map.entry("TICKET_CLOSE",         new ActionStyle(new Color(231,  76,  60), "🔒", "Ticket fermé")),
            Map.entry("LOCK",                 new ActionStyle(new Color(231,  76,  60), "🔒", "Salon verrouillé")),
            Map.entry("UNLOCK",               new ActionStyle(new Color( 46, 204, 113), "🔓", "Salon déverrouillé")),
            Map.entry("SLOWMODE",             new ActionStyle(new Color( 52, 152, 219), "🐢", "Slowmode modifié")),
            Map.entry("SAY",                  new ActionStyle(new Color(155,  89, 182), "🗣️", "Annonce staff"))
    );

    private static final ActionStyle DEFAULT_STYLE =
            new ActionStyle(new Color(128, 128, 128), "📋", "Action");

    // ── Log générique (commandes de modération) ──────────────────────────────────

    public void log(TextChannel channel, String action, Member moderator, Member target, String reason, String extra) {
        if (channel == null) {
            logger.warn("Log de modération ignoré : channel null pour action={}", action);
            return;
        }

        String actionKey = action != null ? action.toUpperCase(Locale.ROOT) : "UNKNOWN";
        ActionStyle style = ACTION_STYLES.getOrDefault(actionKey, DEFAULT_STYLE);
        String timestamp  = EmbedStyle.moderationTimestampNow();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(style.color());
        embed.setTitle(style.emoji() + "  " + style.label());

        User thumbUser = target != null ? target.getUser()
                : moderator != null ? moderator.getUser() : null;
        if (thumbUser != null) {
            embed.setThumbnail(thumbUser.getEffectiveAvatarUrl() + "?size=64");
        }

        StringBuilder desc = new StringBuilder();
        boolean isSelfAction = moderator != null && target != null
                && moderator.getId().equals(target.getId());

        if (isSelfAction) {
            desc.append("> **Utilisateur :** ").append(moderator.getAsMention())
                    .append(" `").append(moderator.getUser().getName()).append("`\n");
        } else {
            if (moderator != null) {
                desc.append("> **Modérateur :** ").append(moderator.getAsMention())
                        .append(" `").append(moderator.getUser().getName()).append("`\n");
            } else {
                desc.append("> **Modérateur :** *Système*\n");
            }
            if (target != null) {
                desc.append("> **Cible :** ").append(target.getAsMention())
                        .append(" `").append(target.getUser().getName()).append("`\n");
            }
        }

        if (reason != null && !reason.isBlank()) {
            desc.append("> **Raison :** ").append(EmbedStyle.truncate(reason, 400)).append("\n");
        }

        if (extra != null && !extra.isBlank()) {
            desc.append("\n").append(EmbedStyle.truncate(extra, 600));
        }

        embed.setDescription(desc.toString());
        embed.setFooter(buildFooter(moderator, target, timestamp));

        channel.sendMessageEmbeds(embed.build()).queue(
                ok    -> logger.debug("Log modération envoyé : action={}, channelId={}", actionKey, channel.getId()),
                error -> logger.error("Échec envoi log modération : action={}, channelId={}", actionKey, channel.getId(), error)
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static String buildFooter(Member moderator, Member target, String timestamp) {
        StringBuilder footer = new StringBuilder();
        boolean isSelf = moderator != null && target != null && moderator.getId().equals(target.getId());

        if (isSelf) {
            footer.append("ID : ").append(moderator.getId());
        } else {
            if (target    != null) footer.append("Cible : ").append(target.getId());
            if (moderator != null) {
                if (!footer.isEmpty()) footer.append("  •  ");
                footer.append("Mod : ").append(moderator.getId());
            }
        }
        if (!footer.isEmpty()) footer.append("  •  ");
        footer.append(timestamp);
        return footer.toString();
    }

    /**
     * Sanitize un contenu pour affichage dans un codeblock Discord :
     * échappe les backticks triples pour éviter de casser le bloc.
     */
    private static String sanitizeCodeblock(String content, int maxLen) {
        String safe = content.replace("```", "` ` `");
        return safe.length() > maxLen ? safe.substring(0, maxLen) + "…" : safe;
    }
}