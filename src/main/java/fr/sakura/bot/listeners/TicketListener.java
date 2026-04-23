package fr.sakura.bot.listeners;

import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import fr.sakura.bot.core.model.TicketEntry;
import fr.sakura.bot.core.service.TicketService;
import fr.sakura.bot.core.util.MdcContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class TicketListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(TicketListener.class);
    private static final String CREATE_ID = "ticket:create";
    private static final String CLAIM_ID = "ticket:claim";
    private static final String CLOSE_ID = "ticket:close";
    private static final String CATEGORY_ID = "ticket:category";

    private final TicketService ticketService;
    private final ModerationLogListener moderationLogListener;

    public TicketListener(TicketService ticketService, ModerationLogListener moderationLogListener) {
        this.ticketService = ticketService;
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!event.isFromGuild() || event.getMember() == null || event.getGuild() == null) {
            return;
        }

        try (var ignored = MdcContext.of("guildId", event.getGuild().getId(), "userId", event.getUser().getId())) {
            if (CATEGORY_ID.equals(event.getComponentId())) {
                if (event.getValues().isEmpty()) {
                    return;
                }
                String selected = event.getValues().get(0);
                String categoryName = getCategoryName(selected);
                handleCreate(event, categoryName);
            }
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!event.isFromGuild() || event.getMember() == null || event.getGuild() == null) {
            return;
        }

        try (var ignored = MdcContext.of("guildId", event.getGuild().getId(), "userId", event.getUser().getId())) {
            if (CREATE_ID.equals(event.getComponentId())) {
                handleCreate(event, "Support");
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
    }

    private String getCategoryName(String id) {
        return switch (id) {
            case "ticket:partnership" -> "Partenariat";
            case "ticket:report" -> "Signalement";
            case "ticket:support" -> "Support";
            case "ticket:suggestion" -> "Suggestion";
            case "ticket:other" -> "Autre";
            default -> "Support";
        };
    }

    private void handleCreate(GenericComponentInteractionCreateEvent event, String categoryLabel) {
        Member requester = event.getMember();
        var guild = event.getGuild();
        if (guild == null || requester == null) return;
        String guildId = guild.getId();

        TicketEntry activeTicket = ticketService.findOpenTicket(guildId, requester.getId());
        if (activeTicket != null) {
            event.reply("ℹ️ Vous avez déjà un ticket actif : <#" + activeTicket.channelId() + ">")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply(true).queue(ignored -> {
            String channelName = ticketService.ticketChannelName(requester);
            Category category = ticketService.resolveTicketCategory(guild);
            List<net.dv8tion.jda.api.entities.Role> supportRoles = ticketService.resolveSupportRoles(guild);
            String supportMention = ticketService.supportMention(guild);
            Member selfMember = guild.getSelfMember();

            if (category != null && !selfMember.hasPermission(category, Permission.MANAGE_CHANNEL)) {
                event.getHook().sendMessage("❌ Je n'ai pas la permission **Gérer les salons** dans la catégorie " + category.getAsMention() + ".").queue();
                return;
            }
            if (category == null && !selfMember.hasPermission(Permission.MANAGE_CHANNEL)) {
                event.getHook().sendMessage("❌ Je n'ai pas la permission **Gérer les salons** pour créer un ticket.").queue();
                return;
            }

            var action = guild.createTextChannel(channelName)
                    .setTopic("Ticket [" + categoryLabel + "] ouvert par " + requester.getUser().getName() + " (" + requester.getId() + ")")
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

                EmbedBuilder embed = EmbedStyle.newActionEmbed("📩", "Nouveau Ticket : " + categoryLabel);
                embed.setAuthor("Support • " + guild.getName(), null, guild.getIconUrl());
                embed.setDescription(String.format("""
                        Bonjour %s !
                        
                        Un membre de notre équipe %s va prendre en charge votre demande.
                        Veuillez expliquer votre demande en détail et nous vous répondrons dès que possible.
                        """,
                        requester.getAsMention(),
                        supportMention));
                
                embed.addField("👤 Utilisateur", requester.getAsMention(), true);
                embed.addField("📌 Sujet", categoryLabel, true);
                embed.addField("⏳ Statut", "En attente de prise en charge", false);
                
                embed.addField("🎯 Actions disponibles", 
                        "✅ **Prendre en charge** - Un modérateur s'occupe de vous\n" + 
                        "🔒 **Fermer** - Clôturer le ticket", false);

                embed.setThumbnail(requester.getEffectiveAvatarUrl());
                EmbedStyle.setFooter(embed, "Ticket ID: " + channel.getName());

                channel.sendMessage(requester.getAsMention() + " " + supportMention).setEmbeds(embed.build())
                        .setActionRow(ticketService.claimButton(), ticketService.closeButton())
                        .queue();

                event.getHook().sendMessage("✅ Ticket créé : " + channel.getAsMention()).queue();
                moderationLogListener.logAction(guild, "TICKET_CREATE", requester, requester, "Ticket ouvert (" + categoryLabel + ")", channel.getId());
                logger.info("Ticket créé");
            }, error -> {
                logger.error("Echec creation ticket", error);
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
        if (guild == null) return;
        var channel = event.getChannel();
        TicketEntry ticket = ticketService.findByChannelId(guild.getId(), channel.getId());
        if (ticket == null || ticket.status() == null || "CLOSED".equalsIgnoreCase(ticket.status())) {
            event.reply("❌ Ticket introuvable ou déjà fermé.").setEphemeral(true).queue();
            return;
        }

        if ("CLAIMED".equalsIgnoreCase(ticket.status())) {
            event.reply("ℹ️ Ticket déjà pris en charge.").setEphemeral(true).queue();
            return;
        }

        TicketEntry claimed = ticketService.claimTicket(guild.getId(), channel.getId(), event.getUser().getId());
        event.reply("✅ Ticket pris en charge par " + event.getUser().getAsMention()).setEphemeral(true).queue();
        
        Member ownerMember = guild.getMemberById(ticket.userId());
        if (ownerMember != null) {
            moderationLogListener.logAction(guild, "TICKET_CLAIM", event.getMember(), ownerMember, "Ticket pris en charge", channel.getId());
        }

        if (claimed != null) {
            channel.sendMessage("✅ " + event.getUser().getAsMention() + " prend en charge ce ticket.").queue();
        }

        logger.info("Ticket claim");
    }

    private void handleClose(ButtonInteractionEvent event) {
        var guild = event.getGuild();
        if (guild == null) return;
        var channel = event.getChannel();
        TicketEntry ticket = ticketService.findByChannelId(guild.getId(), channel.getId());
        if (ticket == null || ticket.status() == null || "CLOSED".equalsIgnoreCase(ticket.status())) {
            event.reply("❌ Ticket introuvable ou déjà fermé.").setEphemeral(true).queue();
            return;
        }

        boolean isOwner = event.getUser().getId().equals(ticket.userId());
        boolean isStaff = ticketService.isStaff(event.getMember());
        if (!isOwner && !isStaff) {
            event.reply("❌ Vous n'êtes pas autorisé à fermer ce ticket.").setEphemeral(true).queue();
            return;
        }

        String closeReason = isOwner ? "Fermé par le demandeur" : "Fermé par le support";
        TicketEntry closed = ticketService.closeTicket(guild.getId(), channel.getId(), event.getUser().getId(), closeReason);

        event.reply("🔒 Ticket fermé. Le salon sera supprimé dans 10 secondes.").setEphemeral(true).queue();
        
        Member ownerMember = guild.getMemberById(ticket.userId());
        if (ownerMember != null) {
            moderationLogListener.logAction(guild, "TICKET_CLOSE", event.getMember(), ownerMember, "Ticket fermé", channel.getId());
        }

        String details = "🔒 Ticket clôturé par " + event.getUser().getAsMention();
        if (closed != null && closed.claimedBy() != null) {
            details += "\n👤 Pris en charge par <@" + closed.claimedBy() + ">";
        }
        channel.sendMessage(details).queue();
        if (channel instanceof TextChannel textChannel) {
            textChannel.delete().queueAfter(10, TimeUnit.SECONDS);
        }
        logger.info("Ticket close");
    }
}
