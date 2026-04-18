package fr.sakura.bot.listeners;

import fr.sakura.bot.utils.EmbedStyle;
import fr.sakura.bot.utils.ModerationLogger;
import fr.sakura.bot.utils.TicketEntry;
import fr.sakura.bot.utils.TicketService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TicketListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(TicketListener.class);
    private static final String CREATE_ID = "ticket:create";
    private static final String CLAIM_ID = "ticket:claim";
    private static final String CLOSE_ID = "ticket:close";

    private final TicketService ticketService;
    private final ModerationLogger moderationLogger;

    public TicketListener(TicketService ticketService, ModerationLogger moderationLogger) {
        this.ticketService = ticketService;
        this.moderationLogger = moderationLogger;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!event.isFromGuild() || event.getMember() == null || event.getGuild() == null) {
            return;
        }

        if (CREATE_ID.equals(event.getComponentId())) {
            handleCreate(event);
            return;
        }

        if (CLAIM_ID.equals(event.getComponentId())) {
            handleClaim(event);
            return;
        }

        if (CLOSE_ID.equals(event.getComponentId())) {
            handleClose(event);
        }
    }

    private void handleCreate(ButtonInteractionEvent event) {
        Member requester = event.getMember();
        var guild = event.getGuild();
        String guildId = guild.getId();

        TicketEntry openTicket = ticketService.findOpenTicket(guildId, requester.getId());
        if (openTicket != null) {
            event.reply("ℹ️ Vous avez déjà un ticket ouvert: <#" + openTicket.channelId() + ">")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply(true).queue(ignored -> {
            String channelName = ticketService.ticketChannelName(requester);
            Category category = ticketService.resolveTicketCategory(guild);
            List<net.dv8tion.jda.api.entities.Role> supportRoles = ticketService.resolveSupportRoles(guild);
            String supportMention = ticketService.supportMention(guild);

            var action = guild.createTextChannel(channelName)
                    .setTopic("Ticket ouvert par " + requester.getUser().getName() + " (" + requester.getId() + ")")
                    .addPermissionOverride(guild.getPublicRole(), null, java.util.EnumSet.of(Permission.VIEW_CHANNEL))
                    .addPermissionOverride(requester, ticketService.ticketUserPermissions(), null)
                    .addPermissionOverride(guild.getSelfMember(), ticketService.ticketStaffPermissions(), null);

            if (category != null) {
                action = action.setParent(category);
            }

            for (var role : supportRoles) {
                action = action.addPermissionOverride(role, ticketService.ticketStaffPermissions(), null);
            }

            action.queue(channel -> {
                ticketService.createTicketRecord(guildId, requester.getId(), channel.getId());

                EmbedBuilder embed = EmbedStyle.newInfoEmbed("🎫", "Ticket ouvert");
                embed.setDescription(requester.getAsMention() + " a ouvert un ticket. " + supportMention + " va prendre le relais.");
                embed.addField("👤 Demandeur", requester.getUser().getName(), true);
                embed.addField("📌 Salon", channel.getAsMention(), true);
                EmbedStyle.setFooter(embed, "Ticket #" + channel.getId());

                channel.sendMessageEmbeds(embed.build())
                        .setActionRow(ticketService.claimButton(), ticketService.closeButton())
                        .queue();

                event.getHook().sendMessage("✅ Ticket créé: " + channel.getAsMention()).queue();
                moderationLogger.logInGuild(guild, "TICKET_CREATE", requester, requester, "Ticket ouvert", channel.getId());
                logger.info("Ticket créé guildId={}, userId={}, channelId={}", guildId, requester.getId(), channel.getId());
            }, error -> {
                logger.error("Echec creation ticket guildId={}, userId={}", guildId, requester.getId(), error);
                event.getHook().sendMessage("❌ Impossible de créer le ticket.").queue();
            });
        });
    }

    private void handleClaim(ButtonInteractionEvent event) {
        if (!ticketService.isStaff(event.getMember())) {
            event.reply("❌ Seul le support peut prendre en charge un ticket.").setEphemeral(true).queue();
            return;
        }

        var guild = event.getGuild();
        var channel = event.getChannel();
        TicketEntry ticket = ticketService.findByChannelId(guild.getId(), channel.getId());
        if (ticket == null || ticket.status() == null || !"OPEN".equalsIgnoreCase(ticket.status())) {
            event.reply("❌ Ticket introuvable ou déjà fermé.").setEphemeral(true).queue();
            return;
        }

        ticketService.claimTicket(guild.getId(), channel.getId(), event.getUser().getId());
        event.reply("✅ Ticket pris en charge par " + event.getUser().getAsMention()).setEphemeral(true).queue();
        moderationLogger.logInGuild(guild, "TICKET_CLAIM", event.getMember(), guild.getMemberById(ticket.userId()), "Ticket pris en charge", channel.getId());
        logger.info("Ticket claim guildId={}, channelId={}, staffId={}", guild.getId(), channel.getId(), event.getUser().getId());
    }

    private void handleClose(ButtonInteractionEvent event) {
        var guild = event.getGuild();
        var channel = event.getChannel();
        TicketEntry ticket = ticketService.findByChannelId(guild.getId(), channel.getId());
        if (ticket == null || ticket.status() == null || !"OPEN".equalsIgnoreCase(ticket.status())) {
            event.reply("❌ Ticket introuvable ou déjà fermé.").setEphemeral(true).queue();
            return;
        }

        boolean isOwner = event.getUser().getId().equals(ticket.userId());
        boolean isStaff = ticketService.isStaff(event.getMember());
        if (!isOwner && !isStaff) {
            event.reply("❌ Vous n'êtes pas autorisé à fermer ce ticket.").setEphemeral(true).queue();
            return;
        }

        ticketService.closeTicket(guild.getId(), channel.getId(), event.getUser().getId(), isOwner ? "Fermé par le demandeur" : "Fermé par le support");
        event.reply("🔒 Ticket fermé. Le salon sera supprimé dans 10 secondes.").setEphemeral(true).queue();
        moderationLogger.logInGuild(guild, "TICKET_CLOSE", event.getMember(), guild.getMemberById(ticket.userId()), "Ticket fermé", channel.getId());

        channel.asTextChannel().sendMessage("🔒 Le ticket est en cours de fermeture...").queue();
        channel.asTextChannel().delete().queueAfter(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS);
        logger.info("Ticket close guildId={}, channelId={}, closedBy={}", guild.getId(), channel.getId(), event.getUser().getId());
    }
}






