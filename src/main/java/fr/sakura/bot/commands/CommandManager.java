package fr.sakura.bot.commands;

import fr.sakura.bot.commands.info.*;
import fr.sakura.bot.commands.moderation.*;
import fr.sakura.bot.commands.staff.*;
import fr.sakura.bot.commands.ticket.*;
import fr.sakura.bot.commands.xp.*;
import fr.sakura.bot.core.util.MdcContext;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Routeur central des commandes (Slash, Auto-complete, Context Menus).
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
            // ... (I'll keep the list as it is for now, just changing the types)
            new PingCommand(),
            new HelpCommand(Collections.unmodifiableMap(commands)),
            new AvatarCommand(),
            new UserInfoCommand(),
            new ServerInfoCommand(),
            
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
            
            new RankCommand(ctx.levelService()),
            new LeaderboardCommand(ctx.levelService()),
            new XpAdminCommand(ctx.levelService(), ctx.settings()),
            
            new TicketPanelCommand(ctx.ticketService()),
            
            new ConfigCommand(ctx.settings()),
            new ProtectCommand(ctx.protectSettings()),
            new SayCommand(ctx.moderationLog()),
            new EmbedCommand(ctx.moderationLog()),
            new RolesPanelCommand(ctx.rolesPanelService()),
            new ReglementsCommand(),
            new PubCommand(ctx.moderationLog()),
            new ConditionCommand(ctx.moderationLog()),
            new ViewProfileContext(ctx.levelService(), ctx.warningService()),
            new ReportMessageContext(ctx.moderationLog())
        );

        for (ICommand command : commandList) {
            commands.put(command.getName(), command);
            logger.debug("Commande enregistrée : {}", command.getName());
        }
    }

    public void registerCommands(Guild guild) {
        List<CommandData> commandDataList = new ArrayList<>();
        for (ICommand command : commands.values()) {
            commandDataList.add(command.getCommandData());
        }

        guild.updateCommands().addCommands(commandDataList).queue(
                success -> logger.info("{} commandes enregistrées pour {} ({})",
                        commands.size(), guild.getName(), guild.getId()),
                error -> logger.error("Échec enregistrement commandes pour {} ({})",
                        guild.getName(), guild.getId(), error)
        );
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        handleInteraction(event.getName(), event.getGuild(), event.getUser(), event.getChannel().getId(), () -> {
            ICommand command = commands.get(event.getName());
            if (command != null) command.execute(event);
            else event.reply("❌ Commande inconnue.").setEphemeral(true).queue();
        }, event.getId());
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        ICommand command = commands.get(event.getName());
        if (command != null) {
            command.onAutoComplete(event);
        }
    }

    @Override
    public void onUserContextInteraction(@NotNull UserContextInteractionEvent event) {
        handleInteraction(event.getName(), event.getGuild(), event.getUser(), event.getChannel().getId(), () -> {
            ICommand command = commands.get(event.getName());
            if (command != null) command.onUserContext(event);
            else event.reply("❌ Action inconnue.").setEphemeral(true).queue();
        }, event.getId());
    }

    @Override
    public void onMessageContextInteraction(@NotNull MessageContextInteractionEvent event) {
        handleInteraction(event.getName(), event.getGuild(), event.getUser(), event.getChannel().getId(), () -> {
            ICommand command = commands.get(event.getName());
            if (command != null) command.onMessageContext(event);
            else event.reply("❌ Action inconnue.").setEphemeral(true).queue();
        }, event.getId());
    }

    private void handleInteraction(String name, Guild guild, net.dv8tion.jda.api.entities.User user, String channelId, Runnable action, String interactionId) {
        if (guild == null) return;

        String cid = interactionId + "-" + UUID.randomUUID().toString().substring(0, 8);
        
        try (var ignored = MdcContext.of(
                "cid", cid,
                "guildId", guild.getId(),
                "userId", user.getId()
        )) {
            if (!guild.getId().equals(guildId)) return;

            logger.info("Interaction reçue: cmd={}, guildId={}, userId={}, channelId={}",
                    name, guild.getId(), user.getId(), channelId);

            try {
                action.run();
            } catch (Exception ex) {
                logger.error("Exception pendant l'exécution de cmd={}", name, ex);
            }
        }
    }
}
