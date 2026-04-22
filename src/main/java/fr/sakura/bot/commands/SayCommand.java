package fr.sakura.bot.commands;

import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SayCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(SayCommand.class);
    private final ModerationLogListener moderationLogListener;

    public SayCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "say";
    }

    @Override
    public String getCategory() {
        return "Staff & Divers";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Fait parler le bot dans le salon")
                .addOptions(new OptionData(OptionType.STRING, "message", "Message à envoyer", true).setMaxLength(1800))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String message = event.getOption("message", "", OptionMapping::getAsString).trim();
        if (message.isBlank()) {
            event.reply("❌ Le message ne peut pas être vide.").setEphemeral(true).queue();
            return;
        }

        event.reply("✅ Message envoyé.").setEphemeral(true).queue();
        event.getChannel().sendMessage(message).queue(
                ok -> {
                    if (event.getGuild() != null) {
                        moderationLogListener.logAction(event.getGuild(), "SAY", event.getMember(), event.getUser(), "Message staff publié", "Salon: <#" + event.getChannel().getId() + ">\nContenu: " + message);
                    }
                },
                err -> {
                    logger.warn("Echec /say channelId={}", event.getChannel().getId(), err);
                }
        );
    }
}
