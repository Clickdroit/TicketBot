package fr.sakura.bot.commands;

import fr.sakura.bot.utils.ModerationLogger;
import fr.sakura.bot.utils.WarningService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class WarnCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(WarnCommand.class);

    private final ModerationLogger moderationLogger;
    private final WarningService warningService;

    public WarnCommand(ModerationLogger moderationLogger, WarningService warningService) {
        this.moderationLogger = moderationLogger;
        this.warningService = warningService;
    }

    @Override
    public String getName() {
        return "warn";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Ajoute un avertissement a un membre")
                .addOptions(
                        new OptionData(OptionType.USER, "membre", "Le membre a avertir", true),
                        new OptionData(OptionType.STRING, "raison", "La raison de l'avertissement", true).setMaxLength(500)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        logger.debug("Execution /warn par userId={}", event.getUser().getId());
        Member target = event.getOption("membre", OptionMapping::getAsMember);
        String reason = event.getOption("raison", OptionMapping::getAsString);

        if (event.getGuild() == null || event.getMember() == null) {
            logger.warn("/warn appelee hors serveur ou member null userId={}", event.getUser().getId());
            event.reply("❌ Cette commande doit etre utilisee dans un serveur.").setEphemeral(true).queue();
            return;
        }

        if (target == null || reason == null || reason.isBlank()) {
            logger.warn("/warn invalide: target/reason manquant modId={}", event.getUser().getId());
            event.reply("❌ Parametres invalides.").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().canInteract(target)) {
            logger.warn("/warn refuse hierarchie: modId={}, targetId={}", event.getUser().getId(), target.getId());
            event.reply("❌ Vous ne pouvez pas avertir cet utilisateur (role superieur).")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        logger.info("/warn demande: modId={}, targetId={}, reasonLength={}",
                event.getUser().getId(), target.getId(), reason.length());

        try {
            int totalWarnings = warningService.addWarning(
                    event.getGuild().getId(),
                    target.getId(),
                    event.getMember().getId(),
                    reason
            );

            event.reply("✅ **" + target.getUser().getName() + "** a recu un avertissement. Total: " + totalWarnings).queue();
            logger.info("/warn reussi: modId={}, targetId={}, total={}", event.getUser().getId(), target.getId(), totalWarnings);

            if (moderationLogger.isEnabled()) {
                TextChannel logChannel = event.getGuild().getTextChannelById(moderationLogger.getLogChannelId());
                moderationLogger.log(
                        logChannel,
                        "WARN",
                        event.getMember(),
                        target,
                        reason,
                        "Total warnings: " + totalWarnings
                );
            }
        } catch (IOException ex) {
            logger.error("/warn echec JSON: modId={}, targetId={}", event.getUser().getId(), target.getId(), ex);
            event.reply("❌ Impossible d'enregistrer le warning (erreur JSON).")
                    .setEphemeral(true)
                    .queue();
        }
    }
}

