package fr.sakura.bot.commands;

import fr.sakura.bot.utils.EmbedStyle;
import fr.sakura.bot.utils.LevelProfile;
import fr.sakura.bot.utils.LevelService;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.EmbedBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RankCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(RankCommand.class);
    private final LevelService levelService;

    public RankCommand(LevelService levelService) {
        this.levelService = levelService;
    }

    @Override
    public String getName() {
        return "rank";
    }

    @Override
    public String getCategory() {
        return "XP & niveaux";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche le niveau et l'XP d'un membre")
                .addOptions(new OptionData(OptionType.USER, "membre", "Le membre cible", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("❌ Commande utilisable uniquement sur un serveur.").setEphemeral(true).queue();
            return;
        }

        Member target = event.getOption("membre", event.getMember(), OptionMapping::getAsMember);
        if (target == null) {
            event.reply("❌ Membre introuvable.").setEphemeral(true).queue();
            return;
        }

        LevelProfile profile = levelService.getProfile(event.getGuild().getId(), target.getId());
        int xpToNext = levelService.getXpForNextLevel(profile.xp());
        int currentLevelFloor = levelService.getXpThresholdForLevel(profile.level());
        int progress = levelService.getCurrentProgressWithinLevel(profile.xp());
        int nextThreshold = levelService.getXpThresholdForLevel(profile.level() + 1);

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("🌸", "Niveau de " + target.getEffectiveName());
        embed.setThumbnail(target.getEffectiveAvatarUrl());
        embed.addField("🎚️ Niveau", String.valueOf(profile.level()), true);
        embed.addField("✨ XP total", String.valueOf(profile.xp()), true);
        embed.addField("🎯 XP restante", String.valueOf(xpToNext), true);
        embed.addField("📈 Progression", progress + " / " + Math.max(1, nextThreshold - currentLevelFloor), false);
        EmbedStyle.setFooter(embed, "Demandé par " + event.getUser().getName());

        event.replyEmbeds(embed.build()).queue();
        logger.info("/rank envoye guildId={}, targetId={}, requesterId={}", event.getGuild().getId(), target.getId(), event.getUser().getId());
    }
}



