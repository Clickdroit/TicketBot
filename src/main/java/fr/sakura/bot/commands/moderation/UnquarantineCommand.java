package fr.sakura.bot.commands.moderation;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.database.ProtectSettingsManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
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
 * Commande pour retirer manuellement le rôle de quarantaine d'un membre.
 */
public class UnquarantineCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(UnquarantineCommand.class);
    private final ProtectSettingsManager protectSettingsManager;

    public UnquarantineCommand(ProtectSettingsManager protectSettingsManager) {
        this.protectSettingsManager = protectSettingsManager;
    }

    @Override
    public String getName() {
        return "unquarantine";
    }

    @Override
    public String getCategory() {
        return "Modération";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Retire le rôle de quarantaine d'un membre")
                .addOptions(
                        new OptionData(OptionType.USER, "membre", "Le membre à sortir de quarantaine", true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;

        Member target = event.getOption("membre", OptionMapping::getAsMember);
        if (target == null) {
            event.reply("❌ Membre introuvable.").setEphemeral(true).queue();
            return;
        }

        String roleId = protectSettingsManager.getQuarantineRoleId(event.getGuild().getId());
        if (roleId == null) {
            event.reply("❌ Aucun rôle de quarantaine n'est configuré sur ce serveur.").setEphemeral(true).queue();
            return;
        }

        Role quarantineRole = event.getGuild().getRoleById(roleId);
        if (quarantineRole == null) {
            event.reply("❌ Le rôle de quarantaine configuré est introuvable.").setEphemeral(true).queue();
            return;
        }

        if (!target.getRoles().contains(quarantineRole)) {
            event.reply("ℹ️ Ce membre n'a pas le rôle de quarantaine.").setEphemeral(true).queue();
            return;
        }

        event.getGuild().removeRoleFromMember(target, quarantineRole).reason("Retrait manuel de quarantaine par " + event.getUser().getName()).queue(
                success -> event.reply("✅ **" + target.getUser().getName() + "** a été sorti de quarantaine.").queue(),
                error -> {
                    logger.error("Erreur lors du retrait du rôle de quarantaine pour {}", target.getId(), error);
                    event.reply("❌ Impossible de retirer le rôle de quarantaine. Vérifiez mes permissions.").setEphemeral(true).queue();
                }
        );
    }
}
