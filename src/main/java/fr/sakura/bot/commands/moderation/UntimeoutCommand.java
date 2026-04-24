package fr.sakura.bot.commands.moderation;



import fr.sakura.bot.commands.ICommand;

import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UntimeoutCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(UntimeoutCommand.class);
    private final ModerationLogListener moderationLogListener;

    public UntimeoutCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "untimeout";
    }

    @Override
    public String getCategory() {
        return "Modération";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Retire le timeout d'un membre")
                .addOption(OptionType.USER, "membre", "Le membre à libérer", true)
                .addOption(OptionType.STRING, "raison", "Raison du retrait du timeout", false)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member target = event.getOption("membre", OptionMapping::getAsMember);
        String reason = event.getOption("raison", "Aucune raison fournie", OptionMapping::getAsString);

        if (target == null) {
            event.reply("❌ Membre introuvable.").setEphemeral(true).queue();
            return;
        }

        if (target.getTimeOutEnd() == null) {
            event.reply("❌ Ce membre n'est pas sous timeout.").setEphemeral(true).queue();
            return;
        }

        Member selfMember = event.getGuild().getSelfMember();
        if (!selfMember.hasPermission(Permission.MODERATE_MEMBERS) || !selfMember.canInteract(target)) {
            event.reply("❌ Je n'ai pas les permissions nécessaires pour retirer le timeout de ce membre.").setEphemeral(true).queue();
            return;
        }

        target.removeTimeout().reason(reason + " (par " + event.getUser().getName() + ")").queue(
                success -> {
                    event.reply("✅ Le timeout de " + target.getAsMention() + " a été retiré.").queue();
                    moderationLogListener.logAction(event.getGuild(), "UNTIMEOUT", event.getMember(), target, reason, null);
                    logger.info("Untimeout: userId={} par modId={} guildId={}", target.getId(), event.getUser().getId(), event.getGuild().getId());
                },
                error -> {
                    event.reply("❌ Impossible de retirer le timeout : " + error.getMessage()).setEphemeral(true).queue();
                    logger.error("Erreur untimeout guildId={}", event.getGuild().getId(), error);
                }
        );
    }
}
