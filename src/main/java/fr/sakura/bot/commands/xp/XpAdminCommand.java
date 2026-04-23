package fr.sakura.bot.commands.xp;



import fr.sakura.bot.commands.ICommand;

import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.core.model.LevelProfile;
import fr.sakura.bot.core.service.LevelService;
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
        return "xp-admin";
    }

    @Override
    public String getCategory() {
        return "XP & niveaux";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Administration XP et rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´les de niveau")
                .addSubcommands(
                        new SubcommandData("set", "DÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©finit l'XP totale d'un membre")
                                .addOptions(
                                        new OptionData(OptionType.USER, "membre", "Membre ciblÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©", true),
                                        new OptionData(OptionType.INTEGER, "xp", "XP totale", true).setMinValue(0)
                                ),
                        new SubcommandData("add", "Ajoute ou retire de l'XP ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â  un membre")
                                .addOptions(
                                        new OptionData(OptionType.USER, "membre", "Membre ciblÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©", true),
                                        new OptionData(OptionType.INTEGER, "delta", "XP ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â  ajouter (nÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©gatif possible)", true)
                                ),
                        new SubcommandData("reset", "RÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©initialise l'XP d'un membre")
                                .addOptions(new OptionData(OptionType.USER, "membre", "Membre ciblÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©", true)),
                        new SubcommandData("roleset", "Associe un rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´le ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â  un niveau")
                                .addOptions(
                                        new OptionData(OptionType.INTEGER, "niveau", "Niveau cible", true).setMinValue(1).setMaxValue(500),
                                        new OptionData(OptionType.ROLE, "role", "RÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´le ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â  attribuer", true)
                                ),
                        new SubcommandData("roleremove", "Retire l'association de rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´le d'un niveau")
                                .addOptions(new OptionData(OptionType.INTEGER, "niveau", "Niveau cible", true).setMinValue(1).setMaxValue(500)),
                        new SubcommandData("rolelist", "Liste les associations niveau -> rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´le")
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("❌ Commande utilisable uniquement sur un serveur.").setEphemeral(true).queue();
            return;
        }

        if (!levelService.isLevelsEnabled(event.getGuild().getId())) {
            event.reply("❌ Le système de niveaux est désactivé sur ce serveur.").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Sous-commande manquante.").setEphemeral(true).queue();
            return;
        }

        switch (sub) {
            case "set" -> {
                Member target = event.getOption("membre", OptionMapping::getAsMember);
                long xp = event.getOption("xp", 0L, OptionMapping::getAsLong);
                if (target == null) {
                    event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Membre introuvable.").setEphemeral(true).queue();
                    return;
                }
                levelService.setUserXp(guildId, target.getId(), xp);
                LevelProfile profile = levelService.getProfile(guildId, target.getId());
                event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ XP de " + target.getAsMention() + " dÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©finie ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â  **" + profile.xp() + "** (niveau **" + profile.level() + "**).")
                        .setEphemeral(true)
                        .queue();
            }
            case "add" -> {
                Member target = event.getOption("membre", OptionMapping::getAsMember);
                long delta = event.getOption("delta", 0L, OptionMapping::getAsLong);
                if (target == null) {
                    event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Membre introuvable.").setEphemeral(true).queue();
                    return;
                }
                levelService.addUserXp(guildId, target.getId(), delta);
                LevelProfile profile = levelService.getProfile(guildId, target.getId());
                event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ XP mise ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â  jour pour " + target.getAsMention() + " : **" + profile.xp() + "** (niveau **" + profile.level() + "**).")
                        .setEphemeral(true)
                        .queue();
            }
            case "reset" -> {
                Member target = event.getOption("membre", OptionMapping::getAsMember);
                if (target == null) {
                    event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Membre introuvable.").setEphemeral(true).queue();
                    return;
                }
                levelService.resetUser(guildId, target.getId());
                event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ XP rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©initialisÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©e pour " + target.getAsMention() + ".").setEphemeral(true).queue();
            }
            case "roleset" -> {
                int level = event.getOption("niveau", 1, OptionMapping::getAsInt);
                Role role = event.getOption("role", OptionMapping::getAsRole);
                if (role == null) {
                    event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ RÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´le introuvable.").setEphemeral(true).queue();
                    return;
                }
                settingsManager.setLevelRole(guildId, level, role.getId());
                event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Niveau **" + level + "** associÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© au rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´le " + role.getAsMention() + ".")
                        .setEphemeral(true)
                        .queue();
            }
            case "roleremove" -> {
                int level = event.getOption("niveau", 1, OptionMapping::getAsInt);
                settingsManager.removeLevelRole(guildId, level);
                event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Association de rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´le supprimÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©e pour le niveau **" + level + "**.").setEphemeral(true).queue();
            }
            case "rolelist" -> {
                Map<Integer, String> mappings = settingsManager.getLevelRoleMappings(guildId);
                if (mappings.isEmpty()) {
                    event.reply("ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¾Ãƒâ€šÃ‚Â¹ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Aucun rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´le de niveau configurÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©.").setEphemeral(true).queue();
                    return;
                }
                StringJoiner joiner = new StringJoiner("\n");
                mappings.forEach((lvl, roleId) -> joiner.add("ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â¢ Niveau **" + lvl + "** ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ <@&" + roleId + ">"));
                event.reply("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã‚Â½ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Å“ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â RÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´les de niveau configurÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©s :\n" + joiner).setEphemeral(true).queue();
            }
            default -> event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Sous-commande inconnue.").setEphemeral(true).queue();
        }

        logger.info("/xpadmin {} par userId={} guildId={}", sub, event.getUser().getId(), guildId);
    }
}
