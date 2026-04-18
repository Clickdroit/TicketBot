package fr.sakura.bot.commands;

import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.utils.LevelProfile;
import fr.sakura.bot.utils.LevelService;
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

import java.util.Map;
import java.util.StringJoiner;

public class XpAdminCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(XpAdminCommand.class);

    private final LevelService levelService;
    private final SettingsManager settingsManager;

    public XpAdminCommand(LevelService levelService, SettingsManager settingsManager) {
        this.levelService = levelService;
        this.settingsManager = settingsManager;
    }

    @Override
    public String getName() {
        return "xpadmin";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Administration XP et rôles de niveau")
                .addSubcommands(
                        new SubcommandData("set", "Définit l'XP totale d'un membre")
                                .addOptions(
                                        new OptionData(OptionType.USER, "membre", "Membre ciblé", true),
                                        new OptionData(OptionType.INTEGER, "xp", "XP totale", true).setMinValue(0)
                                ),
                        new SubcommandData("add", "Ajoute ou retire de l'XP à un membre")
                                .addOptions(
                                        new OptionData(OptionType.USER, "membre", "Membre ciblé", true),
                                        new OptionData(OptionType.INTEGER, "delta", "XP à ajouter (négatif possible)", true)
                                ),
                        new SubcommandData("reset", "Réinitialise l'XP d'un membre")
                                .addOptions(new OptionData(OptionType.USER, "membre", "Membre ciblé", true)),
                        new SubcommandData("roleset", "Associe un rôle à un niveau")
                                .addOptions(
                                        new OptionData(OptionType.INTEGER, "niveau", "Niveau cible", true).setMinValue(1).setMaxValue(500),
                                        new OptionData(OptionType.ROLE, "role", "Rôle à attribuer", true)
                                ),
                        new SubcommandData("roleremove", "Retire l'association de rôle d'un niveau")
                                .addOptions(new OptionData(OptionType.INTEGER, "niveau", "Niveau cible", true).setMinValue(1).setMaxValue(500)),
                        new SubcommandData("rolelist", "Liste les associations niveau -> rôle")
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("❌ Commande utilisable uniquement sur un serveur.").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply("❌ Sous-commande manquante.").setEphemeral(true).queue();
            return;
        }

        switch (sub) {
            case "set" -> {
                Member target = event.getOption("membre", OptionMapping::getAsMember);
                long xp = event.getOption("xp", 0, OptionMapping::getAsLong);
                if (target == null) {
                    event.reply("❌ Membre introuvable.").setEphemeral(true).queue();
                    return;
                }
                levelService.setUserXp(guildId, target.getId(), xp);
                LevelProfile profile = levelService.getProfile(guildId, target.getId());
                event.reply("✅ XP de " + target.getAsMention() + " définie à **" + profile.xp() + "** (niveau **" + profile.level() + "**).")
                        .setEphemeral(true)
                        .queue();
            }
            case "add" -> {
                Member target = event.getOption("membre", OptionMapping::getAsMember);
                long delta = event.getOption("delta", 0, OptionMapping::getAsLong);
                if (target == null) {
                    event.reply("❌ Membre introuvable.").setEphemeral(true).queue();
                    return;
                }
                levelService.addUserXp(guildId, target.getId(), delta);
                LevelProfile profile = levelService.getProfile(guildId, target.getId());
                event.reply("✅ XP mise à jour pour " + target.getAsMention() + " : **" + profile.xp() + "** (niveau **" + profile.level() + "**).")
                        .setEphemeral(true)
                        .queue();
            }
            case "reset" -> {
                Member target = event.getOption("membre", OptionMapping::getAsMember);
                if (target == null) {
                    event.reply("❌ Membre introuvable.").setEphemeral(true).queue();
                    return;
                }
                levelService.resetUser(guildId, target.getId());
                event.reply("✅ XP réinitialisée pour " + target.getAsMention() + ".").setEphemeral(true).queue();
            }
            case "roleset" -> {
                int level = event.getOption("niveau", 1, OptionMapping::getAsInt);
                Role role = event.getOption("role", OptionMapping::getAsRole);
                if (role == null) {
                    event.reply("❌ Rôle introuvable.").setEphemeral(true).queue();
                    return;
                }
                settingsManager.setLevelRole(guildId, level, role.getId());
                event.reply("✅ Niveau **" + level + "** associé au rôle " + role.getAsMention() + ".")
                        .setEphemeral(true)
                        .queue();
            }
            case "roleremove" -> {
                int level = event.getOption("niveau", 1, OptionMapping::getAsInt);
                settingsManager.removeLevelRole(guildId, level);
                event.reply("✅ Association de rôle supprimée pour le niveau **" + level + "**.").setEphemeral(true).queue();
            }
            case "rolelist" -> {
                Map<Integer, String> mappings = settingsManager.getLevelRoleMappings(guildId);
                if (mappings.isEmpty()) {
                    event.reply("ℹ️ Aucun rôle de niveau configuré.").setEphemeral(true).queue();
                    return;
                }
                StringJoiner joiner = new StringJoiner("\n");
                mappings.forEach((lvl, roleId) -> joiner.add("• Niveau **" + lvl + "** → <@&" + roleId + ">"));
                event.reply("🎖️ Rôles de niveau configurés :\n" + joiner).setEphemeral(true).queue();
            }
            default -> event.reply("❌ Sous-commande inconnue.").setEphemeral(true).queue();
        }

        logger.info("/xpadmin {} par userId={} guildId={}", sub, event.getUser().getId(), guildId);
    }
}
