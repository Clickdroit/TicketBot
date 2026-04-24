package fr.sakura.bot.commands.moderation;



import fr.sakura.bot.commands.ICommand;

import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnbanCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(UnbanCommand.class);

    private final ModerationLogListener moderationLogListener;

    public UnbanCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "unban";
    }

    @Override
    public String getCategory() {
        return "Modération";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Retire le bannissement d'un utilisateur")
                .addOptions(
                        new OptionData(OptionType.STRING, "user_id", "ID utilisateur a debannir", true),
                        new OptionData(OptionType.STRING, "raison", "La raison du deban", false)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        logger.debug("Execution /unban par userId={}", event.getUser().getId());

        Guild guild = event.getGuild();
        if (guild == null) {
            logger.warn("/unban appelee hors serveur userId={}", event.getUser().getId());
            event.reply("❌ Cette commande doit etre utilisee dans un serveur.").setEphemeral(true).queue();
            return;
        }

        String userId = event.getOption("user_id", OptionMapping::getAsString);
        String reason = event.getOption("raison", OptionMapping::getAsString);

        if (userId == null || !userId.matches("\\d{17,20}")) {
            logger.warn("/unban invalide: user_id incorrect modId={} userId={}", event.getUser().getId(), userId);
            event.reply("❌ ID utilisateur invalide.").setEphemeral(true).queue();
            return;
        }

        String finalReason = (reason == null || reason.isBlank()) ? "Aucune raison specifiee" : reason;
        logger.info("/unban demande: modId={}, targetId={}, reason={}", event.getUser().getId(), userId, finalReason);

        // Defer immédiat : retrieveBan est asynchrone et peut dépasser les 3 secondes
        event.deferReply(false).queue();

        // retrieveBan cible directement l'utilisateur ❌ pas besoin de charger toute la liste
        guild.retrieveBan(UserSnowflake.fromId(userId)).queue(
                ban -> {
                    // L'utilisateur est bien banni, on procède au unban
                    User bannedUser = ban.getUser();
                    logger.info("/unban cible confirmee bannie: targetId={}", userId);

                    guild.unban(UserSnowflake.fromId(userId)).reason(finalReason).queue(
                            success -> {
                                event.getHook().sendMessage(
                                        "✅ **" + bannedUser.getName() + "** a été débanni. Raison : " + finalReason
                                ).queue();
                                logger.info("/unban reussi: modId={}, targetId={}", event.getUser().getId(), userId);

                                moderationLogListener.logAction(
                                        guild,
                                        "UNBAN",
                                        event.getMember(),
                                        bannedUser,
                                        finalReason,
                                        "Utilisateur débanni: " + bannedUser.getName() + " (" + userId + ")"
                                );
                            },
                            error -> {
                                logger.error("/unban echec API: modId={}, targetId={}", event.getUser().getId(), userId, error);
                                event.getHook().sendMessage("❌ Une erreur est survenue pendant le deban.").queue();
                            }
                    );
                },
                error -> {
                    // ErrorResponse.UNKNOWN_BAN = l'utilisateur n'est pas banni
                    if (error instanceof ErrorResponseException ere && ere.getErrorResponse() == ErrorResponse.UNKNOWN_BAN) {
                        logger.warn("/unban refuse: cible non bannie targetId={}", userId);
                        event.getHook().sendMessage("❌ Cet utilisateur n'est pas banni.").queue();
                    } else {
                        logger.error("/unban echec retrieveBan: modId={}, targetId={}", event.getUser().getId(), userId, error);
                        event.getHook().sendMessage("❌ Impossible de vérifier le bannissement de cet utilisateur.").queue();
                    }
                }
        );
    }
}
