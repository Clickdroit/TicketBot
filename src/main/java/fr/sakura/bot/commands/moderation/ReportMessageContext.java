package fr.sakura.bot.commands.moderation;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class ReportMessageContext implements ICommand {

    private final ModerationLogListener moderationLogListener;

    public ReportMessageContext(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "Signaler ce message";
    }

    @Override
    public CommandData getCommandData() {
        return Commands.message(getName());
    }

    @Override
    public void onMessageContext(MessageContextInteractionEvent event) {
        Message target = event.getTarget();
        
        moderationLogListener.logAction(
                event.getGuild(),
                "REPORT",
                event.getMember(),
                target.getAuthor(),
                "Message signalé par un utilisateur",
                "Salon: <#" + target.getChannel().getId() + ">\nContenu: " + target.getContentRaw()
        );

        event.reply("✅ Merci pour votre signalement. L'équipe de modération a été prévenue.").setEphemeral(true).queue();
    }
}
