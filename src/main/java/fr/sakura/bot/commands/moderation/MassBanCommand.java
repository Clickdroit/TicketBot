package fr.sakura.bot.commands.moderation;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Commande pour bannir plusieurs utilisateurs en une seule fois (IDs séparés par des espaces).
 */
public class MassBanCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(MassBanCommand.class);
    private final ModerationLogListener moderationLogListener;

    public MassBanCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "massban";
    }

    @Override
    public String getCategory() {
        return "Modération";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Bannit plusieurs utilisateurs via leurs IDs")
                .addOptions(
                        new OptionData(OptionType.STRING, "ids", "Les IDs des utilisateurs à bannir (séparés par des espaces)", true),
                        new OptionData(OptionType.STRING, "raison", "La raison du bannissement collectif", false)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;

        String idsString = event.getOption("ids", OptionMapping::getAsString);
        String reason = event.getOption("raison", "Massban (Raid/Abus)", OptionMapping::getAsString);

        if (idsString == null || idsString.isBlank()) {
            event.reply("❌ Aucun ID fourni.").setEphemeral(true).queue();
            return;
        }

        String[] ids = idsString.split("\\s+");
        event.deferReply().queue();

        int successCount = 0;
        int failCount = 0;

        for (String id : ids) {
            try {
                event.getGuild().ban(UserSnowflake.fromId(id), 0, TimeUnit.DAYS).reason(reason).complete();
                successCount++;
            } catch (Exception e) {
                logger.error("Erreur lors du massban pour l'ID {}", id, e);
                failCount++;
            }
        }

        String message = "✅ Massban terminé.\n" +
                "• **Succès :** " + successCount + "\n" +
                "• **Échecs :** " + failCount;
        
        event.getHook().sendMessage(message).queue();

        moderationLogListener.logAction(
                event.getGuild(),
                "MASSBAN",
                event.getMember(),
                reason,
                "Utilisateurs bannis : " + successCount + " (Échecs : " + failCount + ")"
        );
    }
}
