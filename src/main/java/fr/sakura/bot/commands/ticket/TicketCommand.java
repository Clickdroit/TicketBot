package fr.sakura.bot.commands.ticket;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.model.TicketEntry;
import fr.sakura.bot.core.service.TicketService;
import fr.sakura.bot.core.util.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Commandes de gestion des tickets (add, remove, rename, list).
 */
public class TicketCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(TicketCommand.class);
    private final TicketService ticketService;

    public TicketCommand(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public String getName() {
        return "ticket";
    }

    @Override
    public String getCategory() {
        return "Tickets";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Gère les tickets de support")
                .addSubcommands(
                        new SubcommandData("add", "Ajoute un membre au ticket actuel")
                                .addOptions(new OptionData(OptionType.USER, "membre", "Le membre à ajouter", true)),
                        new SubcommandData("remove", "Retire un membre du ticket actuel")
                                .addOptions(new OptionData(OptionType.USER, "membre", "Le membre à retirer", true)),
                        new SubcommandData("rename", "Renomme le salon du ticket actuel")
                                .addOptions(new OptionData(OptionType.STRING, "nom", "Le nouveau nom du salon", true).setMaxLength(100)),
                        new SubcommandData("list", "Liste tous les tickets ouverts ou pris en charge")
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        if (event.getGuild() == null) return;

        switch (subcommand) {
            case "add" -> handleAdd(event);
            case "remove" -> handleRemove(event);
            case "rename" -> handleRename(event);
            case "list" -> handleList(event);
        }
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        TicketEntry ticket = ticketService.findByChannelId(event.getGuild().getId(), event.getChannel().getId());
        if (ticket == null) {
            event.reply("❌ Ce salon n'est pas un ticket actif.").setEphemeral(true).queue();
            return;
        }

        Member target = event.getOption("membre", OptionMapping::getAsMember);
        if (target == null) {
            event.reply("❌ Membre introuvable.").setEphemeral(true).queue();
            return;
        }

        TextChannel channel = event.getChannel().asTextChannel();
        channel.getManager().putPermissionOverride(target, ticketService.ticketUserPermissions(), null).queue(
                success -> event.reply("✅ " + target.getAsMention() + " a été ajouté au ticket.").queue(),
                error -> event.reply("❌ Impossible d'ajouter le membre. Vérifiez mes permissions.").setEphemeral(true).queue()
        );
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        TicketEntry ticket = ticketService.findByChannelId(event.getGuild().getId(), event.getChannel().getId());
        if (ticket == null) {
            event.reply("❌ Ce salon n'est pas un ticket actif.").setEphemeral(true).queue();
            return;
        }

        Member target = event.getOption("membre", OptionMapping::getAsMember);
        if (target == null) {
            event.reply("❌ Membre introuvable.").setEphemeral(true).queue();
            return;
        }

        if (target.getId().equals(ticket.userId())) {
            event.reply("❌ Vous ne pouvez pas retirer le propriétaire du ticket.").setEphemeral(true).queue();
            return;
        }

        TextChannel channel = event.getChannel().asTextChannel();
        channel.getManager().removePermissionOverride(target).queue(
                success -> event.reply("✅ " + target.getAsMention() + " a été retiré du ticket.").queue(),
                error -> event.reply("❌ Impossible de retirer le membre. Vérifiez mes permissions.").setEphemeral(true).queue()
        );
    }

    private void handleRename(SlashCommandInteractionEvent event) {
        TicketEntry ticket = ticketService.findByChannelId(event.getGuild().getId(), event.getChannel().getId());
        if (ticket == null) {
            event.reply("❌ Ce salon n'est pas un ticket actif.").setEphemeral(true).queue();
            return;
        }

        String newName = event.getOption("nom", OptionMapping::getAsString);
        TextChannel channel = event.getChannel().asTextChannel();
        
        channel.getManager().setName(newName).queue(
                success -> event.reply("✅ Le salon a été renommé en `" + newName + "`.").queue(),
                error -> event.reply("❌ Impossible de renommer le salon.").setEphemeral(true).queue()
        );
    }

    private void handleList(SlashCommandInteractionEvent event) {
        if (!ticketService.isStaff(event.getMember())) {
            event.reply("❌ Seul le staff peut lister les tickets.").setEphemeral(true).queue();
            return;
        }

        List<TicketEntry> tickets = ticketService.getTicketStore().getOpenTickets(event.getGuild().getId());
        if (tickets.isEmpty()) {
            event.reply("ℹ️ Aucun ticket ouvert ou pris en charge actuellement.").queue();
            return;
        }

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("🎫", "Liste des tickets actifs (" + tickets.size() + ")");
        StringBuilder sb = new StringBuilder();
        
        for (TicketEntry ticket : tickets) {
            sb.append("• <#").append(ticket.channelId()).append(">")
              .append(" | Client : <@").append(ticket.userId()).append(">")
              .append(" | État : `").append(ticket.status()).append("`")
              .append(ticket.claimedBy() != null ? " | Staff : <@" + ticket.claimedBy() + ">" : "")
              .append("\n");
        }
        
        embed.setDescription(sb.toString());
        event.replyEmbeds(embed.build()).queue();
    }
}
