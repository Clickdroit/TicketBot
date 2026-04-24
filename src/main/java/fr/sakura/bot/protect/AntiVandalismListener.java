package fr.sakura.bot.protect;

import fr.sakura.bot.database.ProtectSettingsManager;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class AntiVandalismListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AntiVandalismListener.class);

    private static final EnumSet<Permission> DANGEROUS_PERMISSIONS = EnumSet.of(
            Permission.ADMINISTRATOR,
            Permission.BAN_MEMBERS,
            Permission.KICK_MEMBERS,
            Permission.MANAGE_SERVER,
            Permission.MANAGE_ROLES,
            Permission.MANAGE_CHANNEL,
            Permission.MANAGE_WEBHOOKS
    );

    private final ProtectSettingsManager protectSettingsManager;
    private final ModerationLogListener moderationLogListener;

    private final Map<String, Integer> strikeByActor = new ConcurrentHashMap<>();
    private final Map<String, Long> lastStrikeAt = new ConcurrentHashMap<>();

    public AntiVandalismListener(ProtectSettingsManager protectSettingsManager, ModerationLogListener moderationLogListener) {
        this.protectSettingsManager = protectSettingsManager;
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;
        Instant eventTime = Instant.now();
        withMatchingActor(event.getGuild(), ActionType.CHANNEL_CREATE, event.getChannel().getId(), eventTime, actor -> {
            if (isTrustedActor(event.getGuild(), actor)) return;
            event.getChannel().delete().queue();
            applyProgressiveSanction(event.getGuild(), actor, "Création de salon non autorisée");
        });
    }

    @Override
    public void onChannelDelete(@NotNull ChannelDeleteEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;
        Instant eventTime = Instant.now();
        withMatchingActor(event.getGuild(), ActionType.CHANNEL_DELETE, event.getChannel().getId(), eventTime, actor -> {
            if (isTrustedActor(event.getGuild(), actor)) return;
            event.getGuild().createTextChannel(event.getChannel().getName()).reason("Sakura Protect: restauration après suppression illicite").queue();
            applyProgressiveSanction(event.getGuild(), actor, "Suppression de salon non autorisée");
        });
    }

    @Override
    public void onRoleCreate(@NotNull RoleCreateEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;
        Instant eventTime = Instant.now();
        withMatchingActor(event.getGuild(), ActionType.ROLE_CREATE, event.getRole().getId(), eventTime, actor -> {
            if (isTrustedActor(event.getGuild(), actor)) return;
            event.getRole().delete().reason("Sakura Protect: rôle créé sans autorisation").queue();
            applyProgressiveSanction(event.getGuild(), actor, "Création de rôle non autorisée");
        });
    }

    @Override
    public void onRoleDelete(@NotNull RoleDeleteEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;
        Instant eventTime = Instant.now();
        withMatchingActor(event.getGuild(), ActionType.ROLE_DELETE, event.getRole().getId(), eventTime, actor -> {
            if (isTrustedActor(event.getGuild(), actor)) return;
            event.getGuild().createRole().setName(event.getRole().getName()).reason("Sakura Protect: restauration après suppression illicite").queue();
            applyProgressiveSanction(event.getGuild(), actor, "Suppression de rôle non autorisée");
        });
    }

    @Override
    public void onRoleUpdatePermissions(@NotNull RoleUpdatePermissionsEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;

        boolean hasDangerousAddition = false;
        for (Permission permission : DANGEROUS_PERMISSIONS) {
            if (event.getNewPermissions().contains(permission) && !event.getOldPermissions().contains(permission)) {
                hasDangerousAddition = true;
                break;
            }
        }
        if (!hasDangerousAddition) return;

        Instant eventTime = Instant.now();
        withMatchingActor(event.getGuild(), ActionType.ROLE_UPDATE, event.getRole().getId(), eventTime, actor -> {
            if (isTrustedActor(event.getGuild(), actor)) return;
            event.getRole().getManager().setPermissions(event.getOldPermissions()).reason("Sakura Protect: permissions dangereuses révoquées").queue();
            applyProgressiveSanction(event.getGuild(), actor, "Attribution de permissions dangereuses non autorisée");
        });
    }

    @Override
    public void onGuildBan(@NotNull GuildBanEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;
        Instant eventTime = Instant.now();
        withMatchingActor(event.getGuild(), ActionType.BAN, event.getUser().getId(), eventTime, actor -> {
            if (isTrustedActor(event.getGuild(), actor)) return;
            event.getGuild().unban(event.getUser()).reason("Sakura Protect: ban non autorisé").queue();
            applyProgressiveSanction(event.getGuild(), actor, "Bannissement non autorisé");
        });
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;
        Instant eventTime = Instant.now();
        withMatchingActor(event.getGuild(), ActionType.KICK, event.getUser().getId(), eventTime, actor -> {
            if (isTrustedActor(event.getGuild(), actor)) return;
            applyProgressiveSanction(event.getGuild(), actor, "Expulsion (kick) non autorisée");
        });
    }

    private boolean isTrustedActor(Guild guild, Member actor) {
        if (actor == null) return true;
        if (actor.getId().equals(guild.getOwnerId())) return true;
        if (actor.getUser().isBot()) return true;
        if (actor.hasPermission(Permission.ADMINISTRATOR) || actor.hasPermission(Permission.MANAGE_SERVER)) return true;

        List<String> whitelist = protectSettingsManager.getWhitelist(guild.getId());
        if (whitelist.contains(actor.getId())) return true;

        Set<String> trustedRoleIds = Set.copyOf(protectSettingsManager.getTrustedRoleIds(guild.getId()));
        for (Role role : actor.getRoles()) {
            if (trustedRoleIds.contains(role.getId())) {
                return true;
            }
        }
        return false;
    }

    private void applyProgressiveSanction(Guild guild, Member actor, String reason) {
        if (actor == null || !guild.getSelfMember().canInteract(actor)) {
            return;
        }

        String key = guild.getId() + ":" + actor.getId();
        long now = System.currentTimeMillis();
        long lastAt = lastStrikeAt.getOrDefault(key, 0L);
        if (now - lastAt > TimeUnit.MINUTES.toMillis(30)) {
            strikeByActor.remove(key);
        }

        int strikes = strikeByActor.merge(key, 1, Integer::sum);
        lastStrikeAt.put(key, now);

        logger.warn("Protect sanction decision guildId={}, actorId={}, strikes={}, reason={}", guild.getId(), actor.getId(), strikes, reason);

        if (strikes == 1) {
            moderationLogListener.logAction(guild, "AUTOMOD_WARN", null, actor,
                    "Sakura Protect: activité suspecte détectée",
                    "> **Règle :** anti-vandalisme\n> **Motif :** " + reason + "\n> **Action :** avertissement préventif");
            return;
        }

        if (strikes == 2 && guild.getSelfMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            actor.timeoutFor(10, TimeUnit.MINUTES).reason("Sakura Protect: " + reason).queue(
                    ok -> moderationLogListener.logAction(guild, "TIMEOUT", null, actor,
                            "Sakura Protect: récidive anti-vandalisme",
                            "> **Motif :** " + reason + "\n> **Action :** timeout 10 minutes"),
                    err -> logger.error("Impossible d'appliquer un timeout Protect à {}", actor.getId(), err)
            );
            return;
        }

        guild.ban(actor, 1, TimeUnit.DAYS).reason("Sakura Protect: " + reason).queue(
                ok -> moderationLogListener.logAction(guild, "BAN", null, actor,
                        "Sakura Protect: récidive grave anti-vandalisme",
                        "> **Motif :** " + reason + "\n> **Action :** ban progressif"),
                err -> logger.error("Impossible de bannir le membre {} via Protect", actor.getId(), err)
        );
    }

    private void withMatchingActor(Guild guild, ActionType actionType, String expectedTargetId, Instant eventTime, Consumer<Member> actorConsumer) {
        guild.retrieveAuditLogs().type(actionType).limit(6).queue(logs -> {
            String actorId = resolveActorId(guild, logs, expectedTargetId, eventTime);
            if (actorId == null) return;

            Member actor = guild.getMemberById(actorId);
            if (actor == null) {
                logger.debug("Protect: acteur introuvable en cache pour guildId={}, actorId={}", guild.getId(), actorId);
                return;
            }

            actorConsumer.accept(actor);
        }, err -> logger.error("Protect: impossible de lire les audit logs ({})", actionType, err));
    }

    private String resolveActorId(Guild guild, List<AuditLogEntry> logs, String expectedTargetId, Instant eventTime) {
        List<ProtectAuditCorrelation.AuditEntrySnapshot> snapshots = new ArrayList<>();
        for (AuditLogEntry entry : logs) {
            String actorId = entry.getUser() == null ? null : entry.getUser().getId();
            String targetId = entry.getTargetId();
            Instant createdAt = entry.getTimeCreated().toInstant();
            snapshots.add(new ProtectAuditCorrelation.AuditEntrySnapshot(actorId, targetId, createdAt));
        }
        return ProtectAuditCorrelation.findActorId(snapshots, expectedTargetId, eventTime, guild.getSelfMember().getId());
    }
}
