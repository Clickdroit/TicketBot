package fr.sakura.bot;

import fr.sakura.bot.commands.BotContext;
import fr.sakura.bot.commands.CommandManager;
import fr.sakura.bot.core.service.TicketService;
import fr.sakura.bot.core.store.TicketStore;
import fr.sakura.bot.database.DatabaseManager;
import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.listeners.SecurityListener;
import fr.sakura.bot.listeners.TicketListener;
import fr.sakura.bot.listeners.log.TicketLogListener;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Point d'entrée principal de TicketBot standalone (multi-serveur).
 * Orchestre l'initialisation du système de tickets, de la base de données et de JDA.
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        String token            = dotenv.get("DISCORD_TOKEN");
        String databaseUrl       = dotenv.get("DATABASE_URL");

        if (token == null || token.isEmpty()) {
            logger.error("DISCORD_TOKEN absent ou vide dans le fichier .env");
            return;
        }

        logger.info("🚀 Démarrage de TicketBot...");

        // 1. Infrastructure BDD
        DatabaseManager.initialize(databaseUrl);
        SettingsManager settingsManager = new SettingsManager();

        // 2. Services Métier
        TicketStore ticketStore = new TicketStore();
        TicketService ticketService = new TicketService(ticketStore, settingsManager);
        
        // 3. Listener de Logs de Tickets
        TicketLogListener ticketLogListener = new TicketLogListener(settingsManager);

        // 4. Contexte et Commandes
        BotContext botContext = new BotContext(
                settingsManager, ticketService, ticketLogListener
        );
        CommandManager commandManager = new CommandManager(botContext);

        // 5. Listeners d'Événements
        SecurityListener securityListener = new SecurityListener(commandManager);
        TicketListener ticketListener = new TicketListener(ticketService, ticketLogListener, settingsManager);

        // 6. Lancement JDA
        net.dv8tion.jda.api.JDA jda = JDABuilder.createLight(token)
                .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.MESSAGE_CONTENT
                )
                .addEventListeners(
                        securityListener, 
                        commandManager, 
                        ticketListener
                )
                .setActivity(Activity.playing("Gérer vos tickets de support"))
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("🛑 Extinction de TicketBot : libération des ressources...");
            DatabaseManager.shutdown();
            jda.shutdown();
        }));

        logger.info("TicketBot prêt au service !");
    }
}
