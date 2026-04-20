package fr.sakura.bot.utils;

import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.Color;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.time.format.DateTimeFormatter;

/**
 * Charte visuelle centralisée des embeds — thème Sakura 🌸
 *
 * Palette principale :
 *   SAKURA_PINK   — rose doux, embeds informatifs
 *   SAKURA_DEEP   — rose profond, actions importantes
 *   SAKURA_GOLD   — or chaud, récompenses / niveau
 *   SAKURA_MIST   — lavande, notifications douces
 *
 * Convention de titres : « 🌸 ✦ Titre »
 * Séparateur de section : ══════════════════
 */
public final class EmbedStyle {

    // ── Palette Sakura ────────────────────────────────────────────────────────────
    /** Rose sakura pastel – embeds info/neutres */
    public static final Color SAKURA_PINK  = new Color(255, 168, 204);
    /** Rose sakura profond – actions, tickets, panneaux */
    public static final Color SAKURA_DEEP  = new Color(219,  98, 152);
    /** Or chaud – XP, niveaux, récompenses */
    public static final Color SAKURA_GOLD  = new Color(255, 210, 120);
    /** Lavande douce – notifications, messages discrets */
    public static final Color SAKURA_MIST  = new Color(198, 168, 230);
    /** Vert cerisier – succès, connexions */
    public static final Color SAKURA_GREEN = new Color(130, 210, 150);

    // ── Typographie décorative ───────────────────────────────────────────────────
    /** Séparateur de section dans les descriptions */
    public static final String SEPARATOR   = "══════════════════";
    /** Séparateur léger */
    public static final String SEP_LIGHT   = "──────────────────";
    /** Puce sakura */
    public static final String BULLET      = "🌸 ";
    /** Flèche de liste */
    public static final String ARROW       = "  ╰─ ";

    // ── Formatters ───────────────────────────────────────────────────────────────
    private static final DateTimeFormatter INFO_DATE_FORMATTER       = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");
    private static final DateTimeFormatter MODERATION_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm:ss");

    // ── Marques ──────────────────────────────────────────────────────────────────
    private static final String APP_MARK = "🌸 Sakura";
    private static final String UNKNOWN  = "Inconnu";
    private static final String ELLIPSIS = "...";

    private EmbedStyle() {}

    // ── Constructeurs d'embed ─────────────────────────────────────────────────────

    /**
     * Embed informatif standard (rose sakura).
     * Titre formaté : « emoji  ✦  titre »
     */
    public static EmbedBuilder newInfoEmbed(String emoji, String title) {
        return baseEmbed(SAKURA_PINK, emoji, title);
    }

    /**
     * Embed d'action / important (rose profond).
     */
    public static EmbedBuilder newActionEmbed(String emoji, String title) {
        return baseEmbed(SAKURA_DEEP, emoji, title);
    }

    /**
     * Embed de récompense / XP (or chaud).
     */
    public static EmbedBuilder newRewardEmbed(String emoji, String title) {
        return baseEmbed(SAKURA_GOLD, emoji, title);
    }

    /**
     * Embed de notification douce (lavande).
     */
    public static EmbedBuilder newMistEmbed(String emoji, String title) {
        return baseEmbed(SAKURA_MIST, emoji, title);
    }

    /**
     * Embed avec couleur personnalisée.
     */
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

    // ── Helpers de description ────────────────────────────────────────────────────

    /**
     * Construit un en-tête de section décoratif pour une description.
     * Exemple : « ══════════════════\n✨ TITRE\n══════════════════ »
     */
    public static String sectionHeader(String emoji, String label) {
        return SEPARATOR + "\n" + emoji + " **" + label.toUpperCase() + "**\n" + SEPARATOR;
    }

    /**
     * Ligne de détail indentée (pour les champs de description).
     * Exemple : « ╰─ **Clé :** valeur »
     */
    public static String detailLine(String key, String value) {
        return ARROW + "**" + key + " :** " + value;
    }

    /**
     * Puce sakura simple.
     * Exemple : « 🌸 texte »
     */
    public static String bullet(String text) {
        return BULLET + text;
    }

    // ── Formatage de dates ────────────────────────────────────────────────────────

    public static String formatInfoDate(TemporalAccessor date) {
        return INFO_DATE_FORMATTER.format(date);
    }

    public static String moderationTimestampNow() {
        return MODERATION_DATE_FORMATTER.format(OffsetDateTime.now());
    }

    // ── Footers ───────────────────────────────────────────────────────────────────

    public static void setInfoFooterWithId(EmbedBuilder embed, String id) {
        embed.setFooter(APP_MARK + "  ✦  ID : " + fallback(id, UNKNOWN));
    }

    public static void setFooter(EmbedBuilder embed, String footerText) {
        embed.setFooter(APP_MARK + "  ✦  " + fallback(footerText, UNKNOWN));
    }

    public static void setFooter(EmbedBuilder embed, String footerText, String iconUrl) {
        embed.setFooter(APP_MARK + "  ✦  " + fallback(footerText, UNKNOWN), iconUrl);
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────────

    public static String fallback(String value, String fallbackValue) {
        return value == null || value.isBlank() ? fallbackValue : value;
    }

    public static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        if (max <= ELLIPSIS.length()) return ELLIPSIS.substring(0, max);
        return value.substring(0, max - ELLIPSIS.length()) + ELLIPSIS;
    }
}