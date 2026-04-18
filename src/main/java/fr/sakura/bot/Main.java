package fr.sakura.bot;

import fr.sakura.bot.commands.CommandManager;
import fr.sakura.bot.listeners.ModerationActivityListener;
import fr.sakura.bot.listeners.SecurityListener;
import fr.sakura.bot.listeners.WelcomeListener;
import fr.sakura.bot.utils.ModerationLogger;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        String token          = dotenv.get("DISCORD_TOKEN");
        String guildId        = dotenv.get("GUILD_ID");
        String welcomeChannelId = dotenv.get("WELCOME_CHANNEL_ID");
        String logChannelId   = dotenv.get("LOG_CHANNEL_ID");
        String warningsFilePath = dotenv.get("WARNINGS_FILE_PATH");
        String welcomeImageUrl  = dotenv.get("WELCOME_IMAGE_URL");

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

        // Instance unique partagée entre CommandManager et ModerationActivityListener
        ModerationLogger moderationLogger = new ModerationLogger(logChannelId);

        CommandManager commandManager = new CommandManager(guildId, moderationLogger, warningsFilePath);
        SecurityListener securityListener = new SecurityListener(guildId, commandManager);
        WelcomeListener welcomeListener = new WelcomeListener(welcomeChannelId, welcomeImageUrl);
        ModerationActivityListener moderationActivityListener = new ModerationActivityListener(moderationLogger);

        JDABuilder.createLight(token)
                .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_VOICE_STATES,
                        GatewayIntent.MESSAGE_CONTENT
                )
                .addEventListeners(securityListener, commandManager, welcomeListener, moderationActivityListener)
                .setActivity(Activity.playing("Sakura Bot (" + guildId + ")"))
                .build();

        logger.info("Initialisation JDA lancee avec intents: GUILD_MESSAGES, GUILD_MEMBERS, GUILD_VOICE_STATES, MESSAGE_CONTENT");
    }
}