package fr.sakura.bot.protect;

import fr.sakura.bot.database.ProtectSettingsManager;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JoinProtectionListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(JoinProtectionListener.class);
    private static final int CRITICAL_RISK_THRESHOLD = 85;
    private static final int MODERATE_RISK_THRESHOLD = 60;
    private static final int RAID_MODE_RISK_THRESHOLD = 40;

    private final ProtectSettingsManager protectSettingsManager;
    private final ModerationLogListener moderationLogListener;

    private final Map<String, Deque<Instant>> joinWindows = new ConcurrentHashMap<>();
    private final Map<String, Long> raidModeUntil = new ConcurrentHashMap<>();

    public JoinProtectionListener(ProtectSettingsManager protectSettingsManager, ModerationLogListener moderationLogListener) {
        this.protectSettingsManager = protectSettingsManager;
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        String guildId = event.getGuild().getId();
        if (!protectSettingsManager.isAntiBotEnabled(guildId)) return;

        Guild guild = event.getGuild();
        Member member = event.getMember();

        long hoursOld = Duration.between(member.getUser().getTimeCreated(), OffsetDateTime.now()).toHours();
        int minAccountAge = protectSettingsManager.getMinAccountAgeHours(guildId);
        int raidThreshold = protectSettingsManager.getRaidJoinThreshold(guildId);
        int raidWindowSeconds = protectSettingsManager.getRaidWindowSeconds(guildId);
        int raidDurationSeconds = protectSettingsManager.getRaidModeDurationSeconds(guildId);

        int burstCount = registerJoinAndGetBurstCount(guildId, raidWindowSeconds);

        if (burstCount >= raidThreshold) {
            raidModeUntil.put(guildId, System.currentTimeMillis() + (raidDurationSeconds * 1000L));
        }

        boolean raidModeActive = isRaidModeActive(guildId);
        int suspicionScore = JoinRiskScorer.computeScore(hoursOld, minAccountAge, burstCount, raidThreshold, raidModeActive);

        String reason = "score=" + suspicionScore
                + " | ageHours=" + hoursOld + "/" + minAccountAge
                + " | burst=" + burstCount + "/" + raidThreshold
                + " | raidMode=" + raidModeActive;

        logger.info("Protect join decision guildId={}, userId={}, {}", guildId, member.getId(), reason);

        if (suspicionScore >= CRITICAL_RISK_THRESHOLD) {
            if (!applyQuarantine(guild, member, "Risque critique: " + reason)) {
                kickMember(guild, member, "Risque critique: " + reason);
            }
            return;
        }

        if (suspicionScore >= MODERATE_RISK_THRESHOLD || (raidModeActive && suspicionScore >= RAID_MODE_RISK_THRESHOLD)) {
            applyQuarantine(guild, member, "Risque modéré: " + reason);
        }
    }

    private int registerJoinAndGetBurstCount(String guildId, int windowSeconds) {
        Instant now = Instant.now();
        Deque<Instant> window = joinWindows.computeIfAbsent(guildId, k -> new ArrayDeque<>());
        synchronized (window) {
            window.addLast(now);
            while (!window.isEmpty() && Duration.between(window.peekFirst(), now).getSeconds() > windowSeconds) {
                window.pollFirst();
            }
            return window.size();
        }
    }

    private boolean isRaidModeActive(String guildId) {
        long until = raidModeUntil.getOrDefault(guildId, 0L);
        return until > System.currentTimeMillis();
    }

    private boolean applyQuarantine(Guild guild, Member member, String reason) {
        String roleId = protectSettingsManager.getQuarantineRoleId(guild.getId());
        if (roleId == null || roleId.isBlank()) {
            return false;
        }

        Role quarantineRole = guild.getRoleById(roleId);
        if (quarantineRole == null) {
            logger.warn("Protect quarantine: rôle introuvable guildId={}, roleId={}", guild.getId(), roleId);
            return false;
        }

        if (!guild.getSelfMember().canInteract(member) || !guild.getSelfMember().canInteract(quarantineRole)) {
            logger.warn("Protect quarantine impossible (hiérarchie) guildId={}, userId={}", guild.getId(), member.getId());
            return false;
        }

        guild.addRoleToMember(member, quarantineRole).reason("Sakura Protect: " + reason).queue(
                ok -> moderationLogListener.logAction(guild, "AUTOMOD_WARN", null, member,
                        "Sakura Protect: quarantaine préventive",
                        "> **Motif :** " + reason + "\n> **Action :** rôle quarantaine attribué"),
                err -> logger.error("Protect quarantine échouée pour userId={}", member.getId(), err)
        );
        return true;
    }

    private void kickMember(Guild guild, Member member, String reason) {
        if (!guild.getSelfMember().canInteract(member)) {
            logger.warn("Protect kick impossible (hiérarchie) guildId={}, userId={}", guild.getId(), member.getId());
            return;
        }

        guild.kick(member).reason("Sakura Protect: " + reason).queue(
                ok -> moderationLogListener.logAction(guild, "KICK", null, member,
                        "Sakura Protect: exclusion préventive",
                        "> **Motif :** " + reason),
                err -> logger.error("Protect kick échoué pour userId={}", member.getId(), err)
        );
    }
}
