package fr.sakura.bot.commands.moderation;



import fr.sakura.bot.commands.ICommand;

import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.Permission;
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

    private final ModerationLogListener moderationLogListener;

    public ClearCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "clear";
    }

    @Override
    public String getCategory() {
        return "ModÃƒÆ’Ã‚Â©ration";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Supprime un certain nombre de messages rÃƒÆ’Ã‚Â©cents")
                .addOptions(new OptionData(OptionType.INTEGER, "montant", "Le nombre de messages ÃƒÆ’Ã‚Â  supprimer (1-100)", true)
                        .setMinValue(1).setMaxValue(100))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        logger.debug("Execution /clear par userId={}", event.getUser().getId());

        var amountOption = event.getOption("montant");
        if (amountOption == null) {
            logger.warn("/clear invalide: montant absent userId={}", event.getUser().getId());
            event.reply("ÃƒÂ¢Ã‚ÂÃ…â€™ Le montant est requis.").setEphemeral(true).queue();
            return;
        }

        int amount = amountOption.getAsInt();
        logger.info("/clear demande amount={} par userId={} channelId={}", amount, event.getUser().getId(), event.getChannel().getId());

        // Defer immÃƒÆ’Ã‚Â©diat pour ÃƒÆ’Ã‚Â©viter l'expiration des 3 secondes pendant le fetchAsync
        event.deferReply(true).queue();

        event.getChannel().getIterableHistory().takeAsync(amount).thenAccept(messages -> {
            if (messages.isEmpty()) {
                logger.info("/clear aucun message a supprimer userId={} channelId={}", event.getUser().getId(), event.getChannel().getId());
                event.getHook().sendMessage("ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¹ÃƒÂ¯Ã‚Â¸Ã‚Â Aucun message ÃƒÆ’Ã‚Â  supprimer.").queue();
                return;
            }

            try {
                event.getChannel().purgeMessages(messages);
                event.getHook().sendMessage("ÃƒÂ¢Ã…â€œÃ¢â‚¬Â¦ " + messages.size() + " message(s) supprimÃƒÆ’Ã‚Â©(s) !").queue();
                logger.info("/clear reussi: deleted={} userId={} channelId={}", messages.size(), event.getUser().getId(), event.getChannel().getId());

                moderationLogListener.logAction(
                        event.getGuild(),
                        "CLEAR",
                        event.getMember(),
                        event.getUser(), // Utilise l'auteur de la commande comme "cible" pour le log si pas d'autre cible
                        "Nettoyage de salon",
                        messages.size() + " message(s) supprimÃƒÆ’Ã‚Â©(s)"
                );
            } catch (IllegalArgumentException e) {
                logger.warn("/clear impossible (messages >14 jours): userId={}, amount={}", event.getUser().getId(), amount, e);
                event.getHook().sendMessage("ÃƒÂ¢Ã‚ÂÃ…â€™ Impossible de supprimer ces messages (ils datent de plus de 14 jours).").queue();
            }
        }).exceptionally(error -> {
            logger.error("/clear echec recuperation historique: userId={}, amount={}", event.getUser().getId(), amount, error);
            event.getHook().sendMessage("ÃƒÂ¢Ã‚ÂÃ…â€™ Une erreur est survenue pendant la suppression.").queue();
            return null;
        });
    }
}
