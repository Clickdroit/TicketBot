package fr.sakura.bot.commands;

import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.utils.TicketService;
import fr.sakura.bot.utils.ModerationLogger;
import fr.sakura.bot.utils.LevelService;
import fr.sakura.bot.utils.WarningService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CommandManager extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CommandManager.class);

    private final String guildId;
    private final Map<String, ICommand> commands = new HashMap<>();

    public CommandManager(String guildId, ModerationLogger moderationLogger, String warningsFilePath, SettingsManager settingsManager, LevelService levelService, TicketService ticketService) {
        this.guildId = guildId;
        WarningService warningService = new WarningService(warningsFilePath);

        logger.info("Initialisation CommandManager guildId={}, warningsFilePath={}",
                guildId,
                (warningsFilePath == null || warningsFilePath.isEmpty()) ? "data/warnings.json" : warningsFilePath);

        addCommand(new PingCommand());
        addCommand(new HelpCommand());
        addCommand(new AvatarCommand());
        addCommand(new UserInfoCommand());
        addCommand(new ServerInfoCommand());
        addCommand(new ClearCommand(moderationLogger));
        addCommand(new KickCommand(moderationLogger));
        addCommand(new BanCommand(moderationLogger));
        addCommand(new TimeoutCommand(moderationLogger));
        addCommand(new UnbanCommand(moderationLogger));
        addCommand(new WarnCommand(moderationLogger, warningService));
        addCommand(new WarningsCommand(warningService));
        addCommand(new ClearWarningsCommand(moderationLogger, warningService));
        addCommand(new ConfigCommand(settingsManager));
        addCommand(new RankCommand(levelService));
        addCommand(new LeaderboardCommand(levelService));
        addCommand(new TicketPanelCommand(ticketService));
        addCommand(new XpAdminCommand(levelService, settingsManager));
        addCommand(new LockCommand(moderationLogger));
        addCommand(new UnlockCommand(moderationLogger));
        addCommand(new SlowmodeCommand(moderationLogger));
        addCommand(new SayCommand(moderationLogger));
    }

    private void addCommand(ICommand command) {
        commands.put(command.getName(), command);
        logger.debug("Commande enregistree dans le routeur: {}", command.getName());
    }

    public void registerCommands(Guild guild) {
        List<SlashCommandData> commandDataList = new ArrayList<>();

        for (ICommand command : commands.values()) {
            commandDataList.add(command.getCommandData());
        }

        guild.updateCommands().addCommands(commandDataList).queue(
                success -> logger.info("{} commandes Slash enregistrees pour {} ({})",
                        commands.size(), guild.getName(), guild.getId()),
                error -> logger.error("Echec enregistrement commandes pour {} ({})",
                        guild.getName(), guild.getId(), error)
        );
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            logger.warn("Interaction slash ignoree hors serveur: commande={}", event.getName());
            return;
        }

        String cid = event.getId() + "-" + UUID.randomUUID().toString().substring(0, 8);
        MDC.put("cid", cid);

        try {
            if (!event.getGuild().getId().equals(guildId)) {
                logger.warn("Interaction rejetee (guild non autorisee): cmd={}, guildId={}, userId={}",
                        event.getName(), event.getGuild().getId(), event.getUser().getId());
                return;
            }

            ICommand command = commands.get(event.getName());
            logger.info("Commande recue: cmd={}, guildId={}, userId={}, channelId={}",
                    event.getName(),
                    event.getGuild().getId(),
                    event.getUser().getId(),
                    event.getChannel().getId());

            if (command != null) {
                try {
                    command.execute(event);
                    logger.debug("Dispatch execute() termine pour cmd={}", event.getName());
                } catch (Exception ex) {
                    logger.error("Exception pendant execution de cmd={}", event.getName(), ex);
                    event.reply("❌ Une erreur interne est survenue.").setEphemeral(true).queue();
                }
            } else {
                logger.warn("Commande inconnue demandee: {}", event.getName());
                event.reply("❌ Commande inconnue.").setEphemeral(true).queue();
            }
        } finally {
            MDC.remove("cid");
        }
    }
}
