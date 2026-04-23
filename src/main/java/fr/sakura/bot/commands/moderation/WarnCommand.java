package fr.sakura.bot.commands.moderation;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.service.WarningService;
import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Commande pour avertir un membre, avec escalade automatique en timeout.
 */
public class WarnCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(WarnCommand.class);

    private final ModerationLogListener moderationLogListener;
    private final WarningService warningService;
    private final SettingsManager settingsManager;

    public WarnCommand(ModerationLogListener moderationLogListener, WarningService warningService, SettingsManager settingsManager) {
        this.moderationLogListener = moderationLogListener;
        this.warningService = warningService;
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
        return Commands.slash(getName(), "Ajoute un avertissement à un membre")
                .addOptions(
                        new OptionData(OptionType.USER, "membre", "Le membre à avertir", true),
                        new OptionData(OptionType.STRING, "raison", "La raison de l'avertissement", true).setMaxLength(500)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member target = event.getOption("membre", OptionMapping::getAsMember);
        String reason = event.getOption("raison", OptionMapping::getAsString);

        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("❌ Cette commande doit être utilisée dans un serveur.").setEphemeral(true).queue();
            return;
        }

        if (target == null || reason == null || reason.isBlank()) {
            event.reply("❌ Paramètres invalides.").setEphemeral(true).queue();
            return;
        }

        if (target.getUser().isBot()) {
            event.reply("❌ Vous ne pouvez pas avertir un bot.").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().canInteract(target)) {
            event.reply("❌ Vous ne pouvez pas avertir cet utilisateur (hiérarchie supérieure).").setEphemeral(true).queue();
            return;
        }

        int totalWarnings = warningService.addWarning(
                event.getGuild().getId(),
                target.getId(),
                event.getMember().getId(),
                reason
        );

        event.reply("✅ **" + target.getUser().getName() + "** a reçu un avertissement. Total : " + totalWarnings).queue();
        
        moderationLogListener.logAction(
                event.getGuild(),
                "WARN",
                event.getMember(),
                target,
                reason,
                "Total warnings : " + totalWarnings
        );

        // Escalade AutoMod
        checkEscalation(event, target, totalWarnings);
    }

    private void checkEscalation(SlashCommandInteractionEvent event, Member target, int totalWarnings) {
        String guildId = event.getGuild().getId();
        int threshold = settingsManager.getAutomodStrikesToTimeout(guildId);
        int timeoutMins = settingsManager.getAutomodTimeoutMinutes(guildId);

        if (threshold > 0 && totalWarnings >= threshold) {
            if (!event.getGuild().getSelfMember().hasPermission(Permission.MODERATE_MEMBERS) || !event.getGuild().getSelfMember().canInteract(target)) {
                logger.warn("Escalade échouée : permissions manquantes pour timeout targetId={} guildId={}", target.getId(), guildId);
                return;
            }

            target.timeoutFor(Duration.ofMinutes(timeoutMins)).reason("Escalade automatique Sakura (Seuil: " + threshold + " warns)").queue(
                    ok -> {
                        event.getChannel().sendMessage("⏳ Escalade AutoMod : **" + target.getUser().getName() + "** a été réduit au silence pendant " + timeoutMins + " minutes.").queue();
                        moderationLogListener.logAction(
                                event.getGuild(),
                                "TIMEOUT",
                                null, // Auto
                                target,
                                "Escalade automatique (" + totalWarnings + " avertissements)",
                                "Durée : " + timeoutMins + " minutes"
                        );
                    },
                    err -> logger.error("Erreur lors du timeout d'escalade targetId={}", target.getId(), err)
            );
        }
    }
}
