package fr.sakura.bot.commands.xp;



import fr.sakura.bot.commands.ICommand;

import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.core.model.LevelProfile;
import fr.sakura.bot.core.service.LevelService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LeaderboardCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(LeaderboardCommand.class);
    private static final int LIMIT = 10;
    private final LevelService levelService;

    public LeaderboardCommand(LevelService levelService) {
        this.levelService = levelService;
    }

    @Override
    public String getName() {
        return "leaderboard";
    }

    @Override
    public String getCategory() {
        return "XP & niveaux";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche le classement XP du serveur");
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

        List<LevelProfile> profiles = levelService.getLeaderboard(event.getGuild().getId(), LIMIT);
        if (profiles.isEmpty()) {
            event.reply("ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¾Ãƒâ€šÃ‚Â¹ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Aucun membre n'a encore gagnÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© d'XP.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã¢â‚¬â„¢Ãƒâ€šÃ‚Â¸", "Leaderboard XP");
        StringBuilder description = new StringBuilder();

        for (int i = 0; i < profiles.size(); i++) {
            LevelProfile profile = profiles.get(i);
            Member member = event.getGuild().getMemberById(profile.userId());
            String name = member != null ? member.getEffectiveName() : "<@" + profile.userId() + ">";
            String medal = switch (i) {
                case 0 -> "ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€šÃ‚Â¥ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¡";
                case 1 -> "ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€šÃ‚Â¥Ãƒâ€¹Ã¢â‚¬Â ";
                case 2 -> "ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€šÃ‚Â¥ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â°";
                default -> "ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã¢â‚¬â„¢Ãƒâ€šÃ‚Â¸";
            };
            description.append("**#").append(i + 1).append("** ")
                    .append(medal).append(" ")
                    .append(name)
                    .append(" ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â niveau **")
                    .append(profile.level())
                    .append("**, XP **")
                    .append(profile.xp())
                    .append("**\n");
        }

        embed.setDescription(description.toString());
        if (event.getJDA().getSelfUser().getEffectiveAvatarUrl() != null) {
            embed.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl());
        }
        EmbedStyle.setFooter(embed, "Top " + profiles.size() + " du serveur");
        event.replyEmbeds(embed.build()).queue();
        logger.info("/leaderboard envoye guildId={}, requesterId={}", event.getGuild().getId(), event.getUser().getId());
    }
}
