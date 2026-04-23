package fr.sakura.bot.commands.moderation;



import fr.sakura.bot.commands.ICommand;

import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

public class UnlockCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(UnlockCommand.class);
    private final ModerationLogListener moderationLogListener;

    public UnlockCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "unlock";
    }

    @Override
    public String getCategory() {
        return "ModÃƒÆ’Ã‚Â©ration";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "DÃƒÆ’Ã‚Â©verrouille le salon actuel")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || !(event.getChannel() instanceof TextChannel textChannel)) {
            event.reply("ÃƒÂ¢Ã‚ÂÃ…â€™ Cette commande fonctionne uniquement dans un salon texte serveur.").setEphemeral(true).queue();
            return;
        }

        textChannel.getManager().putPermissionOverride(event.getGuild().getPublicRole(), EnumSet.of(Permission.MESSAGE_SEND), null).queue(
                ok -> {
                    event.reply("ÃƒÂ°Ã…Â¸Ã¢â‚¬ÂÃ¢â‚¬Å“ Salon dÃƒÆ’Ã‚Â©verrouillÃƒÆ’Ã‚Â©.").queue();
                    moderationLogListener.logAction(event.getGuild(), "UNLOCK", event.getMember(), event.getUser(), "Salon dÃƒÆ’Ã‚Â©verrouillÃƒÆ’Ã‚Â©", "Salon: #" + textChannel.getName());
                },
                err -> {
                    logger.warn("Echec unlock channelId={}", textChannel.getId(), err);
                    event.reply("ÃƒÂ¢Ã‚ÂÃ…â€™ Impossible de dÃƒÆ’Ã‚Â©verrouiller le salon.").setEphemeral(true).queue();
                }
        );
    }
}
