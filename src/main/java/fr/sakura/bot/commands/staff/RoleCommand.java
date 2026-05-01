package fr.sakura.bot.commands.staff;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.service.TempRoleService;
import fr.sakura.bot.core.util.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
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
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Commandes pour gérer les rôles (info, members, temprole).
 */
public class RoleCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(RoleCommand.class);
    private final TempRoleService tempRoleService;

    public RoleCommand(TempRoleService tempRoleService) {
        this.tempRoleService = tempRoleService;
    }

    @Override
    public String getName() {
        return "role";
    }

    @Override
    public String getCategory() {
        return "Staff & Divers";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Gère les rôles du serveur")
                .addSubcommands(
                        new SubcommandData("info", "Affiche des informations sur un rôle")
                                .addOptions(new OptionData(OptionType.ROLE, "role", "Le rôle à inspecter", true)),
                        new SubcommandData("members", "Liste les membres ayant un rôle")
                                .addOptions(new OptionData(OptionType.ROLE, "role", "Le rôle à inspecter", true)),
                        new SubcommandData("temprole", "Attribue un rôle temporairement")
                                .addOptions(
                                        new OptionData(OptionType.USER, "membre", "Le membre", true),
                                        new OptionData(OptionType.ROLE, "role", "Le rôle", true),
                                        new OptionData(OptionType.INTEGER, "duree", "Durée (en minutes)", true).setRequiredRange(1, 43200)
                                )
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        switch (subcommand) {
            case "info" -> handleInfo(event);
            case "members" -> handleMembers(event);
            case "temprole" -> handleTempRole(event);
        }
    }

    private void handleInfo(SlashCommandInteractionEvent event) {
        Role role = event.getOption("role", OptionMapping::getAsRole);
        if (role == null) return;

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("🎭", "Informations sur le rôle");
        embed.addField("Nom", role.getAsMention() + " (" + role.getName() + ")", true);
        embed.addField("ID", "`" + role.getId() + "`", true);
        embed.addField("Couleur", role.getColor() != null ? String.format("#%06X", role.getColorRaw()) : "Aucune", true);
        embed.addField("Position", String.valueOf(role.getPosition()), true);
        embed.addField("Membres", String.valueOf(event.getGuild().getMembersWithRoles(role).size()), true);
        embed.addField("Mentionnable", role.isMentionable() ? "✅ Oui" : "❌ Non", true);
        embed.addField("Affiché séparément", role.isHoisted() ? "✅ Oui" : "❌ Non", true);
        
        String perms = role.getPermissions().stream()
                .map(Permission::getName)
                .collect(Collectors.joining(", "));
        if (perms.length() > 1024) perms = perms.substring(0, 1021) + "...";
        embed.addField("Permissions", "```\n" + (perms.isEmpty() ? "Aucune" : perms) + "\n```", false);

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleMembers(SlashCommandInteractionEvent event) {
        Role role = event.getOption("role", OptionMapping::getAsRole);
        if (role == null) return;

        List<Member> members = event.getGuild().getMembersWithRoles(role);
        if (members.isEmpty()) {
            event.reply("ℹ️ Aucun membre n'a ce rôle.").queue();
            return;
        }

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("👥", "Membres avec le rôle " + role.getName());
        String membersList = members.stream()
                .limit(20)
                .map(m -> "• " + m.getAsMention())
                .collect(Collectors.joining("\n"));
        
        if (members.size() > 20) {
            membersList += "\n*...et " + (members.size() - 20) + " autres membres.*";
        }
        
        embed.setDescription(membersList);
        event.replyEmbeds(embed.build()).queue();
    }

    private void handleTempRole(SlashCommandInteractionEvent event) {
        Member target = event.getOption("membre", OptionMapping::getAsMember);
        Role role = event.getOption("role", OptionMapping::getAsRole);
        int durationMins = event.getOption("duree", 0, OptionMapping::getAsInt);

        if (target == null || role == null) return;

        if (!event.getGuild().getSelfMember().canInteract(role)) {
            event.reply("❌ Je ne peux pas gérer ce rôle (hiérarchie).").setEphemeral(true).queue();
            return;
        }

        tempRoleService.addTempRole(event.getGuild(), target, role, TimeUnit.MINUTES.toMillis(durationMins));
        event.reply("✅ Le rôle " + role.getAsMention() + " a été attribué à **" + target.getUser().getName() + "** pour " + durationMins + " minutes.").queue();
    }
}
