package fr.sakura.bot;

import fr.sakura.bot.commands.CommandManager;
import fr.sakura.bot.listeners.ModerationActivityListener;
import fr.sakura.bot.listeners.SecurityListener;
import fr.sakura.bot.listeners.WelcomeListener;
import fr.sakura.bot.utils.ModerationLogger;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import fr.sakura.bot.database.DatabaseManager;
import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.listeners.AutoModListener;
import fr.sakura.bot.listeners.LevelListener;
import fr.sakura.bot.listeners.TicketListener;
import fr.sakura.bot.utils.LevelService;
import fr.sakura.bot.utils.TicketService;

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
        String databaseUrl = dotenv.get("DATABASE_URL");

        if (token == null || token.isEmpty()) {
            logger.error("DISCORD_TOKEN absent ou vide dans le fichier .env");
            return;
        }

        if (guildId == null || guildId.isEmpty()) {
            logger.error("GUILD_ID absent ou vide dans le fichier .env");
            return;
        }

        logger.info("Demarrage du bot Discord pour le serveur autorise {}", guildId);
        logger.info("Configuration: welcomeChannelId={}, logChannelId={}, warningsFilePath={}, databaseUrl={}",
                welcomeChannelId,
                logChannelId,
                (warningsFilePath == null || warningsFilePath.isEmpty()) ? "data/warnings.json" : warningsFilePath,
                (databaseUrl == null || databaseUrl.isBlank()) ? "jdbc:sqlite:data/sakura.db (fallback)" : databaseUrl);

        // Init SQLite DB
        DatabaseManager.initialize(databaseUrl);

        // Initialisation des services
        SettingsManager settingsManager = new SettingsManager();
        ModerationLogger moderationLogger = new ModerationLogger(logChannelId);
        LevelService levelService = new LevelService();
        TicketService ticketService = new TicketService(settingsManager);

        CommandManager commandManager = new CommandManager(guildId, moderationLogger, warningsFilePath, settingsManager, levelService, ticketService);
        SecurityListener securityListener = new SecurityListener(guildId, commandManager);
        WelcomeListener welcomeListener = new WelcomeListener(welcomeChannelId, welcomeImageUrl);
        ModerationActivityListener moderationActivityListener = new ModerationActivityListener(moderationLogger);
        AutoModListener autoModListener = new AutoModListener(moderationLogger, settingsManager);
        LevelListener levelListener = new LevelListener(levelService);
        TicketListener ticketListener = new TicketListener(ticketService, moderationLogger);

        JDABuilder.createLight(token)
                .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_VOICE_STATES,
                        GatewayIntent.MESSAGE_CONTENT
                )
                .enableCache(CacheFlag.VOICE_STATE)
                .addEventListeners(securityListener, commandManager, welcomeListener, moderationActivityListener, autoModListener, levelListener, ticketListener)
                .setActivity(Activity.playing("Sakura Bot (" + guildId + ")"))
                .build();

        logger.info("Initialisation JDA lancee avec intents: GUILD_MESSAGES, GUILD_MEMBERS, GUILD_VOICE_STATES, MESSAGE_CONTENT");
    }
}