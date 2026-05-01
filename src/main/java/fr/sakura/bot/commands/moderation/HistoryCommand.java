package fr.sakura.bot.commands.moderation;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.model.StaffNoteEntry;
import fr.sakura.bot.core.model.WarningEntry;
import fr.sakura.bot.core.store.StaffNoteStore;
import fr.sakura.bot.core.store.WarningStore;
import fr.sakura.bot.core.util.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/**
 * Commande affichant l'historique complet des sanctions d'un membre.
 */
public class HistoryCommand implements ICommand {

    private final WarningStore warningStore;
    private final StaffNoteStore staffNoteStore;

    public HistoryCommand(WarningStore warningStore, StaffNoteStore staffNoteStore) {
        this.warningStore = warningStore;
        this.staffNoteStore = staffNoteStore;
    }

    @Override
    public String getName() {
        return "history";
    }

    @Override
    public String getCategory() {
        return "Modération";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche l'historique des sanctions d'un membre")
                .addOptions(new OptionData(OptionType.USER, "membre", "Le membre à inspecter", true))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;

        User targetUser = event.getOption("membre", OptionMapping::getAsUser);
        if (targetUser == null) return;

        event.deferReply().queue();

        List<WarningEntry> warnings = warningStore.getWarnings(event.getGuild().getId(), targetUser.getId());
        List<StaffNoteEntry> notes = staffNoteStore.getNotes(event.getGuild().getId(), targetUser.getId());

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("📜", "Historique de " + targetUser.getName());
        embed.setThumbnail(targetUser.getEffectiveAvatarUrl());

        // Warnings
        StringBuilder warnsSb = new StringBuilder();
        if (warnings.isEmpty()) {
            warnsSb.append("*Aucun avertissement.*");
        } else {
            for (WarningEntry w : warnings) {
                String time = "<t:" + java.time.OffsetDateTime.parse(w.timestamp()).toEpochSecond() + ":d>";
                warnsSb.append("• [`#").append(w.id()).append("`] ").append(time).append(" : ").append(w.reason()).append("\n");
            }
        }
        embed.addField("⚠️ Avertissements (" + warnings.size() + ")", warnsSb.toString(), false);

        // Staff Notes
        StringBuilder notesSb = new StringBuilder();
        if (notes.isEmpty()) {
            notesSb.append("*Aucune note.*");
        } else {
            for (StaffNoteEntry n : notes) {
                String time = "<t:" + java.time.Instant.parse(n.createdAt()).getEpochSecond() + ":d>";
                notesSb.append("• ").append(time).append(" par <@").append(n.authorId()).append("> : ").append(n.content()).append("\n");
            }
        }
        embed.addField("📝 Notes du staff", notesSb.toString(), false);

        // On pourrait aussi ajouter les bans passés si on les loggait en base.
        
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
}
