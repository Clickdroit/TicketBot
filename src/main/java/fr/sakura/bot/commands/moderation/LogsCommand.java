package fr.sakura.bot.commands.moderation;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.model.WarningEntry;
import fr.sakura.bot.core.service.WarningService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public class LogsCommand implements ICommand {

    private final WarningService warningService;

    public LogsCommand(WarningService warningService) {
        this.warningService = warningService;
    }

    @Override
    public String getName() {
        return "logs";
    }

    @Override
    public String getCategory() {
        return "Modération";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche l'historique complet des sanctions d'un membre")
                .addOption(OptionType.USER, "membre", "Le membre concerné", true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;

        User target = event.getOption("membre").getAsUser();
        List<WarningEntry> warnings = warningService.getWarnings(event.getGuild().getId(), target.getId());

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("📋 Historique : " + target.getName())
                .setThumbnail(target.getEffectiveAvatarUrl())
                .setColor(warnings.isEmpty() ? Color.GREEN : Color.ORANGE)
                .setTimestamp(Instant.now());

        if (warnings.isEmpty()) {
            eb.setDescription("✅ Cet utilisateur n'a aucune sanction enregistrée.");
        } else {
            eb.setDescription("Retrouvez ci-dessous les " + Math.min(warnings.size(), 10) + " dernières sanctions.");
            
            // On limite à 10 pour l'embed, pagination à prévoir si nécessaire plus tard
            int count = 0;
            for (int i = warnings.size() - 1; i >= 0 && count < 10; i--) {
                WarningEntry w = warnings.get(i);
                String date = w.timestamp().length() > 10 ? w.timestamp().substring(0, 10) : w.timestamp();
                eb.addField("⚠️ Sanction #" + (i + 1) + " | " + date,
                        "> **Raison :** " + w.reason() + "\n> **Modérateur :** <@" + w.moderatorId() + ">", false);
                count++;
            }
            
            if (warnings.size() > 10) {
                eb.setFooter("Affichage des 10 dernières sanctions sur " + warnings.size());
            }
        }

        event.replyEmbeds(eb.build()).queue();
    }
}
