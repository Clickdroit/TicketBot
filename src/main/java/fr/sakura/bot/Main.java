package fr.sakura.bot;

import fr.sakura.bot.core.service.WarningService;
import fr.sakura.bot.core.service.RolesPanelService;
import fr.sakura.bot.commands.BotContext;
import fr.sakura.bot.commands.CommandManager;
import fr.sakura.bot.core.service.*;
import fr.sakura.bot.core.store.*;
import fr.sakura.bot.database.DatabaseManager;
import fr.sakura.bot.database.ProtectSettingsManager;
import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.database.SnapshotStore;
import fr.sakura.bot.listeners.*;
import fr.sakura.bot.listeners.log.MessageLogListener;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import fr.sakura.bot.listeners.log.VoiceLogListener;
import fr.sakura.bot.protect.AntiVandalismListener;
import fr.sakura.bot.protect.JoinProtectionListener;
import fr.sakura.bot.protect.PhishingProtectionListener;
import fr.sakura.bot.protect.PhishingService;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Point d'entrée principal du bot Sakura.
 * Orchestre l'initialisation des services, de la base de données et de JDA.
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        String token            = dotenv.get("DISCORD_TOKEN");
        String guildId          = dotenv.get("GUILD_ID");
        String welcomeChannelId  = dotenv.get("WELCOME_CHANNEL_ID");
        String welcomeImageUrl   = dotenv.get("WELCOME_IMAGE_URL");
        String databaseUrl       = dotenv.get("DATABASE_URL");

        if (token == null || token.isEmpty()) {
            logger.error("DISCORD_TOKEN absent ou vide dans le fichier .env");
            return;
        }

        if (guildId == null || guildId.isEmpty()) {
            logger.error("GUILD_ID absent ou vide dans le fichier .env");
            return;
        }

        logger.info("🚀 Démarrage du bot Sakura pour la guilde {}", guildId);

        // 1. Infrastructure Data
        DatabaseManager.initialize(databaseUrl);
        SettingsManager settingsManager = new SettingsManager();
        ProtectSettingsManager protectSettingsManager = new ProtectSettingsManager();
        SnapshotStore snapshotStore = new SnapshotStore();

        // 2. Services Métier
        MessageCacheService messageCacheService = new MessageCacheService();
        SpamDetector spamDetector = new SpamDetector();
        
        LevelStore levelStore = new LevelStore();
        LevelService levelService = new LevelService(levelStore, settingsManager);
        
        TicketStore ticketStore = new TicketStore();
        TicketService ticketService = new TicketService(ticketStore);
        
        WarningStore warningStore = new WarningStore();
        WarningService warningService = new WarningService(warningStore);
        
        RolesPanelStore rolesPanelStore = new RolesPanelStore();
        RolesPanelService rolesPanelService = new RolesPanelService(rolesPanelStore);

        TempBanStore tempBanStore = new TempBanStore();
        TempBanService tempBanService = new TempBanService(tempBanStore);

        TempRoleStore tempRoleStore = new TempRoleStore();
        TempRoleService tempRoleService = new TempRoleService(tempRoleStore);

        AutoModRuleStore autoModRuleStore = new AutoModRuleStore();

        StaffNoteStore staffNoteStore = new StaffNoteStore();
        StaffNoteService staffNoteService = new StaffNoteService(staffNoteStore);

        ModerationReportService moderationReportService = new ModerationReportService(warningStore, settingsManager);

        // 3. Listeners de Logs
        ModerationLogListener moderationLogListener = new ModerationLogListener(settingsManager, messageCacheService);
        MessageLogListener messageLogListener = new MessageLogListener(settingsManager, messageCacheService);
        VoiceLogListener voiceLogListener = new VoiceLogListener(settingsManager, messageCacheService);

        // 4. Contexte et Commandes
        BotContext botContext = new BotContext(
                guildId, settingsManager, levelService, ticketService, warningService, rolesPanelService, moderationLogListener, protectSettingsManager, tempBanService, staffNoteService, tempRoleService, autoModRuleStore
        );
        CommandManager commandManager = new CommandManager(botContext);

        // 5. Autres Listeners
        SecurityListener securityListener = new SecurityListener(guildId, commandManager, rolesPanelService);
        WelcomeListener welcomeListener = new WelcomeListener(settingsManager, welcomeChannelId, welcomeImageUrl);
        AutoModListener autoModListener = new AutoModListener(moderationLogListener, settingsManager, spamDetector, tempBanService, autoModRuleStore);
        LevelListener levelListener = new LevelListener(levelService);
        TicketListener ticketListener = new TicketListener(ticketService, moderationLogListener, settingsManager);
        RolesPanelListener rolesPanelListener = new RolesPanelListener(rolesPanelService);
        PhishingService phishingService = new PhishingService();
        JoinProtectionListener joinProtectionListener = new JoinProtectionListener(protectSettingsManager, moderationLogListener);
        AntiVandalismListener antiVandalismListener = new AntiVandalismListener(protectSettingsManager, moderationLogListener, snapshotStore);
        PhishingProtectionListener phishingProtectionListener = new PhishingProtectionListener(protectSettingsManager, moderationLogListener, phishingService);

        // 6. Lancement JDA
        net.dv8tion.jda.api.JDA jda = JDABuilder.createLight(token)
                .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_VOICE_STATES,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MESSAGE_REACTIONS
                )
                .enableCache(CacheFlag.VOICE_STATE)
                .addEventListeners(
                        securityListener, 
                        commandManager, 
                        welcomeListener, 
                        moderationLogListener,
                        messageLogListener,
                        voiceLogListener,
                        autoModListener, 
                        levelListener, 
                        ticketListener, 
                        rolesPanelListener,
                        joinProtectionListener,
                        antiVandalismListener,
                        phishingProtectionListener
                )
                .setActivity(Activity.playing("Sakura Bot au service de Sena"))
                .build();

        tempBanService.start(jda);
        tempRoleService.start(jda);
        moderationReportService.start(jda);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("🛑 Extinction du bot : libération des ressources...");
            moderationReportService.shutdown();
            spamDetector.shutdown();
            tempBanService.shutdown();
            tempRoleService.shutdown();
            messageCacheService.shutdown();
            phishingService.close();
            DatabaseManager.shutdown();
            jda.shutdown();
        }));

        logger.info("Sakura Bot au service de Sena");
    }
}
