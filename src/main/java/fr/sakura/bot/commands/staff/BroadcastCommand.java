package fr.sakura.bot.commands.staff;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
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
 * Commande pour envoyer un message dans plusieurs salons à la fois.
 */
public class BroadcastCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(BroadcastCommand.class);
    private final ModerationLogListener moderationLogListener;

    public BroadcastCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "broadcast";
    }

    @Override
    public String getCategory() {
        return "Staff & Divers";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Envoie un message dans plusieurs salons simultanément")
                .addOptions(
                        new OptionData(OptionType.STRING, "message", "Le message à diffuser", true).setMaxLength(1900),
                        new OptionData(OptionType.STRING, "salons", "Les IDs des salons (séparés par des espaces)", true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;

        String message = event.getOption("message", OptionMapping::getAsString);
        String salonsString = event.getOption("salons", OptionMapping::getAsString);

        if (message == null || salonsString == null) return;

        String[] channelIds = salonsString.split("\\s+");
        event.deferReply().queue();

        int successCount = 0;
        int failCount = 0;

        for (String id : channelIds) {
            try {
                GuildMessageChannel channel = event.getGuild().getChannelById(GuildMessageChannel.class, id);
                if (channel != null) {
                    channel.sendMessage(message).complete();
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                logger.error("Erreur broadcast dans le salon {}", id, e);
                failCount++;
            }
        }

        event.getHook().sendMessage("✅ Diffusion terminée.\n" +
                "• **Succès :** " + successCount + "\n" +
                "• **Échecs :** " + failCount).queue();

        moderationLogListener.logAction(event.getGuild(), "BROADCAST", event.getMember(), "Diffusion collective", "Salons: " + successCount + " (Échecs: " + failCount + ")");
    }
}
