package fr.sakura.bot.listeners;

import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.utils.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

public class WelcomeListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(WelcomeListener.class);

    /**
     * Regex : accepte les URLs HTTPS pointant vers une image statique ou animée.
     * Couvre les CDN Discord, Tenor, Giphy, imgur, et les fichiers .gif/.png/.jpg/.webp.
     */
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
            "(?i)^https://[\\w\\-.]+(:[0-9]+)?(/[\\w\\-./_%~:@!$&'()*+,;=?#]*)?$"
    );

    /**
     * GIF de bienvenue par défaut utilisé si WELCOME_IMAGE_URL est absent/invalide.
     * Doit rester une URL HTTPS valide et permanente.
     */
    private static final String DEFAULT_WELCOME_GIF =
            "https://media2.giphy.com/media/v1.Y2lkPTc5MGI3NjExZjhwMjNzNXUzc3l6bHMyOTA5cmw1eXgyZ3l4Y3IxeHRhZzB1YW0yaCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/AdtvZ8gu9gZ32/giphy.gif";

    private final SettingsManager settingsManager;
    private final String defaultWelcomeChannelId;
    private final String defaultWelcomeImageUrl;

    public WelcomeListener(SettingsManager settingsManager, String welcomeChannelId, String welcomeImageUrl) {
        this.settingsManager = settingsManager;
        this.defaultWelcomeChannelId = welcomeChannelId;
        this.defaultWelcomeImageUrl = welcomeImageUrl;
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        MDC.put("guildId", event.getGuild().getId());
        MDC.put("userId", event.getUser().getId());

        try {
            String guildId = event.getGuild().getId();
            String welcomeChannelId = firstNonBlank(settingsManager.getWelcomeChannelId(guildId), defaultWelcomeChannelId);
            if (welcomeChannelId == null || welcomeChannelId.isBlank()) {
                logger.debug("Message de bienvenue ignoré : WELCOME_CHANNEL_ID non configuré");
                return;
            }

            TextChannel channel = event.getGuild().getTextChannelById(welcomeChannelId);
            if (channel == null) {
                logger.warn("Salon de bienvenue introuvable : channelId={}", welcomeChannelId);
                return;
            }

            var member = event.getMember();
            var user   = event.getUser();
            var guild  = event.getGuild();

            int memberNumber = guild.getMemberCount();

            // ── Résolution de l'image ────────────────────────────────────────────────
            // Discord n'anime les GIF que via setImage() (grande bannière en bas).
            // setThumbnail() ne supporte pas l'animation — on l'utilise pour l'avatar.
            String configuredImage = firstNonBlank(settingsManager.getWelcomeImageUrl(guildId), defaultWelcomeImageUrl);
            String bannerUrl = resolveImageUrl(configuredImage);

            // ── Construction de l'embed ──────────────────────────────────────────────
            EmbedBuilder embed = EmbedStyle.newInfoEmbed("🌸", "Bienvenue sur " + guild.getName() + " !");

            // Description principale — mise en avant du membre
            embed.setDescription(
                    "✨ " + member.getAsMention() + " vient de rejoindre le serveur !\n\n" +
                            "Nous sommes ravis de t'accueillir parmi nous. N'hésite pas à te présenter et à lire les règles du serveur. 🎉"
            );

            // Avatar en thumbnail (visible même si le GIF prend toute la largeur)
            String avatarUrl = user.getEffectiveAvatarUrl();
            if (avatarUrl != null) {
                embed.setThumbnail(avatarUrl + "?size=256");
            }

            // GIF en bannière principale
            if (bannerUrl != null) {
                embed.setImage(bannerUrl);
            }

            // Footer avec icône du serveur
            EmbedStyle.setFooter(
                    embed,
                    "Membre n°" + memberNumber + " • Bienvenu(e) !",
                    guild.getIconUrl()
            );

            channel.sendMessageEmbeds(embed.build()).queue(
                    success -> logger.info("Message de bienvenue envoyé : userId={}, guildId={}, channelId={}",
                            user.getId(), guild.getId(), channel.getId()),
                    error -> logger.error("Échec envoi message de bienvenue : userId={}", user.getId(), error)
            );
        } finally {
            MDC.remove("guildId");
            MDC.remove("userId");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /**
     * Résout l'URL d'image à utiliser :
     * 1. Tente l'URL configurée dans .env (après nettoyage).
     * 2. Si invalide ou absente, retourne le GIF par défaut.
     * 3. Si le GIF par défaut est lui-même invalide (réseau), retourne null
     *    (embed sans image plutôt qu'une erreur 400 chez Discord).
     */
    private String resolveImageUrl(String rawUrl) {
        String cleaned = sanitize(rawUrl);
        if (isValidHttpsUrl(cleaned)) {
            return cleaned;
        }

        if (cleaned != null) {
            logger.warn("WELCOME_IMAGE_URL invalide ou non-HTTPS, utilisation du GIF par défaut : \"{}\"", cleaned);
        }

        String fallback = sanitize(DEFAULT_WELCOME_GIF);
        if (isValidHttpsUrl(fallback)) {
            return fallback;
        }

        logger.error("Aucune URL d'image de bienvenue exploitable (configurée et défaut toutes invalides)");
        return null;
    }

    /**
     * Nettoie une URL brute : supprime les espaces, guillemets et balises angle.
     * Retourne null si la chaîne est vide après nettoyage.
     */
    private String sanitize(String raw) {
        if (raw == null) return null;

        String s = raw.strip();
        if (s.isEmpty()) return null;

        // Supprime les guillemets simples/doubles et les balises angle Discord <URL>
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1).strip();
        }
        if (s.startsWith("<") && s.endsWith(">")) {
            s = s.substring(1, s.length() - 1).strip();
        }

        return s.isEmpty() ? null : s;
    }

    /**
     * Vérifie qu'une URL est une URL HTTPS syntaxiquement valide avec un hôte présent.
     * Ne fait aucune requête réseau.
     */
    public static boolean isValidHttpsUrl(String url) {
        if (url == null) return false;
        try {
            URI uri = new URI(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && IMAGE_URL_PATTERN.matcher(url).matches();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }
}
