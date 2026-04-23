package fr.sakura.bot.commands.xp;



import fr.sakura.bot.commands.ICommand;

import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.core.model.LevelProfile;
import fr.sakura.bot.core.service.LevelService;
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

        if (!levelService.isLevelsEnabled(event.getGuild().getId())) {
            event.reply("❌ Le système de niveaux est désactivé sur ce serveur.").setEphemeral(true).queue();
            return;
        }

        Member target = event.getOption("membre", event.getMember(), OptionMapping::getAsMember);
        if (target == null) {
            event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Membre introuvable.").setEphemeral(true).queue();
            return;
        }

        LevelProfile profile = levelService.getProfile(event.getGuild().getId(), target.getId());
        int xpToNext = levelService.getXpForNextLevel(profile.xp());
        int currentLevelFloor = levelService.getXpThresholdForLevel(profile.level());
        int progress = levelService.getCurrentProgressWithinLevel(profile.xp());
        int nextThreshold = levelService.getXpThresholdForLevel(profile.level() + 1);

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã¢â‚¬â„¢Ãƒâ€šÃ‚Â¸", "Niveau de " + target.getEffectiveName());
        embed.setThumbnail(target.getEffectiveAvatarUrl());
        embed.addField("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã‚Â½Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Niveau", String.valueOf(profile.level()), true);
        embed.addField("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“Ãƒâ€šÃ‚Â¨ XP total", String.valueOf(profile.xp()), true);
        embed.addField("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã‚Â½Ãƒâ€šÃ‚Â¯ XP restante", String.valueOf(xpToNext), true);
        embed.addField("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒâ€¹Ã¢â‚¬Â  Progression", progress + " / " + Math.max(1, nextThreshold - currentLevelFloor), false);
        EmbedStyle.setFooter(embed, "DemandÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© par " + event.getUser().getName());

        event.replyEmbeds(embed.build()).queue();
        logger.info("/rank envoye guildId={}, targetId={}, requesterId={}", event.getGuild().getId(), target.getId(), event.getUser().getId());
    }
}



