package fr.sakura.bot.protect;

import fr.sakura.bot.database.ProtectSettingsManager;
import fr.sakura.bot.utils.ModerationLogger;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
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
    private final ProtectSettingsManager protectSettingsManager;
    private final ModerationLogger moderationLogger;

    // Pour la détection de vagues (raid)
    private final Map<String, Deque<Instant>> joinWindows = new ConcurrentHashMap<>();
    private static final int RAID_THRESHOLD = 10; // 10 joins
    private static final long RAID_WINDOW_SEC = 60; // en 60 secondes

    public JoinProtectionListener(ProtectSettingsManager protectSettingsManager, ModerationLogger moderationLogger) {
        this.protectSettingsManager = protectSettingsManager;
        this.moderationLogger = moderationLogger;
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        String guildId = event.getGuild().getId();
        if (!protectSettingsManager.isAntiBotEnabled(guildId)) return;

        Member member = event.getMember();
        Guild guild = event.getGuild();

        // 1. Vérification de l'âge du compte
        OffsetDateTime timeCreated = member.getUser().getTimeCreated();
        long hoursOld = Duration.between(timeCreated, OffsetDateTime.now()).toHours();
        int minAge = protectSettingsManager.getMinAccountAgeHours(guildId);

        if (hoursOld < minAge) {
            kickNewAccount(member, guild, "Compte trop récent (" + hoursOld + "h < " + minAge + "h)");
            return;
        }

        // 2. Détection de vague de raid
        if (isRaidWave(guildId)) {
            kickNewAccount(member, guild, "Détection de raid en cours (vague d'arrivées)");
        }
    }

    private void kickNewAccount(Member member, Guild guild, String reason) {
        if (!guild.getSelfMember().canInteract(member)) return;

        member.getUser().openPrivateChannel().queue(pc -> {
            pc.sendMessage("❌ Vous avez été expulsé de **" + guild.getName() + "**.\n raison : " + reason).queue(
                    success -> performKick(member, guild, reason),
                    error -> performKick(member, guild, reason)
            );
        }, error -> performKick(member, guild, reason));
    }

    private void performKick(Member member, Guild guild, String reason) {
        guild.kick(member).reason("Sakura Protect: " + reason).queue(
                success -> moderationLogger.logInGuild(guild, "PROTECT", null, member, "Sakura Protect", reason),
                error -> logger.error("Impossible d'expulser le compte suspect {}", member.getId(), error)
        );
    }

    private boolean isRaidWave(String guildId) {
        Instant now = Instant.now();
        Deque<Instant> window = joinWindows.computeIfAbsent(guildId, k -> new ArrayDeque<>());
        
        synchronized (window) {
            window.addLast(now);
            while (!window.isEmpty() && Duration.between(window.peekFirst(), now).getSeconds() > RAID_WINDOW_SEC) {
                window.pollFirst();
            }
            return window.size() >= RAID_THRESHOLD;
        }
    }
}
