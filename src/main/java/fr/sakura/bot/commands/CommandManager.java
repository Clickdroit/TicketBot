package fr.sakura.bot.commands;

import fr.sakura.bot.commands.ticket.TicketCommand;
import fr.sakura.bot.commands.ticket.TicketPanelCommand;
import fr.sakura.bot.core.util.MdcContext;
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
 * Routeur central des commandes pour TicketBot (Slash, Auto-complete, Context Menus).
 * Supporte le fonctionnement multi-serveur.
 */
public class CommandManager extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CommandManager.class);

    private final Map<String, ICommand> commands = new HashMap<>();

    public CommandManager(BotContext ctx) {
        logger.info("Initialisation CommandManager (mode multi-serveur)");

        // Liste des commandes à enregistrer
        List<ICommand> commandList = List.of(
            new TicketPanelCommand(ctx.ticketService()),
            new TicketCommand(ctx.ticketService()),
            new fr.sakura.bot.commands.ticket.TicketConfigCommand(ctx.settings())
        );

        for (ICommand command : commandList) {
            commands.put(command.getName(), command);
            logger.debug("Commande enregistrée : {}", command.getName());
        }
    }

    public void registerGlobalCommands(net.dv8tion.jda.api.JDA jda) {
        List<CommandData> commandDataList = new ArrayList<>();
        for (ICommand command : commands.values()) {
            commandDataList.add(command.getCommandData());
        }

        jda.updateCommands().addCommands(commandDataList).queue(
                success -> logger.info("{} commandes globales enregistrées avec succès", commands.size()),
                error -> logger.error("Échec de l'enregistrement des commandes globales", error)
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

    private void handleInteraction(String name, net.dv8tion.jda.api.entities.Guild guild, net.dv8tion.jda.api.entities.User user, String channelId, Runnable action, String interactionId) {
        if (guild == null) return;

        String cid = interactionId + "-" + UUID.randomUUID().toString().substring(0, 8);
        
        try (var ignored = MdcContext.of(
                "cid", cid,
                "guildId", guild.getId(),
                "userId", user.getId()
        )) {
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
