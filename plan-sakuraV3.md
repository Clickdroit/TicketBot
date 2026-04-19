# Plan Sakura Bot — V3

> Document de référence listant les bugs connus, incohérences à corriger et nouvelles fonctionnalités à implémenter.

---

## 1. Bugs à corriger

### 1.1 `AutoModListener` — fuite mémoire potentielle sur `lastNoticeByRuleMember`

**Fichier :** `AutoModListener.java`

La map `lastNoticeByRuleMember` est purgée toutes les 30 minutes via `purgeStaleEntries()`, mais la clé composite est `guildId:userId:ruleKey`. Si un utilisateur déclenche l'auto-mod puis quitte le serveur, son entrée reste en mémoire jusqu'au prochain cycle de purge. Sur un serveur très actif, la map peut grossir significativement entre deux purges.

**Correction attendue :** écouter `GuildMemberRemoveEvent` pour supprimer les entrées correspondantes immédiatement.

---

### 1.2 `LevelStore` — synchronisation excessive bloquante

**Fichier :** `LevelStore.java`

Toutes les méthodes sont déclarées `synchronized`, ce qui force une exécution séquentielle de l'ensemble des lectures/écritures XP sur un seul verrou d'instance. Sur un serveur actif avec de nombreux messages simultanés, les threads JDA se retrouvent en attente les uns des autres.

**Correction attendue :** remplacer le verrou global par des verrous par `guildId:userId` (via `ConcurrentHashMap` de `ReentrantLock`), ou migrer vers des requêtes `UPSERT` atomiques sans verrou Java côté applicatif.

---

### 1.3 `TicketListener` — pas de vérification des permissions du bot avant création de salon

**Fichier :** `TicketListener.java`, méthode `handleCreate()`

Avant d'appeler `guild.createTextChannel(...)`, le bot ne vérifie pas s'il a la permission `MANAGE_CHANNELS` dans la catégorie cible. En cas de permissions manquantes, l'erreur est loggée mais le message d'erreur renvoyé à l'utilisateur est générique. De plus, si la catégorie détectée automatiquement a des permissions restrictives, la création échoue silencieusement.

**Correction attendue :** vérifier `guild.getSelfMember().hasPermission(category, Permission.MANAGE_CHANNEL)` avant la tentative, et renvoyer un message explicite si la permission est absente.

---

### 1.4 `WarningStore` — deux connexions SQL ouvertes dans `addWarning()`

**Fichier :** `WarningStore.java`, méthode `addWarning()`

La méthode ouvre une connexion pour l'`INSERT`, la ferme, puis appelle `getWarningsCount()` qui ouvre une seconde connexion pour le `SELECT COUNT(*)`. Ces deux opérations ne sont pas dans la même transaction, ce qui peut produire un compte inexact sous concurrence (un autre thread insère entre les deux appels).

**Correction attendue :** effectuer l'`INSERT` et le `SELECT COUNT(*)` dans une seule connexion avec `conn.setAutoCommit(false)`, ou retourner la valeur issue d'un `SELECT COUNT(*)` dans la même transaction que l'insert.

---

### 1.5 `DatabaseManager` — `hasColumn()` non fiable sur PostgreSQL

**Fichier :** `DatabaseManager.java`, méthode `hasColumn()`

La méthode tente d'abord avec le nom de table tel quel, puis en minuscules. Sur PostgreSQL, `DatabaseMetaData.getColumns()` est sensible à la casse du schéma et peut retourner un résultat vide si le schéma n'est pas explicitement spécifié. Cela peut provoquer des tentatives d'`ALTER TABLE ADD COLUMN` en double lors des migrations, résultant en une erreur SQL.

**Correction attendue :** préciser le schéma (`public`) dans l'appel `getColumns(null, "public", tableName, columnName)` lorsque le dialecte est PostgreSQL.

---

### 1.6 `SecurityListener` — enregistrement des commandes avant fin de purge globale possible

**Fichier :** `SecurityListener.java`, méthode `onReady()`

La purge des commandes globales (`updateCommands().queue(...)`) est asynchrone. Si Discord répond lentement, `checkAndLeaveUnauthorizedGuilds()` — qui déclenche `registerCommands()` — peut s'exécuter avant que la purge soit confirmée. Des doublons de commandes peuvent apparaître temporairement côté Discord.

**Correction attendue :** appeler `commandManager.registerCommands(guild)` uniquement dans le callback `success` de `updateCommands()`, ce qui est déjà partiellement fait mais doit être vérifié : s'assurer que `registerCommands` n'est jamais appelé hors de ce callback.

---

### 1.7 `ModerationLogger` — embed tronqué silencieusement si description > 4096 caractères

**Fichier :** `ModerationLogger.java`, méthodes `logMessageEdit()` et `logMessageDelete()`

Le contenu des messages est tronqué à 400 ou 800 caractères via `sanitizeCodeblock()`, mais la description totale de l'embed (incluant tous les blocs `>`, labels, séparateurs) peut dépasser la limite Discord de 4096 caractères sur des messages très longs ou multi-éditions. JDA lance alors une `IllegalArgumentException` non catchée, et le log de modération est perdu.

**Correction attendue :** envelopper la construction de la description dans un `EmbedStyle.truncate(desc, 4000)` final avant `embed.setDescription(...)`, et logguer un warning si la troncature est déclenchée.

---

### 1.8 `ConfigCommand` — `xpcooldown` accepte des secondes mais stocke en ms sans validation cohérente

**Fichier :** `ConfigCommand.java`, case `xpcooldown`

La sous-commande `xpcooldown` reçoit une valeur en secondes et appelle `settingsManager.setXpCooldownMs(guildId, seconds * 1000)`. Cependant, `setXpCooldownMs()` attend un `int` (ms), alors que le résultat de `seconds * 1000` peut dépasser `Integer.MAX_VALUE` pour des valeurs élevées (> 2 147 483 secondes, soit ~24 jours — hors plage, mais le cast silencieux produirait une valeur négative). La plage max est fixée à 300 secondes dans le slider Discord, donc le bug ne se déclenche pas en pratique, mais la signature est trompeuse.

**Correction attendue :** harmoniser la signature de `setXpCooldownMs()` pour accepter un `long`, ou documenter clairement la contrainte.

---

## 2. Incohérences à corriger

### 2.1 `LevelService` — constructeur sans `SettingsManager` utilise des constantes par défaut silencieusement

**Fichier :** `LevelService.java`

Le constructeur `LevelService()` (sans argument) et `LevelService(LevelStore)` définissent `settingsManager = null` et utilisent des constantes hardcodées (`DEFAULT_COOLDOWN_MS`, `DEFAULT_MIN_GAIN`, etc.). Si ce constructeur est utilisé par erreur en production, le bot ignorera entièrement les paramètres configurés via `/config`, sans aucun warning ni erreur visible.

**Correction attendue :** marquer le constructeur sans `SettingsManager` `@Deprecated` ou le supprimer si plus utilisé, et ajouter un `logger.warn()` si `settingsManager == null` au démarrage du service.

---

### 2.2 `TicketService.isStaff()` et `AutoModListener` — logique de détection du staff dupliquée

**Fichiers :** `TicketService.java`, `AutoModListener.java`

`AutoModListener` exclut les membres avec `ADMINISTRATOR`, `MANAGE_SERVER` ou `MESSAGE_MANAGE`. `TicketService.isStaff()` exclut les membres avec `ADMINISTRATOR`, `MANAGE_CHANNEL`, `MODERATE_MEMBERS` ou un rôle contenant "support"/"staff"/"mod". Ces deux logiques divergent et ne sont pas centralisées.

**Correction attendue :** créer une classe utilitaire `StaffUtils` avec une méthode statique `isStaff(Member)` utilisée dans les deux endroits, avec des permissions et des noms de rôles configurables.

---

### 2.3 `WelcomeListener` — `WELCOME_CHANNEL_ID` uniquement en `.env`, pas en DB

**Fichier :** `WelcomeListener.java`, `Main.java`

Tous les autres paramètres de configuration sont stockés en base via `SettingsManager`, mais `welcomeChannelId` et `welcomeImageUrl` sont passés uniquement depuis le `.env` au constructeur. Il est impossible de les changer sans redémarrer le bot.

**Correction attendue :** ajouter `welcome_channel_id` et `welcome_image_url` dans la table `settings`, les exposer via `/config`, et faire lire `WelcomeListener` depuis `SettingsManager` à chaque événement `GuildMemberJoin`.

---

### 2.4 `CommandManager` — `WarningService` instancié en dur sans injection

**Fichier :** `CommandManager.java`

`WarningService warningService = new WarningService()` est instancié directement dans le constructeur de `CommandManager`, alors que tous les autres services (`ModerationLogger`, `LevelService`, `TicketService`) sont injectés via les paramètres. Cela rend `WarningService` non substituable pour les tests et crée une incohérence d'architecture.

**Correction attendue :** passer `WarningService` en paramètre du constructeur de `CommandManager` et l'instancier dans `Main.java` comme les autres services.

---

### 2.5 `LeaderboardCommand` — description de l'embed non fermée

**Fichier :** `LeaderboardCommand.java`

Dans la boucle de construction du leaderboard, chaque ligne se termine par `.append("\n")` mais le `**` de fermeture du champ `xp` est absent : `"XP **"` est ouvert mais jamais fermé. L'embed s'affiche mal sur Discord (le texte suivant apparaît en gras jusqu'à la fin de la description).

```java
// Ligne fautive :
.append("**, XP **")
.append(profile.xp())
.append("\n");
// Correction :
.append("**, XP **")
.append(profile.xp())
.append("**\n");
```

---

### 2.6 `ModerationLogger` — action `"WARN"` utilisée pour l'auto-mod et la commande `/warn`

**Fichier :** `AutoModListener.java`, `WarnCommand.java`

Les deux systèmes loggent avec l'action `"WARN"`, rendant impossible la distinction dans le salon de logs entre un avertissement manuel d'un modérateur et une détection automatique.

**Correction attendue :** utiliser une action dédiée `"AUTOMOD_WARN"` dans `AutoModListener`, avec son propre style dans `ACTION_STYLES` de `ModerationLogger`.

---

## 3. Nouvelles fonctionnalités

### 3.1 Reaction Roles via boutons

Système permettant à un administrateur de créer un panel de rôles sélectionnables par les membres via des boutons Discord persistants.

**Commandes :**
- `/rolespanel create` — crée et envoie un panel dans le salon courant
- `/rolespanel add <panel_id> <role> <label> <emoji?>` — ajoute un bouton au panel
- `/rolespanel remove <panel_id> <role>` — retire un bouton
- `/rolespanel list` — liste les panels actifs de la guild

**Comportement :**
- Cliquer sur un bouton attribue le rôle si le membre ne l'a pas, le retire sinon (toggle)
- Les panels sont persistants en base (`role_panels` et `role_panel_buttons`)
- Les boutons survivent à un redémarrage du bot (reconstruction depuis la DB au `onReady`)
- Limite : 5 boutons par panel (contrainte Discord sur les `ActionRow`)
- Permissions requises : `MANAGE_ROLES` pour l'admin, rôle cible inférieur au bot dans la hiérarchie

**Tables DB à créer :**
```sql
CREATE TABLE role_panels (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  guild_id TEXT NOT NULL,
  channel_id TEXT NOT NULL,
  message_id TEXT NOT NULL,
  created_at TEXT
);

CREATE TABLE role_panel_buttons (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  panel_id INTEGER NOT NULL REFERENCES role_panels(id),
  role_id TEXT NOT NULL,
  label TEXT NOT NULL,
  emoji TEXT
);
```

**Fichiers à créer :**
- `commands/RolesPanelCommand.java` — commande slash avec sous-commandes
- `listeners/RolesPanelListener.java` — écoute `ButtonInteractionEvent` avec ID `rolespanel:<panelId>:<roleId>`
- `utils/RolesPanelService.java` — logique métier
- `utils/RolesPanelStore.java` — accès DB

---

### 3.2 `/embed`

Commande permettant au staff d'envoyer un embed personnalisé dans n'importe quel salon, sans avoir à modifier le code.

**Commande :** `/embed`

**Options :**
| Option | Type | Requis | Description |
|---|---|---|---|
| `titre` | STRING | oui | Titre de l'embed (max 256 chars) |
| `description` | STRING | oui | Corps du message (max 2000 chars) |
| `couleur` | STRING | non | Code hex (`#FF0000`) ou nom (`rouge`, `vert`, `bleu`, `sakura`) |
| `image` | STRING | non | URL d'une image à afficher en bannière |
| `miniature` | STRING | non | URL d'une miniature (coin supérieur droit) |
| `footer` | STRING | non | Texte du footer (le préfixe `🌸 Sakura •` est ajouté automatiquement) |
| `salon` | CHANNEL | non | Salon cible (défaut : salon courant) |

**Comportement :**
- Requiert la permission `MESSAGE_MANAGE`
- L'interaction de confirmation est éphémère (`setEphemeral(true)`)
- L'embed est envoyé dans le salon cible avec `sendMessageEmbeds()`
- L'action est loggée dans `ModerationLogger` avec l'action `"EMBED"` (à ajouter dans `ACTION_STYLES`)
- Validation de l'URL image/miniature via la même logique que `WelcomeListener.isValidHttpsUrl()`
- Si `couleur` est invalide, fallback sur la couleur sakura par défaut

**Couleurs nommées supportées :**
```
sakura  → #FFA8CC
rouge   → #DC143C
vert    → #2ECC71
bleu    → #3498DB
orange  → #E67E22
violet  → #9B59B6
gris    → #95A5A6
```

**Fichier à créer :**
- `commands/EmbedCommand.java`

---

## 4. Ordre d'implémentation suggéré

1. **Bugs critiques en premier** — `WarningStore#addWarning()` (double connexion), `ModerationLogger` (embed overflow), `LeaderboardCommand` (markdown cassé)
2. **Incohérences d'architecture** — injection de `WarningService`, centralisation `StaffUtils`, `WelcomeListener` en DB
3. **Petites features** — `/embed` (autonome, sans nouvelles tables)
4. **Feature majeure** — Reaction Roles (nouvelles tables, nouveau listener, reconstruction au `onReady`)

---

*Dernière mise à jour : avril 2026*
