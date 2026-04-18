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
     * @param channel    Le salon textuel (récupéré depuis le Guild)
     * @param action     L'action effectuée (ex: "KICK", "BAN", "CLEAR")
     * @param moderator  Le modérateur qui a effectué l'action
     * @param target     Le membre ciblé (peut être null pour /clear)
     * @param reason     La raison de l'action
     * @param extra      Infos supplémentaires (ex: nombre de messages supprimés)
     */
    public void log(TextChannel channel, String action, Member moderator, Member target, String reason, String extra) {
        if (channel == null) {
            logger.warn("Log de moderation ignore: channel null pour action={}", action);
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm:ss");
        String timestamp = OffsetDateTime.now().format(formatter);

        Color color;
        String emoji;
        switch (action.toUpperCase()) {
            case "KICK":
                color = new Color(255, 165, 0); // Orange
                emoji = "\uD83D\uDC62"; // 👢
                break;
            case "BAN":
                color = new Color(220, 20, 60); // Rouge
                emoji = "\uD83D\uDD28"; // 🔨
                break;
            case "CLEAR":
                color = new Color(30, 144, 255); // Bleu
                emoji = "\uD83E\uDDF9"; // 🧹
                break;
            case "TIMEOUT":
                color = new Color(148, 0, 211); // Violet
                emoji = "\u23F3"; // ⏳
                break;
            case "UNBAN":
                color = new Color(60, 179, 113); // Vert
                emoji = "\uD83D\uDD13"; // 🔓
                break;
            case "WARN":
                color = new Color(255, 215, 0); // Jaune
                emoji = "\u26A0\uFE0F"; // ⚠️
                break;
            case "CLEARWARN":
                color = new Color(70, 130, 180); // Acier
                emoji = "\uD83E\uDDFE"; // 🧾
                break;
            case "MESSAGE_EDIT":
                color = new Color(123, 104, 238); // Medium slate blue
                emoji = "\u270F\uFE0F"; // ✏️
                break;
            case "MESSAGE_DELETE":
                color = new Color(255, 99, 71); // Tomato
                emoji = "\uD83D\uDDD1\uFE0F"; // 🗑️
                break;
            case "VOICE_CONNECT":
                color = new Color(46, 204, 113); // Vert
                emoji = "\uD83D\uDD0A"; // 🔊
                break;
            case "VOICE_DISCONNECT":
                color = new Color(241, 196, 15); // Jaune
                emoji = "\uD83D\uDD07"; // 🔇
                break;
            case "VOICE_MOD_DISCONNECT":
                color = new Color(231, 76, 60); // Rouge
                emoji = "\uD83D\uDEAB"; // 🚫
                break;
            case "VOICE_MOVE":
                color = new Color(52, 152, 219); // Bleu
                emoji = "\uD83D\uDD01"; // 🔁
                break;
            case "VOICE_SELF_MUTE":
            case "VOICE_SELF_UNMUTE":
            case "VOICE_SELF_DEAFEN":
            case "VOICE_SELF_UNDEAFEN":
                color = new Color(155, 89, 182); // Violet
                emoji = "\uD83C\uDFA7"; // 🎧
                break;
            case "VOICE_GUILD_MUTE":
            case "VOICE_GUILD_UNMUTE":
            case "VOICE_GUILD_DEAFEN":
            case "VOICE_GUILD_UNDEAFEN":
                color = new Color(230, 126, 34); // Orange
                emoji = "\uD83D\uDEE1\uFE0F"; // 🛡️
                break;
            default:
                color = new Color(128, 128, 128);
                emoji = "\uD83D\uDCCB"; // 📋
                break;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(emoji + " Action de Modération : " + action.toUpperCase());
        embed.setColor(color);

        embed.addField("\uD83D\uDC6E Modérateur", moderator != null ? moderator.getAsMention() : "Inconnu", true);

        if (target != null) {
            embed.addField("\uD83C\uDFAF Cible", target.getUser().getName() + " (" + target.getId() + ")", true);
        }

        if (reason != null && !reason.isEmpty()) {
            embed.addField("\uD83D\uDCDD Raison", reason, false);
        }

        if (extra != null && !extra.isEmpty()) {
            embed.addField("\u2139️ Détails", extra, false);
        }

        embed.setFooter(timestamp);

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> logger.debug("Log moderation envoye: action={}, channelId={}", action, channel.getId()),
                error -> logger.error("Echec envoi log moderation: action={}, channelId={}", action, channel.getId(), error)
        );
    }
}
