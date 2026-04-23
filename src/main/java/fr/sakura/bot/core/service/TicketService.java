package fr.sakura.bot.core.service;

import fr.sakura.bot.core.model.TicketEntry;
import fr.sakura.bot.core.store.TicketStore;
import fr.sakura.bot.core.util.StaffUtils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Logique métier pour la gestion des tickets.
 */
public class TicketService {

    private static final Logger logger = LoggerFactory.getLogger(TicketService.class);
    private final TicketStore ticketStore;

    public TicketService(TicketStore ticketStore) {
        this.ticketStore = ticketStore;
    }

    public TicketStore getTicketStore() {
        return ticketStore;
    }

    public TicketEntry findOpenTicket(String guildId, String userId) {
        return ticketStore.getActiveTicket(guildId, userId);
    }

    public TicketEntry findByChannelId(String guildId, String channelId) {
        return ticketStore.getByChannelId(guildId, channelId);
    }

    public TicketEntry createTicketRecord(String guildId, String userId, String channelId) {
        return ticketStore.createTicket(guildId, userId, channelId);
    }

    public TicketEntry claimTicket(String guildId, String channelId, String claimedBy) {
        return ticketStore.claimTicket(guildId, channelId, claimedBy);
    }

    public TicketEntry closeTicket(String guildId, String channelId, String closedBy, String closeReason) {
        return ticketStore.closeTicket(guildId, channelId, closedBy, closeReason);
    }

    public Category resolveTicketCategory(Guild guild) {
        if (guild == null) return null;
        return guild.getCategories().stream()
                .filter(category -> {
                    String name = category.getName().toLowerCase(Locale.ROOT);
                    return name.contains("ticket") || name.contains("support");
                })
                .findFirst()
                .orElse(null);
    }

    public List<Role> resolveSupportRoles(Guild guild) {
        if (guild == null) return List.of();
        List<Role> roles = new ArrayList<>();
        for (Role role : guild.getRoles()) {
            String lower = role.getName().toLowerCase(Locale.ROOT);
            if (role.hasPermission(Permission.MANAGE_CHANNEL)
                    || lower.contains("support")
                    || lower.contains("staff")
                    || lower.contains("mod")) {
                roles.add(role);
            }
        }
        return roles;
    }

    public String supportMention(Guild guild) {
        List<Role> roles = resolveSupportRoles(guild);
        if (roles.isEmpty()) return "l'équipe de support";
        return roles.stream().map(Role::getAsMention).collect(Collectors.joining(" "));
    }

    public String ticketChannelName(Member member) {
        String base = member.getEffectiveName().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isBlank()) base = "user";
        
        String name = "ticket-" + base;
        // Limite Discord sur les noms de salons
        if (name.length() > 100) {
            name = name.substring(0, 100);
        }
        return name;
    }

    public EnumSet<Permission> ticketUserPermissions() {
        return EnumSet.of(
                Permission.VIEW_CHANNEL,
                Permission.MESSAGE_SEND,
                Permission.MESSAGE_HISTORY,
                Permission.MESSAGE_EMBED_LINKS,
                Permission.MESSAGE_ATTACH_FILES,
                Permission.MESSAGE_ADD_REACTION
        );
    }

    public EnumSet<Permission> ticketStaffPermissions() {
        return EnumSet.of(
                Permission.VIEW_CHANNEL,
                Permission.MESSAGE_SEND,
                Permission.MESSAGE_HISTORY,
                Permission.MESSAGE_MANAGE,
                Permission.MESSAGE_EMBED_LINKS,
                Permission.MESSAGE_ATTACH_FILES,
                Permission.MESSAGE_ADD_REACTION,
                Permission.MANAGE_CHANNEL
        );
    }

    public boolean isStaff(Member member) {
        return StaffUtils.isStaff(member);
    }

    public Button createButton() {
        return Button.primary("ticket:create", "🎫 Ouvrir un ticket");
    }

    public Button claimButton() {
        return Button.success("ticket:claim", "✅ Prendre en charge");
    }

    public Button closeButton() {
        return Button.danger("ticket:close", "🔒 Fermer le ticket");
    }
}
