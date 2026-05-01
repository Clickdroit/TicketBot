package fr.sakura.bot.commands.staff;

import fr.sakura.bot.commands.ICommand;
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

/**
 * Commande pour envoyer un message privé à un membre via le bot.
 */
public class DmCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(DmCommand.class);
    private final ModerationLogListener moderationLogListener;

    public DmCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "dm";
    }

    @Override
    public String getCategory() {
        return "Staff & Divers";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Envoie un message privé à un membre via le bot")
                .addOptions(
                        new OptionData(OptionType.USER, "membre", "Le destinataire", true),
                        new OptionData(OptionType.STRING, "message", "Le message à envoyer", true).setMaxLength(1900)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member target = event.getOption("membre", OptionMapping::getAsMember);
        String message = event.getOption("message", OptionMapping::getAsString);

        if (target == null || message == null) return;

        if (target.getUser().isBot()) {
            event.reply("❌ Impossible d'envoyer un DM à un bot.").setEphemeral(true).queue();
            return;
        }

        target.getUser().openPrivateChannel().queue(
                channel -> channel.sendMessage(message).queue(
                        success -> {
                            event.reply("✅ Message privé envoyé à **" + target.getUser().getName() + "**.").setEphemeral(true).queue();
                            moderationLogListener.logAction(event.getGuild(), "STAFF_DM", event.getMember(), target, "Message privé envoyé", "Contenu: " + message);
                        },
                        error -> {
                            logger.warn("Impossible d'envoyer un DM à {} (DMs fermés ?)", target.getId());
                            event.reply("❌ Impossible d'envoyer le message (DMs fermés ou utilisateur non accessible).").setEphemeral(true).queue();
                        }
                ),
                error -> {
                    logger.error("Erreur ouverture canal privé", error);
                    event.reply("❌ Une erreur est survenue lors de l'envoi.").setEphemeral(true).queue();
                }
        );
    }
}
