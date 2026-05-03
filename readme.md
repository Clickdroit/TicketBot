# 🌸 Sakura Bot

Un bot Discord polyvalent, performant et sécurisé, développé en Java avec [JDA (Java Discord API)](https://github.com/discord-jda/JDA). 
Conçu exclusivement pour un usage **mono-serveur**, Sakura Bot offre une suite complète d'outils de modération, d'administration, de protection et d'engagement pour votre communauté.

---

## ✨ Fonctionnalités Principales

### 🛡️ Sakura Protect (Sécurité Avancée)
Un système de défense multicouche pour protéger votre serveur en temps réel :
- **Anti-Bot & Anti-Raid** : Analyse du risque lors des joins (score de suspicion) et activation automatique d'un mode raid en cas de vague massive.
- **Anti-Vandalisme (Anti-Nuke)** : Surveillance des actions critiques (suppression de salons, modification de rôles, bannissements massifs) avec restauration automatique via snapshots et sanctions progressives.
- **Anti-Phishing** : Détection et blocage des liens malveillants avec liste blanche de domaines autorisés.
- **Quarantaine** : Isolation automatique des nouveaux membres suspects.

### ⚖️ Modération & Administration
- **Système de Sanctions** : Ban (direct, temporaire, par liste), Kick, Timeout, Warn.
- **Dossier de Modération** : Historique complet des avertissements et notes internes pour chaque utilisateur.
- **Auto-Modération** : Filtrage paramétrable (spam, mots interdits, etc.).
- **Outils de Salon** : Purge de messages (Clear), Verrouillage (Lock/Unlock), Slowmode.
- **Panneaux de Rôles** : Création de menus interactifs pour l'attribution de rôles par les utilisateurs.

### 📈 Engagement & XP
- **Système de Niveaux** : Gain d'XP par l'activité textuelle avec cooldown anti-farm.
- **Récompenses de Rôles** : Attribution automatique de rôles lors du passage de niveaux.
- **Classement & Profil** : Visualisation de la progression via des cartes d'XP et un classement global.

### 🎫 Support & Tickets
- **Système de Tickets** : Gestion fluide du support via des salons privés éphémères.
- **Cycle de Vie** : Prise en charge (Claim) et fermeture avec traçabilité.

---

## ⌨️ Commandes (Slash Commands)

Toutes les commandes sont accessibles via `/`. Certaines nécessitent des permissions administratives.

### 🛡️ Protection (`/protect`)
| Sous-commande | Description |
| :--- | :--- |
| `check <user>` | **[Nouveau]** Analyse le niveau de risque d'un utilisateur (Niveau 1 à 4). |
| `status` | Affiche la configuration active du module Protect. |
| `module <type> <etat>` | Active/Désactive l'Anti-Bot, l'Anti-Raid ou l'Anti-Phishing. |
| `whitelist <add\|remove\|list>` | Gère les utilisateurs ignorés par la protection. |
| `staffrole <add\|remove\|list>` | Définit les rôles de confiance exclus de la surveillance. |
| `accountage <heures>` | Définit l'âge minimum requis pour rejoindre sans suspicion. |
| `raidconfig` | Configure les seuils de détection de raid. |
| `quarantine <role>` | Définit le rôle à attribuer aux membres suspects. |

### 🔨 Modération
| Commande | Description |
| :--- | :--- |
| `/ban <add\|temp\|mass\|list>` | Bannissement définitif, temporaire ou groupé. |
| `/warn <add\|remove\|list\|clear>` | Gestion des avertissements. |
| `/warn history <user>` | Affiche l'historique complet d'un membre. |
| `/warn note-add <user> <note>` | Ajoute une note interne invisible pour l'utilisateur. |
| `/kick <user>` | Expulse un membre du serveur. |
| `/timeout <user> <durée>` | Rend un membre muet temporairement. |
| `/clear <nombre>` | Supprime un grand nombre de messages. |

### ⚙️ Administration & Config
| Commande | Description |
| :--- | :--- |
| `/config` | Configuration générale du bot (salons de logs, bienvenue, etc.). |
| `/automod` | Configuration des règles de filtrage automatique. |
| `/rolespanel` | Création de panneaux d'auto-attribution de rôles. |
| `/message <say\|embed\|announce>` | Envoi de messages personnalisés ou d'annonces. |

### 📊 Informations & XP
| Commande | Description |
| :--- | :--- |
| `/userinfo [user]` | Affiche le profil complet (incluant le dossier staff pour les modérateurs). |
| `/serverinfo` | Informations détaillées sur le serveur. |
| `/xp card` | Affiche votre progression et votre niveau actuel. |
| `/xp top` | Affiche le classement des membres les plus actifs. |
| `/xpadmin` | Gestion de l'XP des membres (ajout, retrait, reset). |

---

## 🛠️ Installation & Configuration

### Prérequis
- Java 17+
- Token de Bot Discord (avec **Server Members** et **Message Content** Intents activés).

### Configuration (`.env`)
Créez un fichier `.env` à la racine :
```env
DISCORD_TOKEN=votre_token
GUILD_ID=id_de_votre_serveur
LOG_CHANNEL_ID=id_salon_logs
DATABASE_URL=jdbc:sqlite:data/sakura.db
```

### Lancement
```bash
./gradlew run
```

---

## 🎨 Personnalisation visuelle
Le style des embeds (couleurs, émojis, séparateurs) est centralisé dans `fr.sakura.bot.core.util.EmbedStyle`. Modifiez cette classe pour changer l'apparence de toutes les commandes du bot instantanément.

---
*Développé avec 🌸 par l'équipe Sakura.*
