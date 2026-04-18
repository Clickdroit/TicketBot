package fr.sakura.bot.commands;

import fr.sakura.bot.utils.ModerationLogger;
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
    private final ModerationLogger moderationLogger;

    public SlowmodeCommand(ModerationLogger moderationLogger) {
        this.moderationLogger = moderationLogger;
    }

    @Override
    public String getName() {
        return "slowmode";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Règle le slowmode du salon (0-21600 secondes)")
                .addOptions(new OptionData(OptionType.INTEGER, "secondes", "Durée en secondes", true).setMinValue(0).setMaxValue(21600))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || !(event.getChannel() instanceof TextChannel textChannel)) {
            event.reply("❌ Cette commande fonctionne uniquement dans un salon texte serveur.").setEphemeral(true).queue();
            return;
        }

        int seconds = event.getOption("secondes", 0, OptionMapping::getAsInt);
        textChannel.getManager().setSlowmode(seconds).queue(
                ok -> {
                    event.reply("✅ Slowmode réglé à **" + seconds + "** seconde(s)." ).queue();
                    moderationLogger.logInGuild(event.getGuild(), "SLOWMODE", event.getMember(), null, "Slowmode modifié", "Salon: #" + textChannel.getName() + " • " + seconds + "s");
                },
                err -> {
                    logger.warn("Echec slowmode channelId={}, seconds={}", textChannel.getId(), seconds, err);
                    event.reply("❌ Impossible de modifier le slowmode.").setEphemeral(true).queue();
                }
        );
    }
}
