package fr.sakura.bot.commands.staff;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.model.AutoModRuleEntry;
import fr.sakura.bot.core.store.AutoModRuleStore;
import fr.sakura.bot.core.util.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
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

/**
 * Commande pour gérer les règles d'AutoMod.
 */
public class AutoModCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(AutoModCommand.class);
    private final AutoModRuleStore ruleStore;

    public AutoModCommand(AutoModRuleStore ruleStore) {
        this.ruleStore = ruleStore;
    }

    @Override
    public String getName() {
        return "automod";
    }

    @Override
    public String getCategory() {
        return "Configuration";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Gère les règles de modération automatique")
                .addSubcommands(
                        new SubcommandData("add", "Ajoute une règle d'AutoMod")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "type", "Type de règle", true)
                                                .addChoice("Mot interdit", "WORD")
                                                .addChoice("Regex", "REGEX"),
                                        new OptionData(OptionType.STRING, "pattern", "Le mot ou le motif regex", true),
                                        new OptionData(OptionType.STRING, "action", "Action à effectuer", true)
                                                .addChoice("Supprimer uniquement", "DELETE")
                                                .addChoice("Supprimer et avertir", "WARN")
                                                .addChoice("Supprimer et sourdine", "TIMEOUT")
                                ),
                        new SubcommandData("remove", "Supprime une règle par son ID")
                                .addOptions(new OptionData(OptionType.INTEGER, "id", "L'ID de la règle", true)),
                        new SubcommandData("list", "Liste toutes les règles configurées")
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;

        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        switch (subcommand) {
            case "add" -> handleAdd(event);
            case "remove" -> handleRemove(event);
            case "list" -> handleList(event);
        }
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        String type = event.getOption("type", OptionMapping::getAsString);
        String pattern = event.getOption("pattern", OptionMapping::getAsString);
        String action = event.getOption("action", OptionMapping::getAsString);

        AutoModRuleEntry entry = new AutoModRuleEntry(event.getGuild().getId(), type, pattern, action);
        ruleStore.addRule(entry);

        event.reply("✅ Règle ajoutée avec succès.").queue();
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        long id = event.getOption("id", 0L, OptionMapping::getAsLong);
        boolean removed = ruleStore.removeRule(event.getGuild().getId(), id);

        if (removed) {
            event.reply("✅ Règle n°" + id + " supprimée.").queue();
        } else {
            event.reply("❌ Aucune règle trouvée avec cet ID.").setEphemeral(true).queue();
        }
    }

    private void handleList(SlashCommandInteractionEvent event) {
        List<AutoModRuleEntry> rules = ruleStore.getRules(event.getGuild().getId());
        if (rules.isEmpty()) {
            event.reply("ℹ️ Aucune règle personnalisée configurée.").queue();
            return;
        }

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("🔧", "Règles AutoMod");
        StringBuilder sb = new StringBuilder();
        
        for (AutoModRuleEntry rule : rules) {
            sb.append("• **#").append(rule.id()).append("** [").append(rule.type()).append("] : `")
              .append(rule.pattern()).append("` (Action: `").append(rule.action()).append("`)\n");
        }
        
        embed.setDescription(sb.toString());
        event.replyEmbeds(embed.build()).queue();
    }
}
