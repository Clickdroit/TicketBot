package fr.sakura.bot.commands.moderation;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.model.StaffNoteEntry;
import fr.sakura.bot.core.model.WarningEntry;
import fr.sakura.bot.core.service.StaffNoteService;
import fr.sakura.bot.core.service.WarningService;
import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.listeners.log.ModerationLogListener;
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
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Commande centralisée pour les avertissements et les notes du staff.
 */
public class WarnCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(WarnCommand.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    private final ModerationLogListener moderationLogListener;
    private final WarningService warningService;
    private final StaffNoteService staffNoteService;
    private final SettingsManager settingsManager;

    public WarnCommand(ModerationLogListener moderationLogListener, WarningService warningService, StaffNoteService staffNoteService, SettingsManager settingsManager) {
        this.moderationLogListener = moderationLogListener;
        this.warningService = warningService;
        this.staffNoteService = staffNoteService;
        this.settingsManager = settingsManager;
    }

    @Override
    public String getName() {
        return "warn";
    }

    @Override
    public String getCategory() {
        return "Modération";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Gère les avertissements et notes du staff")
                .addSubcommands(
                        new SubcommandData("add", "Ajoute un avertissement à un membre")
                                .addOptions(
                                        new OptionData(OptionType.USER, "membre", "Le membre à avertir", true),
                                        new OptionData(OptionType.STRING, "raison", "La raison de l'avertissement", true).setMaxLength(500)
                                ),
                        new SubcommandData("remove", "Supprime un avertissement précis par son ID")
                                .addOptions(new OptionData(OptionType.INTEGER, "id", "L'ID de l'avertissement", true)),
                        new SubcommandData("clear", "Supprime tous les avertissements d'un membre")
                                .addOptions(new OptionData(OptionType.USER, "membre", "Le membre cible", true)),
                        new SubcommandData("list", "Affiche les avertissements d'un membre")
                                .addOptions(new OptionData(OptionType.USER, "membre", "Le membre cible", true)),
                        new SubcommandData("history", "Affiche l'historique complet (warns + notes)")
                                .addOptions(new OptionData(OptionType.USER, "membre", "Le membre cible", true)),
                        new SubcommandData("note-add", "Ajoute une note interne sur un membre")
                                .addOptions(
                                        new OptionData(OptionType.USER, "membre", "Le membre concerné", true),
                                        new OptionData(OptionType.STRING, "contenu", "Le contenu de la note", true)
                                ),
                        new SubcommandData("note-remove", "Supprime une note par son ID")
                                .addOptions(new OptionData(OptionType.INTEGER, "id", "L'ID de la note", true))
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        if (event.getGuild() == null) {
            event.reply("❌ Cette commande doit être utilisée dans un serveur.").setEphemeral(true).queue();
            return;
        }

        switch (subcommand) {
            case "add" -> handleAdd(event);
            case "remove" -> handleRemove(event);
            case "clear" -> handleClear(event);
            case "list" -> handleList(event);
            case "history" -> handleHistory(event);
            case "note-add" -> handleNoteAdd(event);
            case "note-remove" -> handleNoteRemove(event);
        }
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        Member target = event.getOption("membre", OptionMapping::getAsMember);
        String reason = event.getOption("raison", OptionMapping::getAsString);

        if (target == null || target.getUser().isBot()) {
            event.reply("❌ Membre invalide ou robot.").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().canInteract(target)) {
            event.reply("❌ Hiérarchie supérieure.").setEphemeral(true).queue();
            return;
        }

        int totalWarnings = warningService.addWarning(event.getGuild().getId(), target.getId(), event.getMember().getId(), reason);
        event.reply("✅ **" + target.getUser().getName() + "** a reçu un avertissement. Total : " + totalWarnings).queue();
        
        moderationLogListener.logAction(event.getGuild(), "WARN", event.getMember(), target, reason, "Total : " + totalWarnings);
        checkEscalation(event, target, totalWarnings);
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        long id = event.getOption("id", 0L, OptionMapping::getAsLong);
        if (warningService.removeWarning(event.getGuild().getId(), id)) {
            event.reply("✅ Avertissement n°" + id + " supprimé.").queue();
            moderationLogListener.logAction(event.getGuild(), "UNWARN", event.getMember(), "Suppression warn #" + id, null);
        } else {
            event.reply("❌ Avertissement introuvable.").setEphemeral(true).queue();
        }
    }

    private void handleClear(SlashCommandInteractionEvent event) {
        Member target = event.getOption("membre", OptionMapping::getAsMember);
        if (target == null) return;

        int removed = warningService.clearWarnings(event.getGuild().getId(), target.getId());
        event.reply("✅ " + removed + " avertissement(s) supprimé(s) pour **" + target.getUser().getName() + "**.").queue();
        moderationLogListener.logAction(event.getGuild(), "CLEARWARN", event.getMember(), target, "Reset total", removed + " retirés");
    }

    private void handleList(SlashCommandInteractionEvent event) {
        Member target = event.getOption("membre", OptionMapping::getAsMember);
        if (target == null) return;

        List<WarningEntry> warnings = warningService.getWarnings(event.getGuild().getId(), target.getId());
        if (warnings.isEmpty()) {
            event.reply("ℹ️ Aucun avertissement pour **" + target.getUser().getName() + "**.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = EmbedStyle.newInfoEmbed("⚠️", "Avertissements : " + target.getUser().getName());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(warnings.size(), 10); i++) {
            WarningEntry w = warnings.get(i);
            String date = w.timestamp().substring(0, 10);
            sb.append("**#").append(w.id()).append("** | ").append(date).append(" : ").append(w.reason()).append("\n");
        }
        eb.setDescription(sb.toString());
        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    private void handleHistory(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("membre", OptionMapping::getAsUser);
        if (targetUser == null) return;

        List<WarningEntry> warnings = warningService.getWarnings(event.getGuild().getId(), targetUser.getId());
        List<StaffNoteEntry> notes = staffNoteService.getNotes(event.getGuild().getId(), targetUser.getId());

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("📜", "Dossier : " + targetUser.getName());
        embed.setThumbnail(targetUser.getEffectiveAvatarUrl());

        StringBuilder warnsSb = new StringBuilder();
        if (warnings.isEmpty()) warnsSb.append("*Aucun.*");
        else warnings.forEach(w -> warnsSb.append("• [`#").append(w.id()).append("`] ").append(w.reason()).append("\n"));
        embed.addField("⚠️ Avertissements (" + warnings.size() + ")", warnsSb.toString(), false);

        StringBuilder notesSb = new StringBuilder();
        if (notes.isEmpty()) notesSb.append("*Aucune.*");
        else notes.forEach(n -> notesSb.append("• ").append(n.content()).append(" (par <@").append(n.authorId()).append(">)\n"));
        embed.addField("📝 Notes du staff", notesSb.toString(), false);

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleNoteAdd(SlashCommandInteractionEvent event) {
        User target = event.getOption("membre").getAsUser();
        String content = event.getOption("contenu").getAsString();
        staffNoteService.addNote(event.getGuild().getId(), target.getId(), event.getUser().getId(), content);
        event.reply("✅ Note ajoutée pour **" + target.getName() + "**.").setEphemeral(true).queue();
    }

    private void handleNoteRemove(SlashCommandInteractionEvent event) {
        long id = event.getOption("id").getAsLong();
        if (staffNoteService.deleteNote(event.getGuild().getId(), id)) {
            event.reply("✅ Note #" + id + " supprimée.").setEphemeral(true).queue();
        } else {
            event.reply("❌ Note introuvable.").setEphemeral(true).queue();
        }
    }

    private void checkEscalation(SlashCommandInteractionEvent event, Member target, int totalWarnings) {
        int threshold = settingsManager.getAutomodStrikesToTimeout(event.getGuild().getId());
        int timeoutMins = settingsManager.getAutomodTimeoutMinutes(event.getGuild().getId());

        if (threshold > 0 && totalWarnings >= threshold) {
            target.timeoutFor(Duration.ofMinutes(timeoutMins)).reason("Escalade Sakura (" + threshold + " warns)").queue(
                    ok -> moderationLogListener.logAction(event.getGuild(), "TIMEOUT", null, target, "Escalade (" + totalWarnings + " warns)", timeoutMins + " min"),
                    err -> logger.error("Escalade échouée", err)
            );
        }
    }
}

