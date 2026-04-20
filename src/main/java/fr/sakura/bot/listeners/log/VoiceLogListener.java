package fr.sakura.bot.listeners.log;

import fr.sakura.bot.utils.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.voice.*;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Listener pour les logs vocaux (Style Sakura).
 * Gère: connexions, déconnexions (audit log), déplacements, états micro/casque.
 */
public class VoiceLogListener extends BaseLogListener {

    public VoiceLogListener(String logChannelId) {
        super(logChannelId);
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        // Si le salon n'a pas changé, c'est une mise à jour d'état (mute, sourdine, etc.)
        // On ignore ici car c'est géré par les méthodes onGuildVoiceSelfMute, etc.
        if (event.getOldValue() == event.getNewValue()) return;

        Member member = event.getMember();
        
        // Connexion
        if (event.getChannelLeft() == null && event.getChannelJoined() != null) {
            sendLogToChannel(event.getGuild(), embed -> {
                embed.setColor(EmbedStyle.SAKURA_GREEN);
                embed.setTitle("🔊  ✦  Connexion Vocale");
                
                StringBuilder desc = new StringBuilder();
                desc.append(EmbedStyle.SEPARATOR).append("\n");
                desc.append("🔊 **CONNEXION VOCALE**\n");
                desc.append(EmbedStyle.SEPARATOR).append("\n\n");
                
                desc.append(EmbedStyle.detailLine("Utilisateur", member.getAsMention() + " (`" + member.getUser().getName() + "`)")).append("\n");
                desc.append(EmbedStyle.detailLine("Salon", event.getChannelJoined().getName())).append("\n");
                
                embed.setDescription(desc.toString());
                embed.setThumbnail(member.getEffectiveAvatarUrl());
                embed.setTimestamp(Instant.now());
                EmbedStyle.setFooter(embed, "User ID: " + member.getId());
            });
        }
        // Déconnexion
        else if (event.getChannelLeft() != null && event.getChannelJoined() == null) {
            findRecentAuditAction(event.getGuild(), ActionType.MEMBER_VOICE_KICK, member.getId(), AUDIT_TIMING_STANDARD, 3)
                .thenAccept(auditEntry -> {
                    User kickedBy = auditEntry != null ? auditEntry.getUser() : null;
                    boolean wasKicked = kickedBy != null;

                    sendLogToChannel(event.getGuild(), embed -> {
                        embed.setColor(wasKicked ? EmbedStyle.SAKURA_DEEP : EmbedStyle.SAKURA_GOLD);
                        embed.setTitle(wasKicked ? "🚫  ✦  Déconnexion Forcée" : "🔇  ✦  Déconnexion Vocale");
                        
                        StringBuilder desc = new StringBuilder();
                        desc.append(EmbedStyle.SEPARATOR).append("\n");
                        desc.append(wasKicked ? "🚫 **EXPULSION VOCALE**" : "🔇 **DÉCONNEXION VOCALE**").append("\n");
                        desc.append(EmbedStyle.SEPARATOR).append("\n\n");
                        
                        desc.append(EmbedStyle.detailLine("Utilisateur", member.getAsMention() + " (`" + member.getUser().getName() + "`)")).append("\n");
                        desc.append(EmbedStyle.detailLine("Depuis", event.getChannelLeft().getName())).append("\n");
                        
                        if (wasKicked) {
                            desc.append(EmbedStyle.detailLine("Par", kickedBy.getAsMention())).append("\n");
                        }
                        
                        embed.setDescription(desc.toString());
                        embed.setThumbnail(member.getEffectiveAvatarUrl());
                        embed.setTimestamp(Instant.now());
                        EmbedStyle.setFooter(embed, "User ID: " + member.getId());
                    });
                });
        }
        // Déplacement
        else if (event.getChannelLeft() != null && event.getChannelJoined() != null) {
            sendLogToChannel(event.getGuild(), embed -> {
                embed.setColor(EmbedStyle.SAKURA_MIST);
                embed.setTitle("🔁  ✦  Déplacement Vocal");
                
                StringBuilder desc = new StringBuilder();
                desc.append(EmbedStyle.SEPARATOR).append("\n");
                desc.append("🔁 **DÉPLACEMENT VOCAL**\n");
                desc.append(EmbedStyle.SEPARATOR).append("\n\n");
                
                desc.append(EmbedStyle.detailLine("Utilisateur", member.getAsMention() + " (`" + member.getUser().getName() + "`)")).append("\n");
                desc.append(EmbedStyle.detailLine("De", event.getChannelLeft().getName())).append("\n");
                desc.append(EmbedStyle.detailLine("Vers", event.getChannelJoined().getName())).append("\n");
                
                embed.setDescription(desc.toString());
                embed.setThumbnail(member.getEffectiveAvatarUrl());
                embed.setTimestamp(Instant.now());
                EmbedStyle.setFooter(embed, "User ID: " + member.getId());
            });
        }
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
