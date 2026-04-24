package fr.sakura.bot.commands.xp;



import fr.sakura.bot.commands.ICommand;

import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.core.model.LevelProfile;
import fr.sakura.bot.core.service.LevelService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LeaderboardCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(LeaderboardCommand.class);
    private static final int PAGE_SIZE = 10;
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
        return Commands.slash(getName(), "Affiche le classement XP du serveur")
                .addOptions(new OptionData(OptionType.INTEGER, "page", "Numéro de la page", false).setMinValue(1));
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

        int page = event.getOption("page", 1, OptionMapping::getAsInt);
        int offset = (page - 1) * PAGE_SIZE;

        List<LevelProfile> profiles = levelService.getLeaderboard(event.getGuild().getId(), PAGE_SIZE, offset);
        if (profiles.isEmpty()) {
            if (page == 1) {
                event.reply("ℹ️ Aucun membre n'a encore gagné d'XP.").setEphemeral(true).queue();
            } else {
                event.reply("❌ Cette page du classement est vide.").setEphemeral(true).queue();
            }
            return;
        }

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("📊", "Leaderboard XP - Page " + page);
        StringBuilder description = new StringBuilder();

        for (int i = 0; i < profiles.size(); i++) {
            LevelProfile profile = profiles.get(i);
            int rank = offset + i + 1;
            Member member = event.getGuild().getMemberById(profile.userId());
            String name = member != null ? member.getEffectiveName() : "<@" + profile.userId() + ">";
            
            String medal = switch (rank) {
                case 1 -> "🥇";
                case 2 -> "🥈";
                case 3 -> "🥉";
                default -> "🔹";
            };
            
            description.append("**#").append(rank).append("** ")
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
        EmbedStyle.setFooter(embed, "Top du serveur • Page " + page);
        event.replyEmbeds(embed.build()).queue();
        logger.info("/leaderboard page {} envoye guildId={}, requesterId={}", page, event.getGuild().getId(), event.getUser().getId());
    }
}
