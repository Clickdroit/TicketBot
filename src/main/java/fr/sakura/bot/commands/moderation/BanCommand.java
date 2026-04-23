package fr.sakura.bot.commands.moderation;



import fr.sakura.bot.commands.ICommand;

import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
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

public class BanCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(BanCommand.class);

    private final ModerationLogListener moderationLogListener;

    public BanCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "ban";
    }

    @Override
    public String getCategory() {
        return "ModÃƒÆ’Ã‚Â©ration";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Bannit un membre du serveur")
                .addOptions(
                        new OptionData(OptionType.USER, "membre", "Le membre ÃƒÆ’Ã‚Â  bannir", true),
                        new OptionData(OptionType.STRING, "raison", "La raison du bannissement", false)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        logger.debug("Execution /ban par userId={}", event.getUser().getId());

        if (event.getGuild() == null) {
            event.reply("ÃƒÂ¢Ã‚ÂÃ…â€™ Cette commande doit etre utilisee dans un serveur.").setEphemeral(true).queue();
            return;
        }

        var guild = event.getGuild();

        OptionMapping memberOption = event.getOption("membre");
        OptionMapping reasonOption = event.getOption("raison");
        String reason = reasonOption != null ? reasonOption.getAsString() : "Aucune raison spÃƒÆ’Ã‚Â©cifiÃƒÆ’Ã‚Â©e";

        if (memberOption == null) {
            event.reply("ÃƒÂ¢Ã‚ÂÃ…â€™ Membre introuvable.").setEphemeral(true).queue();
            return;
        }

        User targetUser = memberOption.getAsUser();

        Member target = guild.getMember(targetUser);

        // L'utilisateur a peut-ÃƒÆ’Ã‚Âªtre quittÃƒÆ’Ã‚Â© le serveur : fallback sur User pour quand mÃƒÆ’Ã‚Âªme bannir
        if (target == null) {
            logger.warn("/ban cible non membre du serveur, tentative ban par userId={} demandeurId={}",
                    targetUser.getId(), event.getUser().getId());

            guild.ban(targetUser, 0, TimeUnit.SECONDS).reason(reason).queue(
                    success -> {
                        event.reply("ÃƒÂ¢Ã…â€œÃ¢â‚¬Â¦ **" + targetUser.getName() + "** a ÃƒÆ’Ã‚Â©tÃƒÆ’Ã‚Â© banni (hors serveur). Raison : " + reason).queue();
                        logger.info("/ban reussi (hors serveur): modId={}, targetId={}", event.getUser().getId(), targetUser.getId());
                        moderationLogListener.logAction(event.getGuild(), "BAN", event.getMember(), targetUser, reason, "(hors serveur)");
                    },
                    error -> {
                        logger.error("/ban echec API (hors serveur): modId={}, targetId={}", event.getUser().getId(), targetUser.getId(), error);
                        event.reply("ÃƒÂ¢Ã‚ÂÃ…â€™ Impossible de bannir cet utilisateur.").setEphemeral(true).queue();
                    }
            );
            return;
        }

        if (event.getMember() == null || !event.getMember().canInteract(target)) {
            logger.warn("/ban refuse hierarchie: modId={}, targetId={}", event.getUser().getId(), target.getId());
            event.reply("ÃƒÂ¢Ã‚ÂÃ…â€™ Vous ne pouvez pas bannir cet utilisateur (rÃƒÆ’Ã‚Â´le supÃƒÆ’Ã‚Â©rieur).").setEphemeral(true).queue();
            return;
        }

        logger.info("/ban demande: modId={}, targetId={}, reason={}", event.getUser().getId(), target.getId(), reason);

        guild.ban(target, 0, TimeUnit.SECONDS).reason(reason).queue(
                success -> {
                    event.reply("ÃƒÂ¢Ã…â€œÃ¢â‚¬Â¦ **" + target.getUser().getName() + "** a ÃƒÆ’Ã‚Â©tÃƒÆ’Ã‚Â© banni. Raison : " + reason).queue();
                    logger.info("/ban reussi: modId={}, targetId={}", event.getUser().getId(), target.getId());

                    moderationLogListener.logAction(event.getGuild(), "BAN", event.getMember(), target, reason, null);
                },
                error -> {
                    logger.error("/ban echec API: modId={}, targetId={}", event.getUser().getId(), target.getId(), error);
                    event.reply("ÃƒÂ¢Ã‚ÂÃ…â€™ Une erreur est survenue (Ai-je les bonnes permissions ?).").setEphemeral(true).queue();
                }
        );
    }
}
