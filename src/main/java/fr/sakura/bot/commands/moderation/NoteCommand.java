package fr.sakura.bot.commands.moderation;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.model.StaffNoteEntry;
import fr.sakura.bot.core.service.StaffNoteService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public class NoteCommand implements ICommand {

    private final StaffNoteService staffNoteService;

    public NoteCommand(StaffNoteService staffNoteService) {
        this.staffNoteService = staffNoteService;
    }

    @Override
    public String getName() {
        return "note";
    }

    @Override
    public String getCategory() {
        return "Modération";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Gère les notes internes sur les membres")
                .addSubcommands(
                        new SubcommandData("add", "Ajouter une note")
                                .addOption(OptionType.USER, "membre", "Le membre concerné", true)
                                .addOption(OptionType.STRING, "contenu", "Le contenu de la note", true),
                        new SubcommandData("list", "Lister les notes d'un membre")
                                .addOption(OptionType.USER, "membre", "Le membre concerné", true),
                        new SubcommandData("delete", "Supprimer une note")
                                .addOption(OptionType.INTEGER, "id", "L'identifiant de la note", true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        switch (subcommand) {
            case "add" -> handleAdd(event);
            case "list" -> handleList(event);
            case "delete" -> handleDelete(event);
        }
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        User target = event.getOption("membre").getAsUser();
        String content = event.getOption("contenu").getAsString();

        staffNoteService.addNote(event.getGuild().getId(), target.getId(), event.getUser().getId(), content);
        event.reply("✅ Note ajoutée pour **" + target.getName() + "**.").setEphemeral(true).queue();
    }

    private void handleList(SlashCommandInteractionEvent event) {
        User target = event.getOption("membre").getAsUser();
        List<StaffNoteEntry> notes = staffNoteService.getNotes(event.getGuild().getId(), target.getId());

        if (notes.isEmpty()) {
            event.reply("ℹ️ Aucune note pour **" + target.getName() + "**.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Notes internes : " + target.getName())
                .setColor(Color.ORANGE)
                .setTimestamp(Instant.now());

        for (StaffNoteEntry note : notes) {
            String authorMention = "<@" + note.authorId() + ">";
            eb.addField("ID: " + note.id() + " | Par " + authorMention, 
                    note.content() + "\n*Le " + note.createdAt().substring(0, 10) + "*", false);
        }

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    private void handleDelete(SlashCommandInteractionEvent event) {
        long noteId = event.getOption("id").getAsLong();
        boolean deleted = staffNoteService.deleteNote(event.getGuild().getId(), noteId);

        if (deleted) {
            event.reply("✅ Note **#" + noteId + "** supprimée.").setEphemeral(true).queue();
        } else {
            event.reply("❌ Note introuvable ou ID invalide.").setEphemeral(true).queue();
        }
    }
}
