# Sakura Bot

Un bot Discord en Java utilisant la librairie [JDA (Java Discord API)](https://github.com/discord-jda/JDA).
Ce bot a été spécialement conçu pour fonctionner **uniquement sur un seul serveur (Guild)**. S'il est invité sur un autre serveur, il le quittera automatiquement.

## 🛠️ Prérequis

- Java 17 ou supérieur
- Un compte développeur Discord avec un Bot créé sur le [Portail des développeurs Discord](https://discord.com/developers/applications)
- Gradle (inclus ou installé sur votre machine)

## 🚀 Configuration initiale

1. **Obtenir le Token du Bot** :
   Allez sur le portail développeur Discord, créez une application, allez dans l'onglet "Bot", générez un token et copiez-le.
   *Attention : Ne partagez jamais ce token publiquement !*

2. **Obtenir l'ID de votre serveur** :
   Sur Discord, allez dans "Paramètres utilisateur" > "Avancé" et activez le "Mode développeur".
   Ensuite, faites un clic droit sur l'icône de votre serveur Discord et cliquez sur "Copier l'identifiant du serveur" (Guild ID).

3. **Activer les intents privilégiés** :
   Sur la page de votre bot, allez dans l'onglet "Bot", descendez jusqu'à "Privileged Gateway Intents" et cochez **Server Members Intent**.
   Si vous voulez garder les logs de modification de message, cochez aussi **Message Content Intent**.
   Sauvegardez. Sans `Server Members Intent`, les messages de bienvenue ne fonctionneront pas.

4. **Configurer le bot** :
   Ouvrez le fichier `.env` à la racine du projet.
   Remplacez les valeurs par vos propres identifiants.
   ```env
   DISCORD_TOKEN=votre_vrai_token_ici
   GUILD_ID=votre_vrai_id_de_serveur_ici
   WELCOME_CHANNEL_ID=id_du_salon_pour_les_bienvenues
   WELCOME_IMAGE_URL=url_optionnelle_de_l_image_de_bienvenue
   LOG_CHANNEL_ID=id_du_salon_de_logs_moderation
   WARNINGS_FILE_PATH=data/warnings.json
   DATABASE_URL=jdbc:sqlite:data/sakura.db
   ```

   `WELCOME_IMAGE_URL` est optionnelle et permet de changer l'image de bienvenue sans recompiler.
   `WARNINGS_FILE_PATH` est optionnel (par défaut : `data/warnings.json`).
   `DATABASE_URL` est optionnelle (par défaut/fallback : `jdbc:sqlite:data/sakura.db`).
   Sakura accepte aussi une URL PostgreSQL au format `postgresql://user:password@host:5432/database` (ou `jdbc:postgresql://...`).

## ⚙️ Comment lancer le bot ?

Le bot utilise le fichier `.env` pour récupérer ses configurations. Vous n'avez pas besoin de configurer de variables d'environnement manuellement.

Pour lancer le bot, exécutez simplement :

```bash
./gradlew run
```

*(Si `./gradlew` n'est pas encore initialisé, vous pouvez utiliser `gradle run` si Gradle est installé sur votre machine).*

## 🎨 Convention des embeds

Le style des embeds est centralisé dans `src/main/java/fr/sakura/bot/utils/EmbedStyle.java`.

- Embeds **non-modération** (`/serverinfo`, `/userinfo`, `/avatar`, bienvenue) :
  - Couleur sakura commune
  - Titres normalisés (`emoji + titre`)
  - Dates au format `dd/MM/yyyy à HH:mm`
  - Footer normalisé préfixé par `🌸 Sakura`
- Embeds **modération** :
  - Couleurs/emoji conservés par type d’action dans `ModerationLogger`
  - Format de date `dd/MM/yyyy à HH:mm:ss`
  - Limites de longueur et footer harmonisés via `EmbedStyle`

Pour modifier l’apparence globale des embeds, mettez à jour `EmbedStyle` plutôt que chaque commande individuellement.

## 📈 XP / Levels

Le bot attribue automatiquement de l'XP aux membres actifs via les messages de salon texte.

- `/rank` : affiche le niveau et l'XP d'un membre (ou le vôtre par défaut)
- `/leaderboard` : affiche le top XP du serveur

L'attribution d'XP applique un cooldown anti-farm et ignore les messages trop courts ou ressemblant à des commandes.

## 🎫 Tickets

Le système de tickets permet d'ouvrir un salon privé via un panel Discord.

- `/ticketpanel` : publie le panneau avec le bouton d'ouverture
- Bouton `🎫 Ouvrir un ticket` : crée un salon privé par membre
- Dans le salon ticket : boutons pour prendre en charge ou fermer le ticket

Le bot essaie de détecter automatiquement un salon de support ou une catégorie adaptée si aucun réglage spécifique n'est fourni.

## 🛡️ Fonctionnalité d'exclusivité (Mono-Serveur)

Le bot vérifie l'ID des serveurs sur lesquels il se trouve à deux moments :
1. **Au démarrage (`onReady`)** : S'il se trouve sur des serveurs non autorisés, il les quitte immédiatement.
2. **Lorsqu'il rejoint un nouveau serveur (`onGuildJoin`)** : Si l'ID du nouveau serveur ne correspond pas à `GUILD_ID`, il le quitte instantanément.

Vous êtes ainsi assuré que le bot ne pourra être utilisé que sur votre propre serveur.

## 🧭 Plan Sakura V2

Le plan détaillé d’implémentation et de stabilisation est disponible dans :

- `plan-sakuraV2.prompt.md`

Il couvre :
- l’architecture SQLite robuste,
- l’auto-mod et les sanctions automatiques,
- l’XP/leveling,
- les tickets,
- les utilitaires staff,
- et une phase post-livraison dédiée à la correction systématique des bugs/incohérences.

## ✅ QA, release et rollback (V2)

### Vérification qualité
- Exécuter les tests/build : `./gradlew --no-daemon test build`
- Valider les permissions Discord de chaque commande/feature.
- Rejouer les scénarios critiques (modération, auto-mod, tickets, leveling).

### Release
- Déployer de façon contrôlée (checklist Go/No-Go).
- Surveiller les logs et les erreurs de commandes après déploiement.

### Rollback
- Conserver une sauvegarde SQLite avant migration/déploiement.
- Si régression critique, restaurer la base, revenir à la version précédente et relancer la vérification.

## 🎯 Scope V2 implémenté (cible immédiate)

- Data layer robuste avec migrations versionnées (`schema_migrations`) et indexation.
- Stabilisation auto-mod (pipeline ordonné, strike lifecycle configurable, logs plus explicites).
- XP configurable à chaud + outils staff (`/xpadmin`) + mapping rôles de niveau.
- Tickets avec lifecycle explicite (`OPEN` → `CLAIMED` → `CLOSED`) et anti-duplication active.
- Outils staff supplémentaires (`/lock`, `/unlock`, `/slowmode`, `/say`).

## ✅ Critères de validation (DoD) par module

- **Data** : initialisation idempotente, migrations rejouables, index présents, compat SQLite/PostgreSQL.
- **Auto-mod** : règles évaluées dans un ordre déterministe, motifs lisibles, logs d’audit systématiques.
- **XP** : paramètres runtime fonctionnels, commandes staff opérationnelles, classement/rang cohérents.
- **Tickets** : unicité ticket actif garantie, transitions d’état valides, fermeture traçable.
- **Staff tools** : permissions Discord respectées, feedback clair, logs homogènes.

## 🗄️ Runbook backup / restore SQLite

1. Arrêter le bot.
2. Sauvegarder la base :
   - `cp data/sakura.db data/sakura.db.bak.$(date +%Y%m%d-%H%M%S)`
3. Déployer / migrer.
4. Vérifier les commandes critiques.
5. En cas de rollback :
   - arrêter le bot,
   - restaurer le backup voulu vers `data/sakura.db`,
   - relancer puis valider (`./gradlew --no-daemon test build` + checks fonctionnels).

## 🐘 Checklist compat PostgreSQL

- `DATABASE_URL` valide (`postgres://...` ou `jdbc:postgresql://...`).
- Schéma initialisé via `DatabaseManager.initialize(...)`.
- Vérifier que les migrations ont bien été appliquées (`schema_migrations`).
- Contrôler permissions SQL (CREATE/ALTER/INDEX sur le schéma cible).
- Vérifier les commandes sensibles (tickets, XP, config, modération) après démarrage.
