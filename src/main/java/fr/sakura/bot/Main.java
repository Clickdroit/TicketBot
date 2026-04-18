package fr.sakura.bot;

import fr.sakura.bot.commands.CommandManager;
import fr.sakura.bot.listeners.ModerationActivityListener;
import fr.sakura.bot.listeners.SecurityListener;
import fr.sakura.bot.listeners.WelcomeListener;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import fr.sakura.bot.utils.ModerationLogger;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // Chargement du fichier .env
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        
        String token = dotenv.get("DISCORD_TOKEN");
        String guildId = dotenv.get("GUILD_ID");
        String welcomeChannelId = dotenv.get("WELCOME_CHANNEL_ID");
        String logChannelId = dotenv.get("LOG_CHANNEL_ID");
        String warningsFilePath = dotenv.get("WARNINGS_FILE_PATH");

        if (token == null || token.isEmpty()) {
            logger.error("DISCORD_TOKEN absent ou vide dans le fichier .env");
            return;
        }
        
        if (guildId == null || guildId.isEmpty()) {
            logger.error("GUILD_ID absent ou vide dans le fichier .env");
            return;
        }

        logger.info("Demarrage du bot Discord pour le serveur autorise {}", guildId);
        logger.info("Configuration: welcomeChannelId={}, logChannelId={}, warningsFilePath={}",
                welcomeChannelId,
                logChannelId,
                (warningsFilePath == null || warningsFilePath.isEmpty()) ? "data/warnings.json" : warningsFilePath);

        // Instanciation des gestionnaires
        CommandManager commandManager = new CommandManager(guildId, logChannelId, warningsFilePath);
        SecurityListener securityListener = new SecurityListener(guildId, commandManager);
        WelcomeListener welcomeListener = new WelcomeListener(welcomeChannelId);
        ModerationActivityListener moderationActivityListener = new ModerationActivityListener(new ModerationLogger(logChannelId));

        // Création du bot
        JDABuilder.createLight(token)
                // L'intent GUILD_MEMBERS est requis pour savoir quand quelqu'un rejoint le serveur (Welcome message)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_VOICE_STATES)
                .addEventListeners(securityListener, commandManager, welcomeListener, moderationActivityListener)
                .setActivity(Activity.playing("Sakura Bot (" + guildId + ")"))
                .build();

        logger.info("Initialisation JDA lancee avec intents: GUILD_MESSAGES, GUILD_MEMBERS, GUILD_VOICE_STATES");
    }
}
