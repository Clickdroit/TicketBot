package fr.sakura.bot.core.util;

import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.Color;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.time.format.DateTimeFormatter;

/**
 * Charte visuelle centralisée des embeds — thème Sakura 🌸
 */
public final class EmbedStyle {

    // ── Palette Sakura ─────────────────────────────────────────────────────────────
    public static final Color SAKURA_PINK  = new Color(255, 168, 204);
    public static final Color SAKURA_DEEP  = new Color(219,  98, 152);
    public static final Color SAKURA_GOLD  = new Color(255, 210, 120);
    public static final Color SAKURA_MIST  = new Color(198, 168, 230);
    public static final Color SAKURA_GREEN = new Color(130, 210, 150);

    // ── Typographie décorative ─────────────────────────────────────────────────────
    public static final String SEPARATOR   = "══════════════════";
    public static final String SEP_LIGHT   = "──────────────────";
    public static final String BULLET      = "🌸 ";
    public static final String ARROW       = "  ╰─ ";

    // ── Formatters ─────────────────────────────────────────────────────────────────
    private static final DateTimeFormatter INFO_DATE_FORMATTER       = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");
    private static final DateTimeFormatter MODERATION_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm:ss");

    // ── Marques ────────────────────────────────────────────────────────────────────
    private static final String APP_MARK = "🌸 Sakura";
    private static final String UNKNOWN  = "Inconnu";
    private static final String ELLIPSIS = "...";

    private EmbedStyle() {}

    public static EmbedBuilder newInfoEmbed(String emoji, String title) {
        return baseEmbed(SAKURA_PINK, emoji, title);
    }

    public static EmbedBuilder newActionEmbed(String emoji, String title) {
        return baseEmbed(SAKURA_DEEP, emoji, title);
    }

    public static EmbedBuilder newRewardEmbed(String emoji, String title) {
        return baseEmbed(SAKURA_GOLD, emoji, title);
    }

    public static EmbedBuilder newMistEmbed(String emoji, String title) {
        return baseEmbed(SAKURA_MIST, emoji, title);
    }

    public static EmbedBuilder newEmbed(Color color, String emoji, String title) {
        return baseEmbed(color, emoji, title);
    }

    private static EmbedBuilder baseEmbed(Color color, String emoji, String title) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(color);
        embed.setTitle(truncate(emoji + "  ✦  " + title, 256));
        embed.setTimestamp(Instant.now());
        return embed;
    }

    public static String sectionHeader(String emoji, String label) {
        return SEPARATOR + "\n" + emoji + " **" + label.toUpperCase() + "**\n" + SEPARATOR;
    }

    public static String detailLine(String key, String value) {
        return ARROW + "**" + key + " :** " + value;
    }

    public static String bullet(String text) {
        return BULLET + text;
    }

    public static String formatInfoDate(TemporalAccessor date) {
        return INFO_DATE_FORMATTER.format(date);
    }

    public static String moderationTimestampNow() {
        return MODERATION_DATE_FORMATTER.format(OffsetDateTime.now());
    }

    public static void setInfoFooterWithId(EmbedBuilder embed, String id) {
        embed.setFooter(APP_MARK + "  ✦  ID : " + fallback(id, UNKNOWN));
    }

    public static void setFooter(EmbedBuilder embed, String footerText) {
        embed.setFooter(APP_MARK + "  ✦  " + fallback(footerText, UNKNOWN));
    }

    public static void setFooter(EmbedBuilder embed, String footerText, String iconUrl) {
        embed.setFooter(APP_MARK + "  ✦  " + fallback(footerText, UNKNOWN), iconUrl);
    }

    public static String fallback(String value, String fallbackValue) {
        return value == null || value.isBlank() ? fallbackValue : value;
    }

    public static String truncate(String value, int max) {
        if (value == null) return "";
        if (max <= 0) return "";
        if (value.length() <= max) return value;
        if (max <= ELLIPSIS.length()) return value.substring(0, max);
        return value.substring(0, max - ELLIPSIS.length()) + ELLIPSIS;
    }
}
