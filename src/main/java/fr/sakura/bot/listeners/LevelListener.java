package fr.sakura.bot.listeners;

import fr.sakura.bot.utils.EmbedStyle;
import fr.sakura.bot.utils.LevelService;
import fr.sakura.bot.utils.LevelService.XpResult;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class LevelListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(LevelListener.class);
    private final LevelService levelService;

    public LevelListener(LevelService levelService) {
        this.levelService = levelService;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.getMember() == null) {
            return;
        }

        MDC.put("guildId", event.getGuild().getId());
        MDC.put("userId", event.getAuthor().getId());

        try {
            Member member = event.getMember();
            // Retrait de l'exclusion des admins - le cooldown est suffisant
            
            String content = event.getMessage().getContentRaw();
            XpResult result = levelService.addMessageXp(event.getGuild().getId(), member.getId(), content);
            if (!result.xpAwarded()) {
                return;
            }

            logger.debug("XP attribue guildId={}, userId={}, xpGained={}, level={}",
                    event.getGuild().getId(), member.getId(), result.xpGained(), result.profile().level());

            if (result.leveledUp()) {
                announceLevelUp(event, member, result);
                assignLevelRoleIfConfigured(event, member, result.profile().level());
            }
        } finally {
            MDC.remove("guildId");
            MDC.remove("userId");
        }
    }

    private void announceLevelUp(MessageReceivedEvent event, Member member, XpResult result) {
        int currentLevel = result.profile().level();
        int xpToNext = levelService.getXpForNextLevel(result.profile().xp());

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("✨", "Niveau supérieur");
        embed.setDescription(member.getAsMention() + " passe au niveau **" + currentLevel + "**. Continue comme ça !");
        embed.setThumbnail(member.getEffectiveAvatarUrl());
        embed.addField("XP gagné", "+" + result.xpGained(), true);
        embed.addField("XP total", String.valueOf(result.profile().xp()), true);
        embed.addField("XP restante", String.valueOf(xpToNext), true);
        EmbedStyle.setFooter(embed, "Progression de " + member.getUser().getName());

        event.getChannel().sendMessageEmbeds(embed.build()).queue(
                success -> logger.info("Annonce level up envoyee guildId={}, userId={}, level={}", event.getGuild().getId(), member.getId(), currentLevel),
                error -> logger.warn("Echec annonce level up guildId={}, userId={}", event.getGuild().getId(), member.getId(), error)
        );
    }

    private void assignLevelRoleIfConfigured(MessageReceivedEvent event, Member member, int level) {
        String roleId = levelService.getRewardRoleId(event.getGuild().getId(), level);
        if (roleId == null || roleId.isBlank()) {
            return;
        }

        Role role = event.getGuild().getRoleById(roleId);
        if (role == null) {
            logger.warn("Role de niveau introuvable guildId={}, level={}, roleId={}", event.getGuild().getId(), level, roleId);
            return;
        }

        Member self = event.getGuild().getSelfMember();
        if (!self.hasPermission(Permission.MANAGE_ROLES) || !self.canInteract(role) || !self.canInteract(member)) {
            logger.warn("Impossible d'assigner le role de niveau (permissions/hierarchie) guildId={}, level={}, roleId={}", event.getGuild().getId(), level, roleId);
            return;
        }

        event.getGuild().addRoleToMember(member, role).queue(
                ok -> logger.info("Role de niveau attribue guildId={}, userId={}, roleId={}, level={}", event.getGuild().getId(), member.getId(), roleId, level),
                err -> logger.warn("Echec attribution role de niveau guildId={}, userId={}, roleId={}", event.getGuild().getId(), member.getId(), roleId, err)
        );
    }
}
