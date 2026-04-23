package fr.sakura.bot.commands;

import fr.sakura.bot.commands.info.*;
import fr.sakura.bot.commands.moderation.*;
import fr.sakura.bot.commands.staff.*;
import fr.sakura.bot.commands.ticket.*;
import fr.sakura.bot.commands.xp.*;
import fr.sakura.bot.core.util.MdcContext;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Routeur central des commandes Slash.
 * Utilise BotContext pour l'injection des dépendances et enregistre les commandes par factory.
 */
public class CommandManager extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CommandManager.class);

    private final String guildId;
    private final Map<String, ICommand> commands = new HashMap<>();

    public CommandManager(BotContext ctx) {
        this.guildId = ctx.guildId();
        logger.info("Initialisation CommandManager guildId={}", guildId);

        // Liste des commandes à enregistrer
        List<ICommand> commandList = List.of(
            // Général & Info
            new PingCommand(),
            new HelpCommand(Collections.unmodifiableMap(commands)),
            new AvatarCommand(),
            new UserInfoCommand(),
            new ServerInfoCommand(),
            
            // Modération
            new ClearCommand(ctx.moderationLog()),
            new KickCommand(ctx.moderationLog()),
            new BanCommand(ctx.moderationLog()),
            new TimeoutCommand(ctx.moderationLog()),
            new UntimeoutCommand(ctx.moderationLog()),
            new UnbanCommand(ctx.moderationLog()),
            new WarnCommand(ctx.moderationLog(), ctx.warningService(), ctx.settings()),
            new WarningsCommand(ctx.warningService()),
            new ClearWarningsCommand(ctx.moderationLog(), ctx.warningService()),
            new LockCommand(ctx.moderationLog()),
            new UnlockCommand(ctx.moderationLog()),
            new SlowmodeCommand(ctx.moderationLog()),
            
            // XP & Niveaux
            new RankCommand(ctx.levelService()),
            new LeaderboardCommand(ctx.levelService()),
            new XpAdminCommand(ctx.levelService(), ctx.settings()),
            
            // Tickets
            new TicketPanelCommand(ctx.ticketService()),
            
            // Staff & Divers
            new ConfigCommand(ctx.settings()),
            new SayCommand(ctx.moderationLog()),
            new EmbedCommand(ctx.moderationLog()),
            new RolesPanelCommand(ctx.rolesPanelService()),
            new ReglementsCommand()
        );

        for (ICommand command : commandList) {
            commands.put(command.getName(), command);
            logger.debug("Commande enregistrée : {}", command.getName());
        }
    }

    public void registerCommands(Guild guild) {
        List<SlashCommandData> commandDataList = new ArrayList<>();
        for (ICommand command : commands.values()) {
            commandDataList.add(command.getCommandData());
        }

        guild.updateCommands().addCommands(commandDataList).queue(
                success -> logger.info("{} commandes Slash enregistrées pour {} ({})",
                        commands.size(), guild.getName(), guild.getId()),
                error -> logger.error("Échec enregistrement commandes pour {} ({})",
                        guild.getName(), guild.getId(), error)
        );
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            logger.warn("Interaction slash ignorée hors serveur: commande={}", event.getName());
            return;
        }

        String cid = event.getId() + "-" + UUID.randomUUID().toString().substring(0, 8);
        
        try (var ignored = MdcContext.of(
                "cid", cid,
                "guildId", event.getGuild().getId(),
                "userId", event.getUser().getId()
        )) {
            if (!event.getGuild().getId().equals(guildId)) {
                logger.warn("Interaction rejetée (guild non autorisée): cmd={}, guildId={}, userId={}",
                        event.getName(), event.getGuild().getId(), event.getUser().getId());
                return;
            }

            ICommand command = commands.get(event.getName());
            logger.info("Commande reçue: cmd={}, guildId={}, userId={}, channelId={}",
                    event.getName(),
                    event.getGuild().getId(),
                    event.getUser().getId(),
                    event.getChannel().getId());

            if (command != null) {
                try {
                    command.execute(event);
                } catch (Exception ex) {
                    logger.error("Exception pendant l'exécution de cmd={}", event.getName(), ex);
                    event.reply("❌ Une erreur interne est survenue.").setEphemeral(true).queue();
                }
            } else {
                logger.warn("Commande inconnue demandée: {}", event.getName());
                event.reply("❌ Commande inconnue.").setEphemeral(true).queue();
            }
        }
    }
}
