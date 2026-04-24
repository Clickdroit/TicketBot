package fr.sakura.bot.protect;

import fr.sakura.bot.database.ProtectSettingsManager;
import fr.sakura.bot.utils.ModerationLogger;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AntiVandalismListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AntiVandalismListener.class);
    private final ProtectSettingsManager protectSettingsManager;
    private final ModerationLogger moderationLogger;

    private static final EnumSet<Permission> DANGEROUS_PERMISSIONS = EnumSet.of(
            Permission.ADMINISTRATOR,
            Permission.BAN_MEMBERS,
            Permission.KICK_MEMBERS,
            Permission.MANAGE_SERVER,
            Permission.MANAGE_ROLES,
            Permission.MANAGE_CHANNEL,
            Permission.MANAGE_WEBHOOKS
    );

    public AntiVandalismListener(ProtectSettingsManager protectSettingsManager, ModerationLogger moderationLogger) {
        this.protectSettingsManager = protectSettingsManager;
        this.moderationLogger = moderationLogger;
    }

    private boolean isWhitelisted(Guild guild, String userId) {
        if (userId.equals(guild.getOwnerId())) return true;
        List<String> whitelist = protectSettingsManager.getWhitelist(guild.getId());
        return whitelist.contains(userId);
    }

    private void sanction(Guild guild, String userId, String reason) {
        Member member = guild.getMemberById(userId);
        if (member == null) return;
        if (!guild.getSelfMember().canInteract(member)) return;

        guild.ban(member, 7, TimeUnit.DAYS).reason("Sakura Protect: " + reason).queue(
                success -> moderationLogger.logInGuild(guild, "BAN", null, member, "Sakura Protect", reason),
                error -> logger.error("Impossible de bannir le vandale {}", userId, error)
        );
    }

    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;

        event.getGuild().retrieveAuditLogs().type(ActionType.CHANNEL_CREATE).limit(1).queue(logs -> {
            if (logs.isEmpty()) return;
            AuditLogEntry entry = logs.get(0);
            String userId = entry.getUser().getId();

            if (!isWhitelisted(event.getGuild(), userId)) {
                event.getChannel().delete().queue();
                sanction(event.getGuild(), userId, "Création de salon non autorisée");
            }
        });
    }

    @Override
    public void onChannelDelete(@NotNull ChannelDeleteEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;

        event.getGuild().retrieveAuditLogs().type(ActionType.CHANNEL_DELETE).limit(1).queue(logs -> {
            if (logs.isEmpty()) return;
            AuditLogEntry entry = logs.get(0);
            String userId = entry.getUser().getId();

            if (!isWhitelisted(event.getGuild(), userId)) {
                // Re-création simplifiée (on ne peut pas tout restaurer parfaitement sans backup complet)
                event.getGuild().createTextChannel(event.getChannel().getName()).queue(
                        newChannel -> moderationLogger.logInGuild(event.getGuild(), "PROTECT", null, null, "Sakura Protect", "Salon '" + event.getChannel().getName() + "' restauré après suppression illicite.")
                );
                sanction(event.getGuild(), userId, "Suppression de salon non autorisée");
            }
        });
    }

    @Override
    public void onRoleCreate(@NotNull RoleCreateEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;

        event.getGuild().retrieveAuditLogs().type(ActionType.ROLE_CREATE).limit(1).queue(logs -> {
            if (logs.isEmpty()) return;
            AuditLogEntry entry = logs.get(0);
            String userId = entry.getUser().getId();

            if (!isWhitelisted(event.getGuild(), userId)) {
                event.getRole().delete().reason("Sakura Protect: Création non autorisée").queue();
                sanction(event.getGuild(), userId, "Création de rôle non autorisée");
            }
        });
    }

    @Override
    public void onRoleDelete(@NotNull RoleDeleteEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;

        event.getGuild().retrieveAuditLogs().type(ActionType.ROLE_DELETE).limit(1).queue(logs -> {
            if (logs.isEmpty()) return;
            AuditLogEntry entry = logs.get(0);
            String userId = entry.getUser().getId();

            if (!isWhitelisted(event.getGuild(), userId)) {
                event.getGuild().createRole().setName(event.getRole().getName()).queue();
                sanction(event.getGuild(), userId, "Suppression de rôle non autorisée");
            }
        });
    }

    @Override
    public void onRoleUpdatePermissions(@NotNull RoleUpdatePermissionsEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;

        event.getGuild().retrieveAuditLogs().type(ActionType.ROLE_UPDATE).limit(1).queue(logs -> {
            if (logs.isEmpty()) return;
            AuditLogEntry entry = logs.get(0);
            String userId = entry.getUser().getId();

            if (!isWhitelisted(event.getGuild(), userId)) {
                EnumSet<Permission> newPerms = event.getNewPermissions();
                boolean hasDangerous = false;
                for (Permission p : DANGEROUS_PERMISSIONS) {
                    if (newPerms.contains(p) && !event.getOldPermissions().contains(p)) {
                        hasDangerous = true;
                        break;
                    }
                }

                if (hasDangerous) {
                    event.getRole().getManager().setPermissions(event.getOldPermissions()).reason("Sakura Protect: Permission dangereuse révoquée").queue();
                    sanction(event.getGuild(), userId, "Attribution de permissions dangereuses non autorisée");
                }
            }
        });
    }

    @Override
    public void onGuildBan(@NotNull GuildBanEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;

        event.getGuild().retrieveAuditLogs().type(ActionType.BAN).limit(1).queue(logs -> {
            if (logs.isEmpty()) return;
            AuditLogEntry entry = logs.get(0);
            String userId = entry.getUser().getId();

            if (!isWhitelisted(event.getGuild(), userId)) {
                event.getGuild().unban(event.getUser()).reason("Sakura Protect: Ban non autorisé").queue();
                sanction(event.getGuild(), userId, "Bannissement non autorisé");
            }
        });
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;

        event.getGuild().retrieveAuditLogs().type(ActionType.KICK).limit(1).queue(logs -> {
            if (logs.isEmpty()) return;
            AuditLogEntry entry = logs.get(0);
            // Vérifier si c'est un kick récent
            if (entry.getTargetId().equals(event.getUser().getId())) {
                String userId = entry.getUser().getId();
                if (!isWhitelisted(event.getGuild(), userId)) {
                    sanction(event.getGuild(), userId, "Expulsion (Kick) non autorisée");
                }
            }
        });
    }
}
