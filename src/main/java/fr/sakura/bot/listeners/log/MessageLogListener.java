package fr.sakura.bot.listeners.log;

import fr.sakura.bot.core.service.MessageCacheService;
import fr.sakura.bot.core.util.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Listener pour les logs de messages (Style Sakura).
 * GÃ¨re: cache, Ã©ditions, suppressions, ghost pings.
 */
public class MessageLogListener extends BaseLogListener {

    public MessageLogListener(String logChannelId, MessageCacheService messageCacheService) {
        super(logChannelId, messageCacheService);
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;
        messageCacheService.put(event.getMessage());
    }

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;

        MessageCacheService.CachedMessage cached = messageCacheService.get(event.getMessageId());
        String newContent = event.getMessage().getContentDisplay();

        if (cached == null || cached.content.equals(newContent)) return;

        sendLogToChannel(event.getGuild(), embed -> {
            embed.setColor(EmbedStyle.SAKURA_PINK);
            embed.setTitle("âœï¸  âœ¦  Message ModifiÃ©");
            
            StringBuilder desc = new StringBuilder();
            desc.append(EmbedStyle.SEPARATOR).append("\n");
            desc.append("âœï¸  **Ã‰DITION DE MESSAGE**\n");
            desc.append(EmbedStyle.SEPARATOR).append("\n\n");
            
            desc.append(EmbedStyle.detailLine("Auteur", "<@" + cached.authorId + "> (`" + cached.authorName + "`)")).append("\n");
            desc.append(EmbedStyle.detailLine("Salon", "<#" + cached.channelId + ">")).append("\n");
            desc.append(EmbedStyle.detailLine("Lien", "[Aller au message](" + event.getMessage().getJumpUrl() + ")")).append("\n");
            
            desc.append("\n").append(EmbedStyle.SEP_LIGHT).append("\n");
            desc.append("ðŸ“‹ **Avant**\n");
            desc.append("```\n").append(EmbedStyle.truncate(cached.content.isEmpty() ? "(vide)" : cached.content, 900)).append("\n```");
            
            desc.append(EmbedStyle.SEP_LIGHT).append("\n");
            desc.append("ðŸ“ **AprÃ¨s**\n");
            desc.append("```\n").append(EmbedStyle.truncate(newContent.isEmpty() ? "(vide)" : newContent, 900)).append("\n```");
            
            embed.setDescription(desc.toString());
            embed.setThumbnail(cached.authorAvatar);
            embed.setTimestamp(Instant.now());
            EmbedStyle.setFooter(embed, "Message ID: " + event.getMessageId());
        });

        // Mettre Ã  jour le cache
        cached.content = newContent;
    }

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        MessageCacheService.CachedMessage cached = messageCacheService.remove(event.getMessageId());
        if (cached == null) return;

        boolean isGhostPing = !cached.mentionedUserIds.isEmpty();
        
        findRecentAuditAction(event.getGuild(), ActionType.MESSAGE_DELETE, cached.authorId, AUDIT_TIMING_STANDARD, 3)
            .thenAccept(auditEntry -> {
                User deletedBy = null;
                boolean selfDeleted = true;

                if (auditEntry != null) {
                    String channelIdOpt = auditEntry.getOption(net.dv8tion.jda.api.audit.AuditLogOption.CHANNEL);
                    if (channelIdOpt == null || channelIdOpt.equals(cached.channelId)) {
                        deletedBy = auditEntry.getUser();
                        selfDeleted = deletedBy != null && deletedBy.getId().equals(cached.authorId);
                    }
                }

                final User finalDeletedBy = deletedBy;
                final boolean finalSelfDeleted = selfDeleted;
                final boolean isModeratorAction = deletedBy != null && !finalSelfDeleted;

                sendLogToChannel(event.getGuild(), embed -> {
                    embed.setColor(isGhostPing ? EmbedStyle.SAKURA_MIST : (isModeratorAction ? EmbedStyle.SAKURA_DEEP : EmbedStyle.SAKURA_PINK));
                    embed.setTitle(isGhostPing ? "ðŸ‘»  âœ¦  Ghost Ping DÃ©tectÃ©" : (isModeratorAction ? "ðŸ› ï¸  âœ¦  Message SupprimÃ© (Staff)" : "ðŸ—‘ï¸  âœ¦  Message SupprimÃ©"));
                    
                    StringBuilder desc = new StringBuilder();
                    desc.append(EmbedStyle.SEPARATOR).append("\n");
                    desc.append(isGhostPing ? "ðŸ‘» **GHOST PING DÃ‰TECTÃ‰**" : (isModeratorAction ? "ðŸ› ï¸ **SUPPRESSION STAFF**" : "ðŸ—‘ï¸ **MESSAGE SUPPRIMÃ‰**")).append("\n");
                    desc.append(EmbedStyle.SEPARATOR).append("\n\n");
                    
                    desc.append(EmbedStyle.detailLine("Auteur", "<@" + cached.authorId + "> (`" + cached.authorName + "`)")).append("\n");
                    desc.append(EmbedStyle.detailLine("Salon", "<#" + cached.channelId + ">")).append("\n");
                    desc.append(EmbedStyle.detailLine("Par", finalSelfDeleted ? "L'auteur lui-mÃªme" : (finalDeletedBy != null ? finalDeletedBy.getAsMention() : "Inconnu (Audit Log)"))).append("\n");
                    
                    if (isGhostPing) {
                        StringBuilder mentions = new StringBuilder();
                        for (String id : cached.mentionedUserIds) mentions.append("<@").append(id).append("> ");
                        desc.append(EmbedStyle.detailLine("Mentions", mentions.toString())).append("\n");
                    }

                    desc.append("\n").append(EmbedStyle.SEP_LIGHT).append("\n");
                    desc.append("ðŸ’¬ **Contenu SupprimÃ©**\n");
                    desc.append("```\n").append(EmbedStyle.truncate(cached.content.isEmpty() ? "(piÃ¨ce jointe uniquement)" : cached.content, 1800)).append("\n```");
                    
                    if (!cached.attachments.isEmpty()) {
                        desc.append("\nðŸ“Ž **PiÃ¨ces jointes :**\n");
                        for (MessageCacheService.CachedAttachment att : cached.attachments) {
                            desc.append(att.getEmoji()).append(" [").append(att.fileName).append("](").append(att.url).append(") (").append(att.getFormattedSize()).append(")\n");
                        }
                    }

                    embed.setDescription(desc.toString());
                    embed.setThumbnail(cached.authorAvatar);
                    embed.setTimestamp(Instant.now());
                    EmbedStyle.setFooter(embed, "Message ID: " + event.getMessageId());
                });
            });
    }
}
