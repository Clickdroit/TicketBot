package fr.sakura.bot.listeners.log;

import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.database.SettingsManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.Map;

/**
 * Listener et service léger pour les logs d'activité de tickets de TicketBot.
 */
public class TicketLogListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(TicketLogListener.class);

    public record ActionStyle(Color color, String emoji, String label) {}

    public static final Map<String, ActionStyle> ACTION_STYLES = Map.of(
            "TICKET_CREATE", new ActionStyle(EmbedStyle.SAKURA_PINK, "🎫", "Ticket ouvert"),
            "TICKET_CLAIM",  new ActionStyle(EmbedStyle.SAKURA_GREEN, "✅", "Ticket pris en charge"),
            "TICKET_CLOSE",  new ActionStyle(EmbedStyle.SAKURA_DEEP, "🔒", "Ticket fermé")
    );

    private final SettingsManager settingsManager;

    public TicketLogListener(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    /**
     * Envoie un embed de log dans le salon configuré
     */
    private void sendLogToChannel(Guild guild, java.util.function.Consumer<EmbedBuilder> embedConsumer) {
        if (settingsManager == null) return;

        String guildId = guild.getId();
        settingsManager.getLogChannelId(guildId).ifPresent(channelId -> {
            TextChannel logChannel = guild.getTextChannelById(channelId);
            if (logChannel != null) {
                EmbedBuilder embed = new EmbedBuilder();
                embedConsumer.accept(embed);
                logChannel.sendMessageEmbeds(embed.build()).queue(
                        success -> {},
                        error -> logger.error("Erreur lors de l'envoi du log de ticket (guildId={}): {}", guildId, error.getMessage())
                );
            }
        });
    }

    /**
     * Log une action de ticket avec une cible Member.
     */
    public void logAction(@NotNull Guild guild, @NotNull String actionKey, @Nullable Member moderator, @NotNull Member target, @Nullable String reason, @Nullable String extra) {
        logAction(guild, actionKey, moderator, target.getUser(), reason, extra);
    }

    /**
     * Log une action de ticket avec une cible User.
     */
    public void logAction(@NotNull Guild guild, @NotNull String actionKey, @Nullable Member moderator, @NotNull User target, @Nullable String reason, @Nullable String extra) {
        ActionStyle style = ACTION_STYLES.get(actionKey.toUpperCase());
        if (style == null) {
            logger.warn("TicketLogListener: clé d'action inconnue '{}'", actionKey);
            return;
        }

        sendLogToChannel(guild, embed -> {
            embed.setColor(style.color());
            embed.setTitle(style.emoji() + "  ✦  " + style.label());
            
            StringBuilder desc = new StringBuilder();
            desc.append(EmbedStyle.SEPARATOR).append("\n");
            desc.append(style.emoji()).append(" **").append(style.label().toUpperCase()).append("**\n");
            desc.append(EmbedStyle.SEPARATOR).append("\n\n");
            
            if (moderator != null) {
                desc.append(EmbedStyle.detailLine("Opérateur", moderator.getAsMention() + " (`" + moderator.getUser().getName() + "`)")).append("\n");
            } else {
                desc.append(EmbedStyle.detailLine("Opérateur", "🤖 *Système Automatique*")).append("\n");
            }
            
            desc.append(EmbedStyle.detailLine("Cible/Client", target.getAsMention() + " (`" + target.getName() + "`)")).append("\n");
            
            if (reason != null && !reason.isBlank()) {
                desc.append("\n").append(EmbedStyle.SEP_LIGHT).append("\n");
                desc.append(EmbedStyle.detailLine("Raison", reason)).append("\n");
            }
            
            if (extra != null && !extra.isBlank()) {
                desc.append("\n").append(extra);
            }

            embed.setDescription(desc.toString());
            embed.setThumbnail(target.getEffectiveAvatarUrl());
            embed.setTimestamp(Instant.now());
            
            String footerText = "Client : " + target.getId();
            if (moderator != null) footerText += "  ✦  Op : " + moderator.getId();
            EmbedStyle.setFooter(embed, footerText);
        });
    }
}
