package fr.sakura.bot.protect;

import fr.sakura.bot.database.ProtectSettingsManager;
import fr.sakura.bot.database.SnapshotStore;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.attribute.IPositionableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.GenericChannelUpdateEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.role.update.GenericRoleUpdateEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
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
    private static final long STRIKE_RESET_MINUTES = 30;
    private static final int SECOND_STRIKE_TIMEOUT_MINUTES = 10;
    private static final int ANTI_NUKE_THRESHOLD = 3;
    private static final int ANTI_NUKE_WINDOW_SECONDS = 10;
    private static final int MAX_RESTORES_PER_SECOND = 3;

    private final ProtectSettingsManager protectSettingsManager;
    private final ModerationLogListener moderationLogListener;
    private final SnapshotStore snapshotStore;

    private final Map<String, Integer> strikeByActor = new ConcurrentHashMap<>();
    private final Map<String, Long> lastStrikeAt = new ConcurrentHashMap<>();

    // Anti-Nuke tracking
    private final Map<String, Deque<Instant>> destructiveActions = new ConcurrentHashMap<>();
    
    // Rate limit restoration
    private final Map<String, Deque<Instant>> restoreRateLimit = new ConcurrentHashMap<>();

    // Snapshots pour restauration enrichie
    private final Map<String, ChannelSnapshot> channelSnapshots = new ConcurrentHashMap<>();
    private final Map<String, RoleSnapshot> roleSnapshots = new ConcurrentHashMap<>();

    public record ChannelSnapshot(
            String name,
            ChannelType type,
            String categoryId,
            int position,
            List<PermissionOverrideSnapshot> overrides
    ) {}

    public record PermissionOverrideSnapshot(
            String targetId,
            long allowed,
            long denied,
            boolean isRole
    ) {}

    public record RoleSnapshot(
            String name,
            int position,
            long permissions,
            Integer color,
            boolean hoisted,
            boolean mentionable
    ) {}

    public AntiVandalismListener(ProtectSettingsManager protectSettingsManager, ModerationLogListener moderationLogListener, SnapshotStore snapshotStore) {
        this.protectSettingsManager = protectSettingsManager;
        this.moderationLogListener = moderationLogListener;
        this.snapshotStore = snapshotStore;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        logger.info("AntiVandalism: Chargement et initialisation des snapshots...");
        for (Guild guild : event.getJDA().getGuilds()) {
            String guildId = guild.getId();
            
            Map<String, ChannelSnapshot> dbChannels = snapshotStore.loadSnapshots(guildId, "CHANNEL", ChannelSnapshot.class);
            channelSnapshots.putAll(dbChannels);
            
            Map<String, RoleSnapshot> dbRoles = snapshotStore.loadSnapshots(guildId, "ROLE", RoleSnapshot.class);
            roleSnapshots.putAll(dbRoles);

            takeGuildSnapshot(guild);
        }
    }

    private void takeGuildSnapshot(Guild guild) {
        guild.getChannels().forEach(this::updateChannelSnapshot);
        guild.getRoles().forEach(this::updateRoleSnapshot);
        logger.debug("Snapshot terminÃ© pour {} : {} salons, {} rÃ´les", 
                guild.getName(), guild.getChannels().size(), guild.getRoles().size());
    }

    private void updateChannelSnapshot(net.dv8tion.jda.api.entities.channel.Channel channel) {
        if (!(channel instanceof GuildChannel gc)) return;

        List<PermissionOverrideSnapshot> overrides = gc.getPermissionContainer().getPermissionOverrides().stream()
                .map(o -> new PermissionOverrideSnapshot(
                        o.getId(),
                        o.getAllowedRaw(),
                        o.getDeniedRaw(),
                        o.isRoleOverride()
                )).toList();

        String categoryId = (gc instanceof ICategorizableChannel c && c.getParentCategory() != null)
                ? c.getParentCategory().getId()
                : null;

        int position = (gc instanceof IPositionableChannel pc) ? pc.getPosition() : 0;

        ChannelSnapshot snapshot = new ChannelSnapshot(
                gc.getName(),
                gc.getType(),
                categoryId,
                position,
                overrides
        );
        channelSnapshots.put(gc.getId(), snapshot);
        snapshotStore.saveSnapshot(gc.getGuild().getId(), gc.getId(), "CHANNEL", snapshot);
    }

    private void updateRoleSnapshot(Role role) {
        RoleSnapshot snapshot = new RoleSnapshot(
                role.getName(),
                role.getPosition(),
                role.getPermissionsRaw(),
                role.getColor() != null ? role.getColor().getRGB() : null,
                role.isHoisted(),
                role.isMentionable()
        );
        roleSnapshots.put(role.getId(), snapshot);
        snapshotStore.saveSnapshot(role.getGuild().getId(), role.getId(), "ROLE", snapshot);
    }

    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;
        Instant eventTime = Instant.now();
        withMatchingActor(event.getGuild(), ActionType.CHANNEL_CREATE, event.getChannel().getId(), eventTime, actor -> {
            if (isTrustedActor(event.getGuild(), actor)) {
                updateChannelSnapshot(event.getChannel());
                return;
            }
            event.getChannel().delete().queue();
            applyProgressiveSanction(event.getGuild(), actor, "CrÃ©ation de salon non autorisÃ©e");
        });
    }

    @Override
    public void onChannelDelete(@NotNull ChannelDeleteEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;
        Instant eventTime = Instant.now();
        String channelId = event.getChannel().getId();
        withMatchingActor(event.getGuild(), ActionType.CHANNEL_DELETE, channelId, eventTime, actor -> {
            if (isTrustedActor(event.getGuild(), actor)) {
                channelSnapshots.remove(channelId);
                snapshotStore.deleteSnapshot(event.getGuild().getId(), channelId);
                return;
            }
            if (checkAntiNuke(event.getGuild(), actor)) {
                restoreChannel(event.getGuild(), channelId);
                applyProgressiveSanction(event.getGuild(), actor, "Suppression de salon non autorisÃ©e");
            }
        });
    }

    private void restoreChannel(Guild guild, String channelId) {
        if (!checkRestoreRateLimit(guild.getId())) return;

        ChannelSnapshot snapshot = channelSnapshots.get(channelId);
        if (snapshot == null) {
            logger.warn("Impossible de restaurer le salon {}: snapshot manquant", channelId);
            return;
        }

        Consumer<GuildChannel> setupChannel = gc -> {
            if (snapshot.categoryId() != null) {
                Category category = guild.getCategoryById(snapshot.categoryId());
                if (category != null && gc instanceof ICategorizableChannel c) {
                    if (guild.getSelfMember().hasPermission(category, Permission.MANAGE_ROLES)) {
                        c.getManager().setParent(category).queue();
                    }
                }
            }
            
            // Overrides
            for (PermissionOverrideSnapshot os : snapshot.overrides()) {
                if (os.isRole()) {
                    Role role = guild.getRoleById(os.targetId());
                    if (role != null) gc.getPermissionContainer().getManager().putRolePermissionOverride(role.getIdLong(), os.allowed(), os.denied()).queue();
                } else {
                    Member member = guild.getMemberById(os.targetId());
                    if (member != null) gc.getPermissionContainer().getManager().putMemberPermissionOverride(member.getIdLong(), os.allowed(), os.denied()).queue();
                }
            }
            
            if (gc instanceof IPositionableChannel pc) {
                guild.modifyTextChannelPositions().selectPosition(pc).moveTo(snapshot.position()).queue(null, err -> {});
            }
        };

        String reason = "Sakura Protect: restauration aprÃ¨s suppression illicite";
        if (snapshot.type() == ChannelType.VOICE) {
            guild.createVoiceChannel(snapshot.name()).reason(reason).queue(setupChannel);
        } else if (snapshot.type() == ChannelType.TEXT) {
            guild.createTextChannel(snapshot.name()).reason(reason).queue(setupChannel);
        } else if (snapshot.type() == ChannelType.CATEGORY) {
            guild.createCategory(snapshot.name()).reason(reason).queue(setupChannel);
        } else if (snapshot.type() == ChannelType.STAGE) {
            guild.createStageChannel(snapshot.name()).reason(reason).queue(setupChannel);
        }
    }

    @Override
    public void onRoleCreate(@NotNull RoleCreateEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;
        Instant eventTime = Instant.now();
        withMatchingActor(event.getGuild(), ActionType.ROLE_CREATE, event.getRole().getId(), eventTime, actor -> {
            if (isTrustedActor(event.getGuild(), actor)) {
                updateRoleSnapshot(event.getRole());
                return;
            }
            event.getRole().delete().reason("Sakura Protect: rÃ´le crÃ©Ã© sans autorisation").queue();
            applyProgressiveSanction(event.getGuild(), actor, "CrÃ©ation de rÃ´le non autorisÃ©e");
        });
    }

    @Override
    public void onRoleDelete(@NotNull RoleDeleteEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;
        Instant eventTime = Instant.now();
        String roleId = event.getRole().getId();
        withMatchingActor(event.getGuild(), ActionType.ROLE_DELETE, roleId, eventTime, actor -> {
            if (isTrustedActor(event.getGuild(), actor)) {
                roleSnapshots.remove(roleId);
                snapshotStore.deleteSnapshot(event.getGuild().getId(), roleId);
                return;
            }
            if (checkAntiNuke(event.getGuild(), actor)) {
                restoreRole(event.getGuild(), roleId);
                applyProgressiveSanction(event.getGuild(), actor, "Suppression de rÃ´le non autorisÃ©e");
            }
        });
    }

    private void restoreRole(Guild guild, String roleId) {
        if (!checkRestoreRateLimit(guild.getId())) return;

        RoleSnapshot snapshot = roleSnapshots.get(roleId);
        if (snapshot == null) {
            logger.warn("Impossible de restaurer le rÃ´le {}: snapshot manquant", roleId);
            return;
        }

        guild.createRole()
                .setName(snapshot.name())
                .setPermissions(snapshot.permissions())
                .setColor(snapshot.color())
                .setHoisted(snapshot.hoisted())
                .setMentionable(snapshot.mentionable())
                .reason("Sakura Protect: restauration aprÃ¨s suppression illicite")
                .queue(role -> guild.modifyRolePositions().selectPosition(role).moveTo(snapshot.position()).queue());
    }

    @Override
    public void onRoleUpdatePermissions(@NotNull RoleUpdatePermissionsEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;

        final boolean hasDangerousAddition = event.getNewPermissions().stream()
                .anyMatch(p -> DANGEROUS_PERMISSIONS.contains(p) && !event.getOldPermissions().contains(p));
        
        Instant eventTime = Instant.now();
        withMatchingActor(event.getGuild(), ActionType.ROLE_UPDATE, event.getRole().getId(), eventTime, actor -> {
            if (isTrustedActor(event.getGuild(), actor)) {
                updateRoleSnapshot(event.getRole());
                return;
            }
            
            if (hasDangerousAddition) {
                event.getRole().getManager().setPermissions(event.getOldPermissions()).reason("Sakura Protect: permissions dangereuses rÃ©voquÃ©es").queue();
                applyProgressiveSanction(event.getGuild(), actor, "Attribution de permissions dangereuses non autorisÃ©e");
            }
        });
    }

    @Override
    public void onGenericChannelUpdate(@NotNull GenericChannelUpdateEvent<?> event) {
        if (event.getChannel() instanceof GuildChannel gc) {
            updateChannelSnapshot(gc);
        }
    }

    @Override
    public void onGenericRoleUpdate(@NotNull GenericRoleUpdateEvent<?> event) {
        updateRoleSnapshot(event.getRole());
    }

    @Override
    public void onGuildBan(@NotNull GuildBanEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;
        Instant eventTime = Instant.now();
        withMatchingActor(event.getGuild(), ActionType.BAN, event.getUser().getId(), eventTime, actor -> {
            if (isTrustedActor(event.getGuild(), actor)) return;
            if (checkAntiNuke(event.getGuild(), actor)) {
                event.getGuild().unban(event.getUser()).reason("Sakura Protect: ban non autorisÃ©").queue();
                applyProgressiveSanction(event.getGuild(), actor, "Bannissement non autorisÃ©");
            }
        });
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        if (!protectSettingsManager.isAntiRaidEnabled(event.getGuild().getId())) return;
        Instant eventTime = Instant.now();
        withMatchingActor(event.getGuild(), ActionType.KICK, event.getUser().getId(), eventTime, actor -> {
            if (isTrustedActor(event.getGuild(), actor)) return;
            if (checkAntiNuke(event.getGuild(), actor)) {
                applyProgressiveSanction(event.getGuild(), actor, "Expulsion (kick) non autorisÃ©e");
            }
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

    private boolean checkAntiNuke(Guild guild, Member actor) {
        String key = guild.getId() + ":" + actor.getId();
        Deque<Instant> actions = destructiveActions.computeIfAbsent(key, k -> new ArrayDeque<>());
        Instant now = Instant.now();
        
        synchronized (actions) {
            actions.addLast(now);
            while (!actions.isEmpty() && Duration.between(actions.peekFirst(), now).getSeconds() > ANTI_NUKE_WINDOW_SECONDS) {
                actions.pollFirst();
            }
            
            if (actions.size() > ANTI_NUKE_THRESHOLD) {
                guild.ban(actor, 1, TimeUnit.DAYS).reason("Sakura Protect: ANTI-NUKE TRIGGERED").queue(
                        ok -> moderationLogListener.logAction(guild, "BAN", null, actor,
                                "Sakura Protect: ANTI-NUKE ACTIVÃ‰",
                                "> **Action :** Bannissement immÃ©diat\n> **Cause :** Trop d'actions destructives en peu de temps"),
                        err -> logger.error("Impossible de bannir via Anti-Nuke: {}", actor.getId())
                );
                return false;
            }
        }
        return true;
    }

    private boolean checkRestoreRateLimit(String guildId) {
        Deque<Instant> restores = restoreRateLimit.computeIfAbsent(guildId, k -> new ArrayDeque<>());
        Instant now = Instant.now();
        synchronized (restores) {
            restores.addLast(now);
            while (!restores.isEmpty() && Duration.between(restores.peekFirst(), now).getSeconds() > 1) {
                restores.pollFirst();
            }
            return restores.size() <= MAX_RESTORES_PER_SECOND;
        }
    }

    private void applyProgressiveSanction(Guild guild, Member actor, String reason) {
        if (actor == null || !guild.getSelfMember().canInteract(actor)) {
            return;
        }

        String key = guild.getId() + ":" + actor.getId();
        long now = System.currentTimeMillis();
        long lastAt = lastStrikeAt.getOrDefault(key, 0L);
        if (now - lastAt > TimeUnit.MINUTES.toMillis(STRIKE_RESET_MINUTES)) {
            strikeByActor.remove(key);
        }

        int strikes = strikeByActor.merge(key, 1, Integer::sum);
        lastStrikeAt.put(key, now);

        logger.warn("Protect sanction decision guildId={}, actorId={}, strikes={}, reason={}", guild.getId(), actor.getId(), strikes, reason);

        if (strikes == 1) {
            moderationLogListener.logAction(guild, "AUTOMOD_WARN", null, actor,
                    "Sakura Protect: activitÃ© suspecte dÃ©tectÃ©e",
                    "> **RÃ¨gle :** anti-vandalisme\n> **Motif :** " + reason + "\n> **Action :** avertissement prÃ©ventif");
            return;
        }

        if (strikes == 2 && guild.getSelfMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            actor.timeoutFor(SECOND_STRIKE_TIMEOUT_MINUTES, TimeUnit.MINUTES).reason("Sakura Protect: " + reason).queue(
                    ok -> moderationLogListener.logAction(guild, "TIMEOUT", null, actor,
                            "Sakura Protect: rÃ©cidive anti-vandalisme",
                            "> **Motif :** " + reason + "\n> **Action :** timeout " + SECOND_STRIKE_TIMEOUT_MINUTES + " minutes"),
                    err -> logger.error("Impossible d'appliquer un timeout Protect Ã  {}", actor.getId(), err)
            );
            return;
        }

        guild.ban(actor, 1, TimeUnit.DAYS).reason("Sakura Protect: " + reason).queue(
                ok -> moderationLogListener.logAction(guild, "BAN", null, actor,
                        "Sakura Protect: rÃ©cidive grave anti-vandalisme",
                        "> **Motif :** " + reason + "\n> **Action :** ban progressif"),
                err -> logger.error("Impossible de bannir le membre {} via Protect", actor.getId(), err)
        );
    }

    private void withMatchingActor(Guild guild, ActionType actionType, String expectedTargetId, Instant eventTime, Consumer<Member> actorConsumer) {
        retryWithMatchingActor(guild, actionType, expectedTargetId, eventTime, actorConsumer, 0);
    }

    private void retryWithMatchingActor(Guild guild, ActionType actionType, String expectedTargetId, Instant eventTime, Consumer<Member> actorConsumer, int retryCount) {
        guild.retrieveAuditLogs().type(actionType).limit(10).queue(logs -> {
            String actorId = resolveActorId(guild, logs, expectedTargetId, eventTime);
            if (actorId != null) {
                Member actor = guild.getMemberById(actorId);
                if (actor != null) {
                    actorConsumer.accept(actor);
                    return;
                }
            }

            if (retryCount < 3) {
                eventTime.plusSeconds(2); // Slightly shift window for next retry
                long delay = (retryCount + 1) * 2000L;
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        retryWithMatchingActor(guild, actionType, expectedTargetId, eventTime, actorConsumer, retryCount + 1);
                    }
                }, delay);
            }
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