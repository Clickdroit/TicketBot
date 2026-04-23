package fr.sakura.bot.listeners.log;

import fr.sakura.bot.core.service.MessageCacheService;
import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.database.SettingsManager;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.guild.voice.*;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Listener pour les logs vocaux refait pour JDA 6.
 * Utilise les événements spécifiques pour chaque changement d'état.
 */
public class VoiceLogListener extends BaseLogListener {

    public VoiceLogListener(SettingsManager settingsManager, MessageCacheService messageCacheService) {
        super(settingsManager, messageCacheService);
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        Member member = event.getMember();
        AudioChannel joined = event.getChannelJoined();
        AudioChannel left = event.getChannelLeft();

        if (joined != left) {
            if (left == null) {
                handleJoin(member, joined);
            } else if (joined == null) {
                handleLeave(event, member, left);
            } else {
                handleMove(event, member, left, joined);
            }
        }
    }

    @Override
    public void onGuildVoiceSelfMute(@NotNull GuildVoiceSelfMuteEvent event) {
        logVoiceStatus(event.getMember(), event.isSelfMuted() ? "Micro coupé" : "Micro activé", "🎙️");
    }

    @Override
    public void onGuildVoiceSelfDeafen(@NotNull GuildVoiceSelfDeafenEvent event) {
        logVoiceStatus(event.getMember(), event.isSelfDeafened() ? "Casque coupé" : "Casque activé", "🎧");
    }

    @Override
    public void onGuildVoiceGuildMute(@NotNull GuildVoiceGuildMuteEvent event) {
        logVoiceStatus(event.getMember(), event.isGuildMuted() ? "Réduit au silence (Serveur)" : "Parole rendue (Serveur)", "🙊");
    }

    @Override
    public void onGuildVoiceGuildDeafen(@NotNull GuildVoiceGuildDeafenEvent event) {
        logVoiceStatus(event.getMember(), event.isGuildDeafened() ? "Sourdine forcée (Serveur)" : "Sourdine levée (Serveur)", "🔇");
    }

    @Override
    public void onGuildVoiceVideo(@NotNull GuildVoiceVideoEvent event) {
        logVoiceStatus(event.getMember(), event.isSendingVideo() ? "Caméra activée" : "Caméra coupée", "📷");
    }

    @Override
    public void onGuildVoiceStream(@NotNull GuildVoiceStreamEvent event) {
        logVoiceStatus(event.getMember(), event.isStream() ? "Début de stream" : "Fin de stream", "📺");
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

    private void logVoiceStatus(Member member, String statusMessage, String emoji) {
        sendLogToChannel(member.getGuild(), embed -> {
            embed.setColor(EmbedStyle.SAKURA_PINK);
            embed.setTitle(emoji + "  ✦  État Vocal");

            StringBuilder desc = new StringBuilder();
            desc.append(EmbedStyle.SEPARATOR).append("\n");
            desc.append(emoji).append(" **MISE À JOUR ÉTAT**\n");
            desc.append(EmbedStyle.SEPARATOR).append("\n\n");
            desc.append(EmbedStyle.detailLine("Utilisateur", member.getAsMention() + " (`" + member.getUser().getName() + "`)")).append("\n");
            desc.append(EmbedStyle.detailLine("Action", statusMessage)).append("\n");

            embed.setDescription(desc.toString());
            embed.setTimestamp(Instant.now());
            EmbedStyle.setFooter(embed, "User ID: " + member.getId());
        });
    }
}
