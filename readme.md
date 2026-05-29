# 🎫 TicketBot
 
Un bot Discord de tickets moderne, performant et sécurisé, développé en Java avec [JDA (Java Discord API)](https://github.com/discord-jda/JDA) et [HikariCP](https://github.com/brettwooldridge/HikariCP).
Extrait de Sakura Bot, ce bot est conçu exclusivement pour un usage **mono-serveur** et se concentre à 100% sur un système de support client fluide, esthétique et robuste.

---

## ✨ Fonctionnalités Principales

### 🎫 Système de Support & Tickets
* **Interface moderne JDA 5** : Les utilisateurs créent des tickets en un clic via un panneau interactif doté de menus de sélection (`StringSelectMenu`) et de formulaires de saisie (`Modal`).
* **Gestion simplifiée du cycle de vie** :
  * **Prise en charge (Claim)** : Un membre du support prend en charge le ticket d'un clic (Bouton).
  * **Fermeture (Close)** : Permet de clore le ticket avec un délai de sécurité de 10 secondes.
* **Transcriptions Automatiques** : À la fermeture d'un ticket, l'historique complet de la discussion est converti en fichier texte et envoyé dans le salon de transcription configuré.
* **Permissions Dynamiques** : Attribution automatique des permissions au client et au staff dans les salons de tickets temporaires.

### 🛡️ Logs & Traçabilité
* **Ticket Logging** : Journalisation automatique des événements de tickets (`TICKET_CREATE`, `TICKET_CLAIM`, `TICKET_CLOSE`) dans un salon dédié avec des embeds esthétiques roses, verts et mauves (thème Sakura).

---

## ⌨️ Commandes (Slash Commands)

Toutes les commandes sont accessibles via `/`. Certaines nécessitent des permissions administratives.

| Commande | Description |
| :--- | :--- |
| `/ticketpanel` | Envoie le panneau interactif de support (nécessite la permission *Gérer les salons*). |
| `/ticket add <membre>` | Ajoute un membre au ticket en cours. |
| `/ticket remove <membre>` | Retire un membre du ticket en cours (impossible de retirer l'auteur). |
| `/ticket rename <nom>` | Renomme le salon du ticket. |
| `/ticket list` | Liste tous les tickets actuellement ouverts ou pris en charge (réservé au staff). |

---

## 🛠️ Installation & Configuration

### Prérequis
* Java 17+
* Token de Bot Discord avec les intents **Server Members** et **Message Content** activés sur le portail développeur.

### Configuration (`.env`)
Créez un fichier `.env` à la racine de votre projet :
```env
DISCORD_TOKEN=votre_token_de_bot
GUILD_ID=id_de_votre_serveur_discord
DATABASE_URL=jdbc:sqlite:data/ticketbot.db
```

### Lancement
```bash
./gradlew run
```

### Initialisation BDD (Sqlite)
La BDD SQLite s'initialisera automatiquement dans `data/ticketbot.db`.
Pour configurer les salons de logs et de transcription dans la base de données :
* Renseignez le salon de logs dans la table `settings` (colonne `log_channel_id`).
* Renseignez le salon de transcription de tickets (colonne `transcript_channel_id`).

---

## 🎨 Personnalisation visuelle
La charte graphique des embeds (couleurs roses Sakura, emojis, séparateurs) est centralisée dans la classe `fr.sakura.bot.core.util.EmbedStyle`. Vous pouvez y éditer les couleurs et les signatures des embeds pour adapter le bot à vos préférences esthétiques.

---
*Développé par Clickdroit.*
