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

3. **Activer l'intent Server Members** :
   Sur la page de votre bot, allez dans l'onglet "Bot", descendez jusqu'à "Privileged Gateway Intents" et cochez **Server Members Intent**. Sauvegardez. Sans cela, les messages de bienvenue ne fonctionneront pas.

4. **Configurer le bot** :
   Ouvrez le fichier `.env` à la racine du projet.
   Remplacez les valeurs par vos propres identifiants.
   ```env
   DISCORD_TOKEN=votre_vrai_token_ici
   GUILD_ID=votre_vrai_id_de_serveur_ici
   WELCOME_CHANNEL_ID=id_du_salon_pour_les_bienvenues
   LOG_CHANNEL_ID=id_du_salon_de_logs_moderation
   WARNINGS_FILE_PATH=data/warnings.json
   ```

   `WARNINGS_FILE_PATH` est optionnel (par defaut: `data/warnings.json`).

## ⚙️ Comment lancer le bot ?

Le bot utilise le fichier `.env` pour récupérer ses configurations. Vous n'avez pas besoin de configurer de variables d'environnement manuellement.

Pour lancer le bot, exécutez simplement :

```bash
./gradlew run
```

*(Si `./gradlew` n'est pas encore initialisé, vous pouvez utiliser `gradle run` si Gradle est installé sur votre machine).*

## 🛡️ Fonctionnalité d'exclusivité (Mono-Serveur)

Le bot vérifie l'ID des serveurs sur lesquels il se trouve à deux moments :
1. **Au démarrage (`onReady`)** : S'il se trouve sur des serveurs non autorisés, il les quitte immédiatement.
2. **Lorsqu'il rejoint un nouveau serveur (`onGuildJoin`)** : Si l'ID du nouveau serveur ne correspond pas à `GUILD_ID`, il le quitte instantanément.

Vous êtes ainsi assuré que le bot ne pourra être utilisé que sur votre propre serveur.
