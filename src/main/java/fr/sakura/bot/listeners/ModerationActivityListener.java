package fr.sakura.bot.listeners;

import fr.sakura.bot.utils.MessageCache;
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
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;

public class ModerationActivityListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ModerationActivityListener.class);
    private static final long AUDIT_MATCH_WINDOW_SECONDS = 12;

    private final ModerationLogger moderationLogger;
    private final MessageCache messageCache = new MessageCache();

    public ModerationActivityListener(ModerationLogger moderationLogger) {
        this.moderationLogger = moderationLogger;
    }

    // ── Cache ────────────────────────────────────────────────────────────────────

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;
        String content = event.getMessage().getContentDisplay();
        if (content.isBlank()) return;

        messageCache.put(
                event.getGuild().getId(),
                event.getMessageId(),
                event.getAuthor().getId(),
                event.getAuthor().getName(),
                event.getChannel().getId(),
                content
        );
    }

    // ── Messages ─────────────────────────────────────────────────────────────────

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;

        String guildId   = event.getGuild().getId();
        String messageId = event.getMessageId();
        String channelId = event.getChannel().getId();
        String after     = event.getMessage().getContentDisplay();

        // Récupère le contenu AVANT modification depuis le cache
        MessageCache.CachedMessage cached = messageCache.getContent(guildId, messageId);
        String before = cached != null ? cached.content() : null;

        // Met à jour le cache avec le nouveau contenu (pour la prochaine édition éventuelle)
        if (!after.isBlank()) {
            messageCache.updateContent(guildId, messageId, after);
        }

        Member author = event.getMember();
        moderationLogger.logMessageEdit(event.getGuild(), author, channelId, messageId, before, after);

        logger.info("Message modifié: guildId={}, channelId={}, messageId={}, authorId={}",
                guildId, channelId, messageId, event.getAuthor().getId());
    }

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        if (!event.isFromGuild()) return;

        String guildId   = event.getGuild().getId();
        String messageId = event.getMessageId();
        String channelId = event.getChannel().getId();

        MessageCache.CachedMessage cached = messageCache.remove(guildId, messageId);

        Member author  = cached != null ? event.getGuild().getMemberById(cached.authorId()) : null;
        String content = cached != null ? cached.content() : null;

        moderationLogger.logMessageDelete(event.getGuild(), author, channelId, messageId, content);

        logger.info("Message supprimé: guildId={}, channelId={}, messageId={}, cachedContent={}",
                guildId, channelId, messageId, cached != null ? "oui" : "non");
    }

    // ── Vocal ─────────────────────────────────────────────────────────────────────

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        Guild guild   = event.getGuild();
        Member member = event.getMember();

        if (event.getChannelLeft() != null && event.getChannelJoined() != null
                && event.getChannelLeft().getId().equals(event.getChannelJoined().getId())) return;

        if (event.getChannelLeft() == null && event.getChannelJoined() != null) {
            moderationLogger.logInGuild(guild, "VOICE_CONNECT", member, member,
                    "Connexion vocale", "> **Salon :** " + event.getChannelJoined().getName());
            logger.info("Connexion vocale: guildId={}, memberId={}, channelId={}",
                    guild.getId(), member.getId(), event.getChannelJoined().getId());
            return;
        }

        if (event.getChannelLeft() != null && event.getChannelJoined() == null) {
            moderationLogger.logInGuild(guild, "VOICE_DISCONNECT", member, member,
                    "Déconnexion vocale", "> **Depuis :** " + event.getChannelLeft().getName());
            logger.info("Déconnexion vocale: guildId={}, memberId={}, channelId={}",
                    guild.getId(), member.getId(), event.getChannelLeft().getId());
            tryLogModeratorVoiceDisconnect(event);
            return;
        }

        if (event.getChannelLeft() != null && event.getChannelJoined() != null) {
            moderationLogger.logInGuild(guild, "VOICE_MOVE", member, member, "Déplacement vocal",
                    "> **De :** " + event.getChannelLeft().getName()
                            + "\n> **Vers :** " + event.getChannelJoined().getName());
            logger.info("Déplacement vocal: guildId={}, memberId={}, from={}, to={}",
                    guild.getId(), member.getId(),
                    event.getChannelLeft().getId(), event.getChannelJoined().getId());
        }
    }

    @Override
    public void onGuildVoiceSelfMute(@NotNull GuildVoiceSelfMuteEvent event) {
        String action = event.isSelfMuted() ? "VOICE_SELF_MUTE" : "VOICE_SELF_UNMUTE";
        String reason = event.isSelfMuted() ? "Mute micro (self)" : "Démute micro (self)";
        moderationLogger.logInGuild(event.getGuild(), action, event.getMember(), event.getMember(), reason, null);
    }

    @Override
    public void onGuildVoiceSelfDeafen(@NotNull GuildVoiceSelfDeafenEvent event) {
        String action = event.isSelfDeafened() ? "VOICE_SELF_DEAFEN" : "VOICE_SELF_UNDEAFEN";
        String reason = event.isSelfDeafened() ? "Mute casque (self)" : "Démute casque (self)";
        moderationLogger.logInGuild(event.getGuild(), action, event.getMember(), event.getMember(), reason, null);
    }

    @Override
    public void onGuildVoiceGuildMute(@NotNull GuildVoiceGuildMuteEvent event) {
        String action = event.isGuildMuted() ? "VOICE_GUILD_MUTE" : "VOICE_GUILD_UNMUTE";
        String reason = event.isGuildMuted() ? "Mute serveur" : "Démute serveur";
        moderationLogger.logInGuild(event.getGuild(), action, null, event.getMember(), reason,
                "> 🛡️ *Action serveur (modération/permissions)*");
    }

    @Override
    public void onGuildVoiceGuildDeafen(@NotNull GuildVoiceGuildDeafenEvent event) {
        String action = event.isGuildDeafened() ? "VOICE_GUILD_DEAFEN" : "VOICE_GUILD_UNDEAFEN";
        String reason = event.isGuildDeafened() ? "Mute casque serveur" : "Démute casque serveur";
        moderationLogger.logInGuild(event.getGuild(), action, null, event.getMember(), reason,
                "> 🛡️ *Action serveur (modération/permissions)*");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void tryLogModeratorVoiceDisconnect(GuildVoiceUpdateEvent event) {
        Guild guild   = event.getGuild();
        Member target = event.getMember();

        guild.retrieveAuditLogs().type(ActionType.MEMBER_VOICE_KICK).limit(5).queue(entries -> {
            AuditLogEntry matched = findMatchingDisconnect(entries, target.getId());
            if (matched == null) return;

            Member moderator = matched.getUser() != null
                    ? guild.getMemberById(matched.getUser().getId()) : null;

            moderationLogger.logInGuild(guild, "VOICE_MOD_DISCONNECT", moderator, target,
                    "Utilisateur déconnecté du vocal par modération",
                    "> **Target ID :** `" + target.getId() + "`");

            logger.info("Déconnexion vocale modérée: guildId={}, targetId={}, moderatorId={}",
                    guild.getId(), target.getId(), matched.getUserId());

        }, error -> logger.warn("Impossible de lire les audit logs pour détecter une déconnexion vocale modérée", error));
    }

    private AuditLogEntry findMatchingDisconnect(List<AuditLogEntry> entries, String targetId) {
        OffsetDateTime now = OffsetDateTime.now();
        for (AuditLogEntry entry : entries) {
            if (!targetId.equals(entry.getTargetId())) continue;
            long age = Math.abs(now.toEpochSecond() - entry.getTimeCreated().toEpochSecond());
            if (age <= AUDIT_MATCH_WINDOW_SECONDS) return entry;
        }
        return null;
    }
}