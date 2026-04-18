package fr.sakura.bot.commands;

import fr.sakura.bot.utils.ModerationLogger;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

public class LockCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(LockCommand.class);
    private final ModerationLogger moderationLogger;

    public LockCommand(ModerationLogger moderationLogger) {
        this.moderationLogger = moderationLogger;
    }

    @Override
    public String getName() {
        return "lock";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Verrouille le salon actuel (envoi de messages bloqué)")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || !(event.getChannel() instanceof TextChannel textChannel)) {
            event.reply("❌ Cette commande fonctionne uniquement dans un salon texte serveur.").setEphemeral(true).queue();
            return;
        }

        textChannel.getManager().putPermissionOverride(event.getGuild().getPublicRole(), null, EnumSet.of(Permission.MESSAGE_SEND)).queue(
                ok -> {
                    event.reply("🔒 Salon verrouillé.").queue();
                    moderationLogger.logInGuild(event.getGuild(), "LOCK", event.getMember(), null, "Salon verrouillé", "Salon: #" + textChannel.getName());
                },
                err -> {
                    logger.warn("Echec lock channelId={}", textChannel.getId(), err);
                    event.reply("❌ Impossible de verrouiller le salon.").setEphemeral(true).queue();
                }
        );
    }
}
