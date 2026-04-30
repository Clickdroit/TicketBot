package fr.sakura.bot.protect;

import fr.sakura.bot.database.ProtectSettingsManager;
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

    private final ProtectSettingsManager protectSettingsManager;
    private final ModerationLogListener moderationLogListener;

    private final Map<String, Integer> strikeByActor = new ConcurrentHashMap<>();
    private final Map<String, Long> lastStrikeAt = new ConcurrentHashMap<>();

    // Snapshots pour restauration enrichie
    private final Map<String, ChannelSnapshot> channelSnapshots = new ConcurrentHashMap<>();
    private final Map<String, RoleSnapshot> roleSnapshots = new ConcurrentHashMap<>();

    private record ChannelSnapshot(
            String name,
            ChannelType type,
            String categoryId,
            int position,
            List<PermissionOverrideSnapshot> overrides
    ) {}

    private record PermissionOverrideSnapshot(
            String targetId,
            long allowed,
            long denied,
            boolean isRole
    ) {}

    private record RoleSnapshot(
            String name,
            int position,
            long permissions,
            Integer color,
            boolean hoisted,
            boolean mentionable
    ) {}

    public AntiVandalismListener(ProtectSettingsManager protectSettingsManager, ModerationLogListener moderationLogListener) {
        this.protectSettingsManager = protectSettingsManager;
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        logger.info("AntiVandalism: Initialisation des snapshots pour {} serveur(s)", event.getJDA().getGuilds().size());
        for (Guild guild : event.getJDA().getGuilds()) {
            takeGuildSnapshot(guild);
        }
    }

    private void takeGuildSnapshot(Guild guild) {
        guild.getChannels().forEach(this::updateChannelSnapshot);
        guild.getRoles().forEach(this::updateRoleSnapshot);
        logger.debug("Snapshot terminé pour {} : {} salons, {} rôles", 
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

        channelSnapshots.put(gc.getId(), new ChannelSnapshot(
                gc.getName(),
                gc.getType(),
                categoryId,
                position,
                overrides
        ));
    }

    private void updateRoleSnapshot(Role role) {
        roleSnapshots.put(role.getId(), new RoleSnapshot(
                role.getName(),
                role.getPosition(),
                role.getPermissionsRaw(),
                role.getColor() != null ? role.getColor().getRGB() : null,
                role.isHoisted(),
                role.isMentionable()
        ));
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
            applyProgressiveSanction(event.getGuild(), actor, "Création de salon non autorisée");
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
                return;
            }
            restoreChannel(event.getGuild(), channelId);
            applyProgressiveSanction(event.getGuild(), actor, "Suppression de salon non autorisée");
        });
    }

    private void restoreChannel(Guild guild, String channelId) {
        ChannelSnapshot snapshot = channelSnapshots.get(channelId);
        if (snapshot == null) {
            logger.warn("Impossible de restaurer le salon {}: snapshot manquant", channelId);
            return;
        }

        Consumer<GuildChannel> setupChannel = gc -> {
            if (snapshot.categoryId() != null) {
                Category category = guild.getCategoryById(snapshot.categoryId());
                if (category != null && gc instanceof ICategorizableChannel c) {
                    c.getManager().setParent(category).queue();
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
                // Note: simplification, on ne gère pas tous les types de listes de positions ici
            }
        };

        String reason = "Sakura Protect: restauration après suppression illicite";
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
            event.getRole().delete().reason("Sakura Protect: rôle créé sans autorisation").queue();
            applyProgressiveSanction(event.getGuild(), actor, "Création de rôle non autorisée");
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
                return;
            }
            restoreRole(event.getGuild(), roleId);
            applyProgressiveSanction(event.getGuild(), actor, "Suppression de rôle non autorisée");
        });
    }

    private void restoreRole(Guild guild, String roleId) {
        RoleSnapshot snapshot = roleSnapshots.get(roleId);
        if (snapshot == null) {
            logger.warn("Impossible de restaurer le rôle {}: snapshot manquant", roleId);
            return;
        }

        guild.createRole()
                .setName(snapshot.name())
                .setPermissions(snapshot.permissions())
                .setColor(snapshot.color())
                .setHoisted(snapshot.hoisted())
                .setMentionable(snapshot.mentionable())
                .reason("Sakura Protect: restauration après suppression illicite")
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
                event.getRole().getManager().setPermissions(event.getOldPermissions()).reason("Sakura Protect: permissions dangereuses révoquées").queue();
                applyProgressiveSanction(event.getGuild(), actor, "Attribution de permissions dangereuses non autorisée");
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
        if (now - lastAt > TimeUnit.MINUTES.toMillis(STRIKE_RESET_MINUTES)) {
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
            actor.timeoutFor(SECOND_STRIKE_TIMEOUT_MINUTES, TimeUnit.MINUTES).reason("Sakura Protect: " + reason).queue(
                    ok -> moderationLogListener.logAction(guild, "TIMEOUT", null, actor,
                            "Sakura Protect: récidive anti-vandalisme",
                            "> **Motif :** " + reason + "\n> **Action :** timeout " + SECOND_STRIKE_TIMEOUT_MINUTES + " minutes"),
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