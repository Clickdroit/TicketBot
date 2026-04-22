package fr.sakura.bot.commands;

import fr.sakura.bot.utils.EmbedStyle;
import fr.sakura.bot.utils.LevelProfile;
import fr.sakura.bot.utils.LevelService;
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

        List<LevelProfile> profiles = levelService.getLeaderboard(event.getGuild().getId(), LIMIT);
        if (profiles.isEmpty()) {
            event.reply("ℹ️ Aucun membre n'a encore gagné d'XP.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("🌸", "Leaderboard XP");
        StringBuilder description = new StringBuilder();

        for (int i = 0; i < profiles.size(); i++) {
            LevelProfile profile = profiles.get(i);
            Member member = event.getGuild().getMemberById(profile.userId());
            String name = member != null ? member.getEffectiveName() : "<@" + profile.userId() + ">";
            String medal = switch (i) {
                case 0 -> "🥇";
                case 1 -> "🥈";
                case 2 -> "🥉";
                default -> "🌸";
            };
            description.append("**#").append(i + 1).append("** ")
                    .append(medal).append(" ")
                    .append(name)
                    .append(" — niveau **")
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
