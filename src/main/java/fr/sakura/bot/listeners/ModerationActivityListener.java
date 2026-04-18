package fr.sakura.bot.listeners;

import fr.sakura.bot.utils.ModerationLogger;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildDeafenEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildMuteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceSelfDeafenEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceSelfMuteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Listener central pour les activites moderation/messages/vocal.
 */
public class ModerationActivityListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ModerationActivityListener.class);
    private static final long AUDIT_MATCH_WINDOW_SECONDS = 12;

    private final ModerationLogger moderationLogger;

    public ModerationActivityListener(ModerationLogger moderationLogger) {
        this.moderationLogger = moderationLogger;
    }

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        if (!event.isFromGuild()) {
            return;
        }


        Member author = event.getMember();
        if (event.getAuthor().isBot()) return;
        String content = event.getMessage().getContentDisplay();
        String reason = "Message modifie";
        String extra = "messageId=" + event.getMessageId()
                + " | channelId=" + event.getChannel().getId()
                + " | contenu=" + (content.isBlank() ? "(indisponible/vide)" : truncate(content, 300));

        moderationLogger.logInGuild(event.getGuild(), "MESSAGE_EDIT", author, author, reason, extra);
        logger.info("Message modifie: guildId={}, channelId={}, messageId={}, authorId={}",
                event.getGuild().getId(),
                event.getChannel().getId(),
                event.getMessageId(),
                event.getAuthor().getId());
    }

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        if (!event.isFromGuild()) {
            return;
        }

        String extra = "messageId=" + event.getMessageId() + " | channelId=" + event.getChannel().getId();
        moderationLogger.logInGuild(event.getGuild(), "MESSAGE_DELETE", null, null, "Message supprime", extra);

        logger.info("Message supprime: guildId={}, channelId={}, messageId={}",
                event.getGuild().getId(), event.getChannel().getId(), event.getMessageId());
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();

        if (event.getChannelLeft() == null && event.getChannelJoined() != null) {
            moderationLogger.logInGuild(
                    guild,
                    "VOICE_CONNECT",
                    member,
                    member,
                    "Connexion vocale",
                    "channel=" + event.getChannelJoined().getName() + " (" + event.getChannelJoined().getId() + ")"
            );
            logger.info("Connexion vocale: guildId={}, memberId={}, channelId={}", guild.getId(), member.getId(), event.getChannelJoined().getId());
            return;
        }

        if (event.getChannelLeft() != null && event.getChannelJoined() == null) {
            moderationLogger.logInGuild(
                    guild,
                    "VOICE_DISCONNECT",
                    member,
                    member,
                    "Deconnexion vocale",
                    "from=" + event.getChannelLeft().getName() + " (" + event.getChannelLeft().getId() + ")"
            );
            logger.info("Deconnexion vocale: guildId={}, memberId={}, channelId={}", guild.getId(), member.getId(), event.getChannelLeft().getId());
            tryLogModeratorVoiceDisconnect(event);
            return;
        }

        if (event.getChannelLeft() != null && event.getChannelJoined() != null) {
            moderationLogger.logInGuild(
                    guild,
                    "VOICE_MOVE",
                    member,
                    member,
                    "Deplacement vocal",
                    "from=" + event.getChannelLeft().getName() + " -> to=" + event.getChannelJoined().getName()
            );
            logger.info("Deplacement vocal: guildId={}, memberId={}, from={}, to={}",
                    guild.getId(), member.getId(), event.getChannelLeft().getId(), event.getChannelJoined().getId());
        }
    }

    @Override
    public void onGuildVoiceSelfMute(@NotNull GuildVoiceSelfMuteEvent event) {
        String action = event.isSelfMuted() ? "VOICE_SELF_MUTE" : "VOICE_SELF_UNMUTE";
        String reason = event.isSelfMuted() ? "Mute micro (self)" : "Demute micro (self)";
        moderationLogger.logInGuild(event.getGuild(), action, event.getMember(), event.getMember(), reason, null);
        logger.info("Self mute change: guildId={}, memberId={}, selfMuted={}",
                event.getGuild().getId(), event.getMember().getId(), event.isSelfMuted());
    }

    @Override
    public void onGuildVoiceSelfDeafen(@NotNull GuildVoiceSelfDeafenEvent event) {
        String action = event.isSelfDeafened() ? "VOICE_SELF_DEAFEN" : "VOICE_SELF_UNDEAFEN";
        String reason = event.isSelfDeafened() ? "Mute casque (self)" : "Demute casque (self)";
        moderationLogger.logInGuild(event.getGuild(), action, event.getMember(), event.getMember(), reason, null);
        logger.info("Self deaf change: guildId={}, memberId={}, selfDeafened={}",
                event.getGuild().getId(), event.getMember().getId(), event.isSelfDeafened());
    }

    @Override
    public void onGuildVoiceGuildMute(@NotNull GuildVoiceGuildMuteEvent event) {
        String action = event.isGuildMuted() ? "VOICE_GUILD_MUTE" : "VOICE_GUILD_UNMUTE";
        String reason = event.isGuildMuted() ? "Mute serveur" : "Demute serveur";
        moderationLogger.logInGuild(event.getGuild(), action, null, event.getMember(), reason, "Source: moderation/permissions serveur");
        logger.info("Guild mute change: guildId={}, memberId={}, guildMuted={}",
                event.getGuild().getId(), event.getMember().getId(), event.isGuildMuted());
    }

    @Override
    public void onGuildVoiceGuildDeafen(@NotNull GuildVoiceGuildDeafenEvent event) {
        String action = event.isGuildDeafened() ? "VOICE_GUILD_DEAFEN" : "VOICE_GUILD_UNDEAFEN";
        String reason = event.isGuildDeafened() ? "Mute casque serveur" : "Demute casque serveur";
        moderationLogger.logInGuild(event.getGuild(), action, null, event.getMember(), reason, "Source: moderation/permissions serveur");
        logger.info("Guild deaf change: guildId={}, memberId={}, guildDeafened={}",
                event.getGuild().getId(), event.getMember().getId(), event.isGuildDeafened());
    }

    private void tryLogModeratorVoiceDisconnect(GuildVoiceUpdateEvent event) {
        Guild guild = event.getGuild();
        Member target = event.getMember();

        guild.retrieveAuditLogs()
                .type(ActionType.MEMBER_VOICE_KICK)
                .limit(5)
                .queue(entries -> {
                    AuditLogEntry matched = findMatchingDisconnect(entries, target.getId());
                    if (matched == null) {
                        return;
                    }

                    Member moderator = matched.getUser() != null ? guild.getMemberById(matched.getUser().getId()) : null;
                    moderationLogger.logInGuild(
                            guild,
                            "VOICE_MOD_DISCONNECT",
                            moderator,
                            target,
                            "Utilisateur deconnecte du vocal par moderation",
                            "targetId=" + target.getId()
                    );

                    logger.info("Deconnexion vocale moderee detectee: guildId={}, targetId={}, moderatorId={}",
                            guild.getId(),
                            target.getId(),
                            matched.getUserId());
                }, error -> logger.warn("Impossible de lire les audit logs pour detecter une deconnexion vocale moderee", error));
    }

    private AuditLogEntry findMatchingDisconnect(List<AuditLogEntry> entries, String targetId) {
        OffsetDateTime now = OffsetDateTime.now();

        for (AuditLogEntry entry : entries) {
            if (!targetId.equals(entry.getTargetId())) {
                continue;
            }

            OffsetDateTime timeCreated = entry.getTimeCreated();
            long age = Math.abs(now.toEpochSecond() - timeCreated.toEpochSecond());
            if (age <= AUDIT_MATCH_WINDOW_SECONDS) {
                return entry;
            }
        }

        return null;
    }

    private String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}


