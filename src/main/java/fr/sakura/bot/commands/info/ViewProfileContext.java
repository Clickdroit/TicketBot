package fr.sakura.bot.commands.info;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.service.LevelService;
import fr.sakura.bot.core.service.WarningService;
import fr.sakura.bot.core.util.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class ViewProfileContext implements ICommand {

    private final LevelService levelService;
    private final WarningService warningService;

    public ViewProfileContext(LevelService levelService, WarningService warningService) {
        this.levelService = levelService;
        this.warningService = warningService;
    }

    @Override
    public String getName() {
        return "Voir le profil";
    }

    @Override
    public CommandData getCommandData() {
        return Commands.user(getName());
    }

    @Override
    public void onUserContext(UserContextInteractionEvent event) {
        Member target = event.getTargetMember();
        if (target == null) {
            event.reply("❌ Impossible de trouver les informations de cet utilisateur.").setEphemeral(true).queue();
            return;
        }

        var profile = levelService.getProfile(event.getGuild().getId(), target.getId());
        var warnings = warningService.getWarnings(event.getGuild().getId(), target.getId());

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("🌸", "Profil de " + target.getEffectiveName());
        embed.setThumbnail(target.getEffectiveAvatarUrl());

        embed.addField("📊 Niveaux", 
                EmbedStyle.bullet("Niveau : **" + profile.level() + "**\n") +
                EmbedStyle.bullet("XP : **" + profile.xp() + "**"), true);

        embed.addField("⚠️ Sanctions", 
                EmbedStyle.bullet("Avertissements : **" + warnings.size() + "**"), true);

        if (!warnings.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            warnings.stream().limit(3).forEach(w -> 
                sb.append(EmbedStyle.bullet(w.reason() + " (par <@" + w.moderatorId() + ">)\n"))
            );
            embed.addField("Derniers warns", sb.toString(), false);
        }

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
