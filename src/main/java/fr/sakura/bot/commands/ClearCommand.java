package fr.sakura.bot.commands;

import fr.sakura.bot.utils.ModerationLogger;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClearCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(ClearCommand.class);

    private final ModerationLogger moderationLogger;

    public ClearCommand(ModerationLogger moderationLogger) {
        this.moderationLogger = moderationLogger;
    }

    @Override
    public String getName() {
        return "clear";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Supprime un certain nombre de messages récents")
                .addOptions(new OptionData(OptionType.INTEGER, "montant", "Le nombre de messages à supprimer (1-100)", true)
                        .setMinValue(1).setMaxValue(100))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        logger.debug("Execution /clear par userId={}", event.getUser().getId());
        if (event.getOption("montant") == null) {
            logger.warn("/clear invalide: montant absent userId={}", event.getUser().getId());
            event.reply("❌ Le montant est requis.").setEphemeral(true).queue();
            return;
        }

        int amount = event.getOption("montant").getAsInt();
        logger.info("/clear demande amount={} par userId={} channelId={}", amount, event.getUser().getId(), event.getChannel().getId());
        
        event.getChannel().getIterableHistory().takeAsync(amount).thenAccept(messages -> {
            try {
                event.getChannel().purgeMessages(messages);
                event.reply("✅ " + messages.size() + " messages supprimés !").setEphemeral(true).queue();
                logger.info("/clear reussi: deleted={} userId={} channelId={}", messages.size(), event.getUser().getId(), event.getChannel().getId());

                if (event.getGuild() != null && moderationLogger.isEnabled()) {
                    TextChannel logChannel = event.getGuild().getTextChannelById(moderationLogger.getLogChannelId());
                    moderationLogger.log(
                            logChannel,
                            "CLEAR",
                            event.getMember(),
                            null,
                            "Nettoyage de salon",
                            messages.size() + " message(s) supprimé(s)"
                    );
                }
            } catch (IllegalArgumentException e) {
                logger.warn("/clear impossible (messages >14 jours probablement): userId={}, amount={}", event.getUser().getId(), amount, e);
                event.reply("❌ Impossible de supprimer ces messages (ils datent probablement de plus de 14 jours).").setEphemeral(true).queue();
            }
        }).exceptionally(error -> {
            logger.error("/clear echec recuperation historique: userId={}, amount={}", event.getUser().getId(), amount, error);
            event.reply("❌ Une erreur est survenue pendant la suppression.").setEphemeral(true).queue();
            return null;
        });
    }
}
