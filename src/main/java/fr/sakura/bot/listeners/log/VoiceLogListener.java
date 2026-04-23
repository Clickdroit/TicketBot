package fr.sakura.bot.listeners.log;

import fr.sakura.bot.core.service.MessageCacheService;
import fr.sakura.bot.core.util.EmbedStyle;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.*;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

public class VoiceLogListener extends BaseLogListener {

    public VoiceLogListener(String logChannelId, MessageCacheService messageCacheService) {
        super(logChannelId, messageCacheService);
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        Member member = event.getMember();
        AudioChannel channelJoined = event.getChannelJoined();
        AudioChannel channelLeft = event.getChannelLeft();

        if (channelJoined == null && channelLeft == null) return;
        if (channelJoined != null && channelLeft != null
                && channelJoined.getId().equals(channelLeft.getId())) return;

        if (channelJoined != null && channelLeft == null) {
            handleJoin(event.getMember(), channelJoined);
        } else if (channelLeft != null && channelJoined == null) {
            handleLeave(event, member, channelLeft);
        } else if (channelLeft != null && channelJoined != null) {
            handleMove(event, member, channelLeft, channelJoined);
        }
    }

    private void handleJoin(Member member, AudioChannel channel) {
        sendLogToChannel(member.getGuild(), embed -> {
            embed.setColor(EmbedStyle.SAKURA_GREEN);
            embed.setTitle("🔊  ✦  Connexion Vocale");

            StringBuilder desc = new StringBuilder();
            desc.append(EmbedStyle.SEPARATOR).append("\n");
            desc.append("🔊 **CONNEXION VOCALE**\n");
            desc.append(EmbedStyle.SEPARATOR).append("\n\n");
            desc.append(EmbedStyle.detailLine("Utilisateur", member.getAsMention() + " (`" + member.getUser().getName() + "`)")).append("\n");
            desc.append(EmbedStyle.detailLine("Salon", channel.getName())).append("\n");

            embed.setDescription(desc.toString());
            embed.setThumbnail(member.getEffectiveAvatarUrl());
            embed.setTimestamp(Instant.now());
            EmbedStyle.setFooter(embed, "User ID: " + member.getId());
        });
    }

    private void handleLeave(GuildVoiceUpdateEvent event, Member member, AudioChannel fromChannel) {
        findRecentAuditAction(event.getGuild(), ActionType.MEMBER_VOICE_KICK, member.getId(), AUDIT_TIMING_STANDARD, 3)
                .thenAccept(auditEntry -> {
                    User kickedBy = auditEntry != null ? auditEntry.getUser() : null;
                    boolean wasKicked = kickedBy != null && !kickedBy.getId().equals(member.getId());

                    sendLogToChannel(event.getGuild(), embed -> {
                        embed.setColor(wasKicked ? EmbedStyle.SAKURA_DEEP : EmbedStyle.SAKURA_GOLD);
                        embed.setTitle(wasKicked ? "🚫  ✦  Déconnexion Forcée" : "🔉  ✦  Déconnexion Vocale");

                        StringBuilder desc = new StringBuilder();
                        desc.append(EmbedStyle.SEPARATOR).append("\n");
                        desc.append(wasKicked ? "🚫 **EXPULSION VOCALE**" : "🔉 **DÉCONNEXION VOCALE**").append("\n");
                        desc.append(EmbedStyle.SEPARATOR).append("\n\n");
                        desc.append(EmbedStyle.detailLine("Utilisateur", member.getAsMention() + " (`" + member.getUser().getName() + "`)")).append("\n");
                        desc.append(EmbedStyle.detailLine("Depuis", fromChannel.getName())).append("\n");

                        if (wasKicked) {
                            desc.append(EmbedStyle.detailLine("Par", kickedBy.getAsMention())).append("\n");
                            EmbedStyle.setFooter(embed, "User ID: " + member.getId() + " | Mod ID: " + kickedBy.getId(), kickedBy.getEffectiveAvatarUrl());
                        } else {
                            EmbedStyle.setFooter(embed, "User ID: " + member.getId());
                        }

                        embed.setDescription(desc.toString());
                        embed.setThumbnail(member.getEffectiveAvatarUrl());
                        embed.setTimestamp(Instant.now());
                    });
                });
    }

    private void handleMove(GuildVoiceUpdateEvent event, Member member, AudioChannel fromChannel, AudioChannel toChannel) {
        findRecentAuditAction(event.getGuild(), ActionType.MEMBER_VOICE_MOVE, member.getId(), AUDIT_TIMING_STANDARD, 3)
                .thenAccept(auditEntry -> {
                    User movedBy = auditEntry != null ? auditEntry.getUser() : null;
                    boolean wasMoved = movedBy != null && !movedBy.getId().equals(member.getId());

                    sendLogToChannel(event.getGuild(), embed -> {
                        embed.setColor(wasMoved ? EmbedStyle.SAKURA_PINK : EmbedStyle.SAKURA_MIST);
                        embed.setTitle(wasMoved ? "⚡  ✦  Déplacement Forcé" : "🔄  ✦  Déplacement Vocal");

                        StringBuilder desc = new StringBuilder();
                        desc.append(EmbedStyle.SEPARATOR).append("\n");
                        desc.append(wasMoved ? "⚡ **DÉPLACEMENT FORCÉ**" : "🔄 **DÉPLACEMENT VOLONTAIRE**").append("\n");
                        desc.append(EmbedStyle.SEPARATOR).append("\n\n");
                        desc.append(EmbedStyle.detailLine("Utilisateur", member.getAsMention() + " (`" + member.getUser().getName() + "`)")).append("\n");

                        if (wasMoved) {
                            desc.append(EmbedStyle.detailLine("Par", movedBy.getAsMention())).append("\n");
                        }

                        desc.append("\n**🎙️ » Trajet Vocal**\n");
                        if (wasMoved) {
                            desc.append("```diff\n- ").append(fromChannel.getName()).append("\n+ ").append(toChannel.getName()).append("\n```");
                        } else {
                            desc.append("```yaml\n").append(fromChannel.getName()).append(" ➜ ").append(toChannel.getName()).append("\n```");
                        }

                        embed.setDescription(desc.toString());
                        embed.setThumbnail(member.getEffectiveAvatarUrl());
                        embed.setTimestamp(Instant.now());

                        if (wasMoved) {
                            EmbedStyle.setFooter(embed, "User ID: " + member.getId() + " | Mod ID: " + movedBy.getId(), movedBy.getEffectiveAvatarUrl());
                        } else {
                            EmbedStyle.setFooter(embed, "User ID: " + member.getId());
                        }
                    });
                });
    }

    @Override
    public void onGuildVoiceSelfMute(@NotNull GuildVoiceSelfMuteEvent event) {
        logVoiceState(event.getMember(), event.isSelfMuted() ? "Micro coupé" : "Micro activé", "🎙️");
    }

    @Override
    public void onGuildVoiceSelfDeafen(@NotNull GuildVoiceSelfDeafenEvent event) {
        logVoiceState(event.getMember(), event.isSelfDeafened() ? "Casque coupé" : "Casque activé", "🎧");
    }

    private void logVoiceState(Member member, String action, String emoji) {
        sendLogToChannel(member.getGuild(), embed -> {
            embed.setColor(EmbedStyle.SAKURA_PINK);
            embed.setTitle(emoji + "  ✦  État Vocal");

            StringBuilder desc = new StringBuilder();
            desc.append(EmbedStyle.detailLine("Utilisateur", member.getAsMention())).append("\n");
            desc.append(EmbedStyle.detailLine("Action", action));

            embed.setDescription(desc.toString());
            embed.setTimestamp(Instant.now());
            EmbedStyle.setFooter(embed, "User ID: " + member.getId());
        });
    }
}
