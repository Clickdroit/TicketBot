package fr.sakura.bot.commands;

import fr.sakura.bot.utils.ModerationLogger;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
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

public class UnbanCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(UnbanCommand.class);

    private final ModerationLogger moderationLogger;

    public UnbanCommand(ModerationLogger moderationLogger) {
        this.moderationLogger = moderationLogger;
    }

    @Override
    public String getName() {
        return "unban";
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

        String finalReason = (reason == null || reason.isEmpty()) ? "Aucune raison specifiee" : reason;
        logger.info("/unban demande: modId={}, targetId={}, reason={}", event.getUser().getId(), userId, finalReason);

        guild.retrieveBanList().queue(bans -> {
            boolean isBanned = bans.stream().anyMatch(ban -> ban.getUser().getId().equals(userId));
            if (!isBanned) {
                logger.warn("/unban refuse: cible non bannie targetId={}", userId);
                event.reply("❌ Cet utilisateur n'est pas banni.").setEphemeral(true).queue();
                return;
            }

            guild.unban(UserSnowflake.fromId(userId)).reason(finalReason).queue(
                    success -> {
                        event.getJDA().retrieveUserById(userId).queue(
                                user -> sendSuccessWithLog(event, guild, user, finalReason),
                                error -> {
                                    event.reply("✅ Utilisateur debanni (ID: " + userId + "). Raison : " + finalReason).queue();
                                    logger.info("/unban reussi sans details user object: targetId={}", userId);
                                    sendFallbackLog(event, guild, userId, finalReason);
                                }
                        );
                    },
                    error -> {
                        logger.error("/unban echec API: modId={}, targetId={}", event.getUser().getId(), userId, error);
                        event.reply("❌ Une erreur est survenue pendant le deban.").setEphemeral(true).queue();
                    }
            );
        }, error -> {
            logger.error("/unban echec retrieveBanList: modId={}, targetId={}", event.getUser().getId(), userId, error);
            event.reply("❌ Impossible de verifier la liste des bannissements.").setEphemeral(true).queue();
        });
    }

    private void sendSuccessWithLog(SlashCommandInteractionEvent event, Guild guild, User user, String reason) {
        event.reply("✅ **" + user.getName() + "** a ete debanni. Raison : " + reason).queue();
        logger.info("/unban reussi: modId={}, targetId={}", event.getUser().getId(), user.getId());

        if (moderationLogger.isEnabled()) {
            TextChannel logChannel = guild.getTextChannelById(moderationLogger.getLogChannelId());
            moderationLogger.log(logChannel, "UNBAN", event.getMember(), null, reason, "Utilisateur: " + user.getName() + " (" + user.getId() + ")");
        }
    }

    private void sendFallbackLog(SlashCommandInteractionEvent event, Guild guild, String userId, String reason) {
        logger.debug("/unban fallback log utilise targetId={}", userId);
        if (moderationLogger.isEnabled()) {
            TextChannel logChannel = guild.getTextChannelById(moderationLogger.getLogChannelId());
            moderationLogger.log(logChannel, "UNBAN", event.getMember(), null, reason, "Utilisateur ID: " + userId);
        }
    }
}

