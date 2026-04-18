package fr.sakura.bot.listeners;

import fr.sakura.bot.utils.EmbedStyle;
import fr.sakura.bot.utils.LevelService;
import fr.sakura.bot.utils.LevelService.XpResult;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        Member member = event.getMember();
        if (member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            return;
        }

        String content = event.getMessage().getContentRaw();
        XpResult result = levelService.addMessageXp(event.getGuild().getId(), member.getId(), content);
        if (!result.xpAwarded()) {
            return;
        }

        logger.debug("XP attribue guildId={}, userId={}, xpGained={}, level={}",
                event.getGuild().getId(), member.getId(), result.xpGained(), result.profile().level());

        if (result.leveledUp()) {
            announceLevelUp(event, member, result);
        }
    }

    private void announceLevelUp(MessageReceivedEvent event, Member member, XpResult result) {
        int currentLevel = result.profile().level();
        int xpToNext = levelService.getXpForNextLevel(result.profile().xp());

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("🌸", "Niveau supérieur !");
        embed.setDescription(member.getAsMention() + " passe au niveau **" + currentLevel + "** !");
        embed.addField("✨ XP gagné", "+" + result.xpGained(), true);
        embed.addField("📊 XP total", String.valueOf(result.profile().xp()), true);
        embed.addField("🎯 XP restante", String.valueOf(xpToNext), true);
        EmbedStyle.setFooter(embed, "Bravo à " + member.getUser().getName());

        event.getChannel().sendMessageEmbeds(embed.build()).queue(
                success -> logger.info("Annonce level up envoyee guildId={}, userId={}, level={}", event.getGuild().getId(), member.getId(), currentLevel),
                error -> logger.warn("Echec annonce level up guildId={}, userId={}", event.getGuild().getId(), member.getId(), error)
        );
    }
}


