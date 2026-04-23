package fr.sakura.bot.commands.moderation;



import fr.sakura.bot.commands.ICommand;

import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.Permission;
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

public class SlowmodeCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(SlowmodeCommand.class);
    private final ModerationLogListener moderationLogListener;

    public SlowmodeCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "slowmode";
    }

    @Override
    public String getCategory() {
        return "ModÃƒÆ’Ã‚Â©ration";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "RÃƒÆ’Ã‚Â¨gle le slowmode du salon (0-21600 secondes)")
                .addOptions(new OptionData(OptionType.INTEGER, "secondes", "DurÃƒÆ’Ã‚Â©e en secondes", true).setMinValue(0).setMaxValue(21600))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || !(event.getChannel() instanceof TextChannel textChannel)) {
            event.reply("ÃƒÂ¢Ã‚ÂÃ…â€™ Cette commande fonctionne uniquement dans un salon texte serveur.").setEphemeral(true).queue();
            return;
        }

        int seconds = event.getOption("secondes", 0, OptionMapping::getAsInt);
        textChannel.getManager().setSlowmode(seconds).queue(
                ok -> {
                    event.reply("ÃƒÂ¢Ã…â€œÃ¢â‚¬Â¦ Slowmode rÃƒÆ’Ã‚Â©glÃƒÆ’Ã‚Â© ÃƒÆ’Ã‚Â  **" + seconds + "** seconde(s)." ).queue();
                    moderationLogListener.logAction(event.getGuild(), "SLOWMODE", event.getMember(), event.getUser(), "Slowmode modifiÃƒÆ’Ã‚Â©", "Salon: #" + textChannel.getName() + " ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢ " + seconds + "s");
                },
                err -> {
                    logger.warn("Echec slowmode channelId={}, seconds={}", textChannel.getId(), seconds, err);
                    event.reply("ÃƒÂ¢Ã‚ÂÃ…â€™ Impossible de modifier le slowmode.").setEphemeral(true).queue();
                }
        );
    }
}
