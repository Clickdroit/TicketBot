# Explore embed aesthetics in codebase

Perfect! Now I have all the information needed. Let me compile a comprehensive report.

## COMPREHENSIVE DISCORD EMBED ANALYSIS - SAKURA BOT

### SUMMARY OF EMBED USAGE
The Sakura Bot repository contains **5 files** that construct Discord embeds using `EmbedBuilder`:
1. **ModerationLogger.java** - Utility for logging moderation actions
2. **WelcomeListener.java** - Welcome message on member join
3. **ServerInfoCommand.java** - Server information display
4. **UserInfoCommand.java** - User information display
5. **AvatarCommand.java** - Avatar display

---

## 1. ModerationLogger.java (FULL CONTENT)
**File Path:** `/e/dev/sakura-bot/src/main/java/fr/sakura/bot/utils/ModerationLogger.java`

```java
package fr.sakura.bot.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Utilitaire pour envoyer des logs de modération dans un salon dédié.
 */
public class ModerationLogger {

    private static final Logger logger = LoggerFactory.getLogger(ModerationLogger.class);

    private final String logChannelId;

    public ModerationLogger(String logChannelId) {
        this.logChannelId = logChannelId;
        logger.info("ModerationLogger initialise (enabled={}, channelId={})", isEnabled(), logChannelId);
    }

    public String getLogChannelId() {
        return logChannelId;
    }

    public boolean isEnabled() {
        return logChannelId != null && !logChannelId.isEmpty();
    }

    /**
     * Resolve le salon de logs configure dans la guilde.
     */
    public TextChannel resolveLogChannel(Guild guild) {
        if (!isEnabled()) {
            logger.debug("Logs moderation desactives: LOG_CHANNEL_ID non configure");
            return null;
        }

        if (guild == null) {
            logger.warn("Impossible de resoudre le salon de logs: guild null");
            return null;
        }

        TextChannel channel = guild.getTextChannelById(logChannelId);
        if (channel == null) {
            logger.warn("Salon de logs moderation introuvable: guildId={}, channelId={}", guild.getId(), logChannelId);
        }
        return channel;
    }

    /**
     * Ecrit un log de moderation en resolvant automatiquement le salon configure.
     */
    public void logInGuild(Guild guild, String action, Member moderator, Member target, String reason, String extra) {
        TextChannel channel = resolveLogChannel(guild);
        log(channel, action, moderator, target, reason, extra);
    }

    /**
     * Envoie un log de modération dans le salon configuré.
     *
     */
    private record ActionStyle(Color color, String emoji) {}

    private static final Map<String, ActionStyle> ACTION_STYLES = Map.ofEntries(
            Map.entry("KICK",                 new ActionStyle(new Color(255, 165, 0),   "👢")),
            Map.entry("BAN",                  new ActionStyle(new Color(220, 20, 60),   "🔨")),
            Map.entry("CLEAR",                new ActionStyle(new Color(30, 144, 255),  "🧹")),
            Map.entry("TIMEOUT",              new ActionStyle(new Color(148, 0, 211),   "⏳")),
            Map.entry("UNBAN",                new ActionStyle(new Color(60, 179, 113),  "🔓")),
            Map.entry("WARN",                 new ActionStyle(new Color(255, 215, 0),   "⚠️")),
            Map.entry("CLEARWARN",            new ActionStyle(new Color(70, 130, 180),  "🧾")),
            Map.entry("MESSAGE_EDIT",         new ActionStyle(new Color(123, 104, 238), "✏️")),
            Map.entry("MESSAGE_DELETE",       new ActionStyle(new Color(255, 99, 71),   "🗑️")),
            Map.entry("VOICE_CONNECT",        new ActionStyle(new Color(46, 204, 113),  "🔊")),
            Map.entry("VOICE_DISCONNECT",     new ActionStyle(new Color(241, 196, 15),  "🔇")),
            Map.entry("VOICE_MOD_DISCONNECT", new ActionStyle(new Color(231, 76, 60),   "🚫")),
            Map.entry("VOICE_MOVE",           new ActionStyle(new Color(52, 152, 219),  "🔁")),
            Map.entry("VOICE_SELF_MUTE",      new ActionStyle(new Color(155, 89, 182),  "🎧")),
            Map.entry("VOICE_SELF_UNMUTE",    new ActionStyle(new Color(155, 89, 182),  "🎧")),
            Map.entry("VOICE_SELF_DEAFEN",    new ActionStyle(new Color(155, 89, 182),  "🎧")),
            Map.entry("VOICE_SELF_UNDEAFEN",  new ActionStyle(new Color(155, 89, 182),  "🎧")),
            Map.entry("VOICE_GUILD_MUTE",     new ActionStyle(new Color(230, 126, 34),  "🛡️")),
            Map.entry("VOICE_GUILD_UNMUTE",   new ActionStyle(new Color(230, 126, 34),  "🛡️")),
            Map.entry("VOICE_GUILD_DEAFEN",   new ActionStyle(new Color(230, 126, 34),  "🛡️")),
            Map.entry("VOICE_GUILD_UNDEAFEN", new ActionStyle(new Color(230, 126, 34),  "🛡️"))
    );

    private static final ActionStyle DEFAULT_STYLE = new ActionStyle(new Color(128, 128, 128), "📋");

    private String truncateField(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max - 3) + "...";
    }

    public void log(TextChannel channel, String action, Member moderator, Member target, String reason, String extra) {
        if (channel == null) {
            logger.warn("Log de moderation ignore: channel null pour action={}", action);
            return;
        }

        String actionKey = action != null ? action.toUpperCase() : "UNKNOWN";
        ActionStyle style = ACTION_STYLES.getOrDefault(actionKey, DEFAULT_STYLE);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm:ss");
        String timestamp = OffsetDateTime.now().format(formatter);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(truncateField(style.emoji() + " Action de Modération : " + actionKey, 256));
        embed.setColor(style.color());

        embed.addField("👮 Modérateur", moderator != null ? moderator.getAsMention() : "Inconnu / Système", true);

        if (target != null) {
            embed.addField("🎯 Cible", truncateField(target.getUser().getName() + " (<@" + target.getId() + ">)", 1024), true);
        }

        if (reason != null && !reason.isBlank()) {
            embed.addField("📝 Raison", truncateField(reason, 1024), false);
        }

        if (extra != null && !extra.isBlank()) {
            embed.addField("ℹ️ Détails", truncateField(extra, 1024), false);
        }

        embed.setFooter(timestamp);

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> logger.debug("Log moderation envoye: action={}, channelId={}", actionKey, channel.getId()),
                error  -> logger.error("Echec envoi log moderation: action={}, channelId={}", actionKey, channel.getId(), error)
        );
    }
}
```

**KEY DETAILS:**
- **EmbedBuilder construction:** Lines 115-132
- **Action-based color & emoji mapping:** Lines 72-94 (21 different action types)
- **Embed components:**
    - Title: Action emoji + "Action de Modération : " + action key (line 116)
    - Color: Dynamic based on action type (line 117)
    - Field 1: "👮 Modérateur" - Moderator mention (line 119)
    - Field 2: "🎯 Cible" - Target user (line 122)
    - Field 3: "📝 Raison" - Reason (line 126)
    - Field 4: "ℹ️ Détails" - Extra details (line 130)
    - Footer: Timestamp in format "dd/MM/yyyy à HH:mm:ss" (line 133)

**COLOR CODES USED:**
- KICK: RGB(255, 165, 0) - Orange
- BAN: RGB(220, 20, 60) - Crimson red
- CLEAR: RGB(30, 144, 255) - Dodger blue
- TIMEOUT: RGB(148, 0, 211) - Purple
- UNBAN: RGB(60, 179, 113) - Medium sea green
- WARN: RGB(255, 215, 0) - Gold
- CLEARWARN: RGB(70, 130, 180) - Steel blue
- MESSAGE_EDIT: RGB(123, 104, 238) - Medium slate blue
- MESSAGE_DELETE: RGB(255, 99, 71) - Tomato
- VOICE_CONNECT: RGB(46, 204, 113) - Emerald
- VOICE_DISCONNECT: RGB(241, 196, 15) - Sunflower
- VOICE_MOD_DISCONNECT: RGB(231, 76, 60) - Alizarin
- VOICE_MOVE: RGB(52, 152, 219) - Peter river
- VOICE_SELF_MUTE/UNMUTE/DEAFEN/UNDEAFEN: RGB(155, 89, 182) - Wisteria
- VOICE_GUILD_MUTE/UNMUTE/DEAFEN/UNDEAFEN: RGB(230, 126, 34) - Carrot
- DEFAULT: RGB(128, 128, 128) - Gray

---

## 2. WelcomeListener.java
**File Path:** `/e/dev/sakura-bot/src/main/java/fr/sakura/bot/listeners/WelcomeListener.java`

**EmbedBuilder Construction (Lines 49-70):**

```java
EmbedBuilder embed = new EmbedBuilder();
embed.setTitle("Bienvenue !");
embed.setDescription("Bienvenue " + event.getMember().getAsMention() + " sur **" + event.getGuild().getName() + "** !");
embed.setColor(new Color(43, 45, 49));

if (event.getMember().getUser().getAvatarUrl() != null) {
    embed.setThumbnail(event.getMember().getUser().getAvatarUrl());
}

// Image optionnelle : ignorée si non configurée dans le .env
if (welcomeImageUrl != null && !welcomeImageUrl.isBlank()) {
    embed.setImage(welcomeImageUrl);
} else {
    logger.debug("Aucune image de bienvenue configuree (WELCOME_IMAGE_URL absent)");
}

embed.setFooter(
        "Arrivée à " + event.getMember().getTimeJoined().format(DateTimeFormatter.ofPattern("HH:mm"))
                + " • Membre n°" + memberCount
                + " • " + joinTime,
        event.getMember().getUser().getEffectiveAvatarUrl()
);

channel.sendMessageEmbeds(embed.build()).queue(
        success -> logger.info("Message de bienvenue envoye pour {} dans #{}",
                event.getUser().getId(), channel.getName()),
        error -> logger.error("Echec envoi message de bienvenue pour {}", event.getUser().getId(), error)
);
```

**EMBED COMPONENTS:**
- **Title:** "Bienvenue !" (line 50)
- **Description:** "Bienvenue @member sur **server_name**!" (line 51)
- **Color:** RGB(43, 45, 49) - Dark gray/charcoal (line 52)
- **Thumbnail:** Member's avatar URL (line 55)
- **Image:** Optional welcome image from environment (line 60)
- **Footer:** Format "Arrivée à HH:mm • Membre n°X • dd/MM/yyyy HH:mm" with avatar icon (lines 65-70)

---

## 3. ServerInfoCommand.java
**File Path:** `/e/dev/sakura-bot/src/main/java/fr/sakura/bot/commands/ServerInfoCommand.java`

**EmbedBuilder Construction (Lines 40-59):**

```java
EmbedBuilder embed = new EmbedBuilder();
embed.setTitle("\uD83C\uDF38 " + guild.getName());
embed.setColor(new Color(255, 183, 197)); // Rose sakura
embed.setThumbnail(guild.getIconUrl());

embed.addField("\uD83D\uDC51 Propriétaire", guild.getOwner() != null ? guild.getOwner().getAsMention() : "Inconnu", true);
embed.addField("\uD83D\uDC65 Membres", String.valueOf(guild.getMemberCount()), true);
embed.addField("\uD83D\uDCAC Salons", String.valueOf(guild.getChannels().size()), true);
embed.addField("\uD83C\uDFAD Rôles", String.valueOf(guild.getRoles().size()), true);
embed.addField("\uD83D\uDE00 Emojis", String.valueOf(guild.getEmojis().size()), true);
embed.addField("\uD83D\uDD12 Niveau de vérification", guild.getVerificationLevel().name(), true);
embed.addField("\uD83D\uDCC5 Créé le", guild.getTimeCreated().format(formatter), false);

if (guild.getBannerUrl() != null) {
    embed.setImage(guild.getBannerUrl() + "?size=1024");
}

embed.setFooter("ID : " + guild.getId());

event.replyEmbeds(embed.build()).queue();
```

**EMBED COMPONENTS:**
- **Title:** Cherry blossom emoji (🌸) + guild name (line 41)
- **Color:** RGB(255, 183, 197) - Light pink/Sakura (line 42) - **Theme color**
- **Thumbnail:** Guild icon (line 43)
- **Fields (6 fields, inline=true except last):**
    - Line 45: "👑 Propriétaire" → Guild owner mention
    - Line 46: "👥 Membres" → Member count
    - Line 47: "💬 Salons" → Channel count
    - Line 48: "🎭 Rôles" → Role count
    - Line 49: "😀 Emojis" → Emoji count
    - Line 50: "🔒 Niveau de vérification" → Verification level
- **Field (non-inline):**
    - Line 51: "📅 Créé le" → Creation date (format: dd/MM/yyyy à HH:mm)
- **Image:** Guild banner at 1024px (line 54)
- **Footer:** "ID : [guild_id]" (line 57)

---

## 4. UserInfoCommand.java
**File Path:** `/e/dev/sakura-bot/src/main/java/fr/sakura/bot/commands/UserInfoCommand.java`

**EmbedBuilder Construction (Lines 54-68):**

```java
EmbedBuilder embed = new EmbedBuilder();
embed.setTitle("\uD83D\uDCCB Informations sur " + target.getUser().getName());
embed.setColor(new Color(255, 183, 197));
embed.setThumbnail(target.getUser().getEffectiveAvatarUrl() + "?size=512");

embed.addField("\uD83D\uDCDB Pseudo", target.getUser().getName(), true);
embed.addField("\uD83C\uDFF7️ Surnom", target.getNickname() != null ? target.getNickname() : "Aucun", true);
embed.addField("\uD83E\uDD16 Bot", target.getUser().isBot() ? "Oui" : "Non", true);
embed.addField("\uD83D\uDCC5 Compte créé le", target.getUser().getTimeCreated().format(formatter), true);
embed.addField("\uD83D\uDCE5 A rejoint le serveur le", target.getTimeJoined().format(formatter), true);
embed.addField("\uD83C\uDFAD Rôles (" + target.getRoles().size() + ")", roles, false);

embed.setFooter("ID : " + target.getId());

event.replyEmbeds(embed.build()).queue();
```

**EMBED COMPONENTS:**
- **Title:** Clipboard emoji (📋) + "Informations sur " + username (line 55)
- **Color:** RGB(255, 183, 197) - Light pink/Sakura (line 56) - **Same as ServerInfo**
- **Thumbnail:** User avatar at 512px (line 57)
- **Fields (5 inline fields, 1 non-inline):**
    - Line 59: "📛 Pseudo" → Username
    - Line 60: "🏷️ Surnom" → Nickname or "Aucun"
    - Line 61: "🤖 Bot" → "Oui" or "Non"
    - Line 62: "📅 Compte créé le" → Account creation date
    - Line 63: "📥 A rejoint le serveur le" → Server join date
    - Line 64: "🎭 Rôles (N)" → List of roles (comma-separated mentions)
- **Footer:** "ID : [user_id]" (line 66)

---

## 5. AvatarCommand.java
**File Path:** `/e/dev/sakura-bot/src/main/java/fr/sakura/bot/commands/AvatarCommand.java`

**EmbedBuilder Construction (Lines 45-50):**

```java
EmbedBuilder embed = new EmbedBuilder();
embed.setTitle("\uD83D\uDDBC️ Avatar de " + target.getUser().getName());
embed.setColor(new Color(255, 183, 197));
embed.setImage(avatarUrl);
embed.setFooter("Clique sur l'image pour la voir en taille originale");

event.replyEmbeds(embed.build()).queue();
```

**EMBED COMPONENTS:**
- **Title:** Picture frame emoji (🖼️) + "Avatar de " + username (line 46)
- **Color:** RGB(255, 183, 197) - Light pink/Sakura (line 47) - **Same as UserInfo & ServerInfo**
- **Image:** User avatar URL at 1024px (line 48)
- **Footer:** "Clique sur l'image pour la voir en taille originale" (line 49)

---

## COMMANDS USING ModerationLogger EMBEDS

The following commands trigger the ModerationLogger embed through `moderationLogger.log()` method:

### BanCommand.java
- **Lines 87-90:** Triggers "BAN" action log after successful ban
- **Calls:** `moderationLogger.log(logChannel, "BAN", event.getMember(), target, reason, null);`

### KickCommand.java
- **Lines 75-78:** Triggers "KICK" action log after successful kick
- **Calls:** `moderationLogger.log(logChannel, "KICK", event.getMember(), target, reason, null);`

### WarnCommand.java
- **Lines 87-96:** Triggers "WARN" action log after successful warning
- **Calls:** `moderationLogger.log(logChannel, "WARN", event.getMember(), target, reason, "Total warnings: " + totalWarnings);`

### TimeoutCommand.java
- **Lines 80-88:** Triggers "TIMEOUT" action log after successful timeout
- **Calls:** `moderationLogger.log(logChannel, "TIMEOUT", event.getMember(), target, finalReason, "Duree: " + minutes + " minute(s)");`

### UnbanCommand.java
- **Lines 87-95:** Triggers "UNBAN" action log after successful unban
- **Calls:** `moderationLogger.log(logChannel, "UNBAN", event.getMember(), null, finalReason, "Utilisateur: " + bannedUser.getName() + " (" + userId + ")");`

### ClearCommand.java
- **Lines 68-75:** Triggers "CLEAR" action log after messages deleted
- **Calls:** `moderationLogger.log(logChannel, "CLEAR", event.getMember(), null, "Nettoyage de salon", messages.size() + " message(s) supprimé(s)");`

### ClearWarningsCommand.java
- **Lines 78-86:** Triggers "CLEARWARN" action log after warnings cleared
- **Calls:** `moderationLogger.log(logChannel, "CLEARWARN", event.getMember(), target, "Reset des warnings", removed + " warning(s) retire(s)");`

### ModerationActivityListener.java (Auto-logging events)
Logs the following actions automatically via `moderationLogger.logInGuild()`:
- **Line 52:** MESSAGE_EDIT - Triggered on message update
- **Line 67:** MESSAGE_DELETE - Triggered on message delete
- **Lines 79-86:** VOICE_CONNECT - When member joins voice channel
- **Lines 92-99:** VOICE_DISCONNECT - When member leaves voice channel
- **Lines 106-113:** VOICE_MOVE - When member moves between voice channels
- **Line 123:** VOICE_SELF_MUTE/UNMUTE - When member toggles microphone
- **Line 132:** VOICE_SELF_DEAFEN/UNDEAFEN - When member toggles deafness
- **Line 141:** VOICE_GUILD_MUTE/UNMUTE - Server-side mute toggle
- **Line 150:** VOICE_GUILD_DEAFEN/UNDEAFEN - Server-side deafen toggle
- **Lines 169-176:** VOICE_MOD_DISCONNECT - Moderator kicked user from voice

---

## STYLING SUMMARY

**Primary Color Theme:** RGB(255, 183, 197) - Light pink/Sakura used for:
- UserInfoCommand
- ServerInfoCommand
- AvatarCommand

**Moderation Colors:** Dynamic per-action colors in ModerationLogger ranging from blues, reds, greens, purples, oranges, and grays.

**Common Emoji Usage:**
- Moderation: 👢 🔨 🧹 ⏳ 🔓 ⚠️ 🧾 ✏️ 🗑️ 🔊 🔇 🚫 🔁 🎧 🛡️
- Info: 🌸 👑 👥 💬 🎭 😀 🔒 📅 📋 🏷️ 🤖 📥 🖼️

**Date Format:** dd/MM/yyyy à HH:mm (with seconds for moderation logs: dd/MM/yyyy à HH:mm:ss)