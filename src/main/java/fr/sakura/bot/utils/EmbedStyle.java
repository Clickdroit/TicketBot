package fr.sakura.bot.utils;

import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.time.format.DateTimeFormatter;

/**
 * Charte visuelle centralisée des embeds.
 */
public final class EmbedStyle {

    private static final Color SAKURA_PINK = new Color(255, 183, 197);
    private static final DateTimeFormatter INFO_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");
    private static final DateTimeFormatter MODERATION_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm:ss");
    private static final String APP_MARK = "🌸 Sakura";
    private static final String UNKNOWN = "Inconnu";
    private static final String ELLIPSIS = "...";

    private EmbedStyle() {
    }

    public static EmbedBuilder newInfoEmbed(String emoji, String title) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(SAKURA_PINK);
        embed.setTitle(truncate(emoji + " " + title, 256));
        return embed;
    }

    public static String formatInfoDate(TemporalAccessor date) {
        return INFO_DATE_FORMATTER.format(date);
    }

    public static String moderationTimestampNow() {
        return MODERATION_DATE_FORMATTER.format(OffsetDateTime.now());
    }

    public static void setInfoFooterWithId(EmbedBuilder embed, String id) {
        embed.setFooter(APP_MARK + " • ID : " + fallback(id, UNKNOWN));
    }

    public static void setFooter(EmbedBuilder embed, String footerText) {
        embed.setFooter(APP_MARK + " • " + fallback(footerText, UNKNOWN));
    }

    public static String fallback(String value, String fallbackValue) {
        return value == null || value.isBlank() ? fallbackValue : value;
    }

    public static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        if (max <= ELLIPSIS.length()) {
            return ELLIPSIS.substring(0, max);
        }
        return value.substring(0, max - ELLIPSIS.length()) + ELLIPSIS;
    }
}
