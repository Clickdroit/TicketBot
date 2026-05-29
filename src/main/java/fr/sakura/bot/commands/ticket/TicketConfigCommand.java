package fr.sakura.bot.commands.ticket;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.core.model.TicketCategory;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
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
 * Commande de configuration de TicketBot.
 * Gère le panneau interactif, le statut Premium et la gestion dynamique des catégories (Premium).
 */
public class TicketConfigCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(TicketConfigCommand.class);
    private final SettingsManager settingsManager;

    private static final String FOUNDER_ID = "838024514369617930"; // Clickdroit

    public TicketConfigCommand(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    @Override
    public String getName() {
        return "ticketconfig";
    }

    @Override
    public String getCategory() {
        return "Tickets";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Gère la configuration de TicketBot")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(
                        new SubcommandData("panel", "Affiche le panneau de configuration interactif"),
                        new SubcommandData("premium-set", "Active/Désactive le statut Premium pour ce serveur (Founder only)")
                                .addOptions(new OptionData(OptionType.BOOLEAN, "active", "Activer ou non le Premium", true)),
                        new SubcommandData("category-add", "Ajoute ou modifie une catégorie de tickets (Premium)")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "id", "ID unique de la catégorie (ex: bug, vip)", true),
                                        new OptionData(OptionType.STRING, "nom", "Nom affiché (ex: Rapport de bug)", true),
                                        new OptionData(OptionType.STRING, "description", "Description de la catégorie", true),
                                        new OptionData(OptionType.STRING, "emoji", "Emoji Unicode associé (ex: 🐛)", false)
                                ),
                        new SubcommandData("category-remove", "Supprime une catégorie de tickets (Premium)")
                                .addOptions(new OptionData(OptionType.STRING, "id", "ID unique de la catégorie à supprimer", true)),
                        new SubcommandData("category-list", "Affiche toutes vos catégories actives (Premium)")
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("❌ Commande utilisable uniquement sur un serveur.").setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null || "panel".equals(subcommand)) {
            handlePanel(event);
            return;
        }

        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();

        switch (subcommand) {
            case "premium-set" -> handlePremiumSet(event, guildId, userId);
            case "category-add" -> handleCategoryAdd(event, guildId, userId);
            case "category-remove" -> handleCategoryRemove(event, guildId, userId);
            case "category-list" -> handleCategoryList(event, guildId, userId);
            default -> handlePanel(event);
        }
    }

    private void handlePanel(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = fr.sakura.bot.listeners.TicketConfigListener.buildConfigEmbed(event.getGuild(), settingsManager);

        Button btnLogs = Button.secondary("config:logs", "📝 Salon de Logs");
        Button btnTranscripts = Button.secondary("config:transcripts", "📂 Salon Transcriptions");
        Button btnSupportRole = Button.secondary("config:support-role", "👥 Rôle Support");

        event.replyEmbeds(embed.build())
                .setComponents(ActionRow.of(btnLogs, btnTranscripts, btnSupportRole))
                .queue(
                        success -> logger.info("Panneau ticketconfig affiché guildId={} par userId={}", event.getGuild().getId(), event.getUser().getId()),
                        error -> logger.error("Échec affichage panneau ticketconfig guildId={}", event.getGuild().getId(), error)
                );
    }

    private void handlePremiumSet(SlashCommandInteractionEvent event, String guildId, String userId) {
        if (!FOUNDER_ID.equals(userId)) {
            event.reply("❌ Seul le fondateur du bot (Clickdroit) peut accorder le statut Premium.").setEphemeral(true).queue();
            return;
        }

        boolean active = event.getOption("active", OptionMapping::getAsBoolean);
        settingsManager.setGuildPremium(guildId, active);

        event.reply("✨ Le statut Premium a été mis à jour pour ce serveur : **" + (active ? "🟢 Activé" : "🔴 Désactivé") + "**.").queue();
    }

    private void handleCategoryAdd(SlashCommandInteractionEvent event, String guildId, String userId) {
        if (!isPremium(guildId, userId)) {
            sendPremiumAlert(event);
            return;
        }

        String id = event.getOption("id", OptionMapping::getAsString);
        String nom = event.getOption("nom", OptionMapping::getAsString);
        String description = event.getOption("description", OptionMapping::getAsString);
        String emoji = event.getOption("emoji", OptionMapping::getAsString);

        if (id.length() > 20 || id.contains(" ") || !id.matches("^[a-zA-Z0-9_-]+$")) {
            event.reply("❌ L'ID de catégorie doit faire maximum 20 caractères, sans espaces et ne contenir que des lettres, chiffres, tirets ou underscores.").setEphemeral(true).queue();
            return;
        }

        settingsManager.addCategory(guildId, id, nom, description, emoji);
        TicketPanelCommand.updateAllGuildPanels(event.getGuild(), settingsManager);
        event.reply("✅ Catégorie **" + nom + "** (`" + id.toLowerCase() + "`) ajoutée/mise à jour avec succès !").setEphemeral(true).queue();
    }

    private void handleCategoryRemove(SlashCommandInteractionEvent event, String guildId, String userId) {
        if (!isPremium(guildId, userId)) {
            sendPremiumAlert(event);
            return;
        }

        String id = event.getOption("id", OptionMapping::getAsString);
        settingsManager.removeCategory(guildId, id);
        TicketPanelCommand.updateAllGuildPanels(event.getGuild(), settingsManager);
        event.reply("✅ Catégorie `" + id.toLowerCase() + "` supprimée avec succès !").setEphemeral(true).queue();
    }

    private void handleCategoryList(SlashCommandInteractionEvent event, String guildId, String userId) {
        if (!isPremium(guildId, userId)) {
            sendPremiumAlert(event);
            return;
        }

        List<TicketCategory> categories = settingsManager.getCategories(guildId);
        EmbedBuilder embed = EmbedStyle.newInfoEmbed("📂", "Catégories de tickets actives");
        embed.setAuthor("Panneau Catégories • " + event.getGuild().getName(), null, event.getGuild().getIconUrl());

        StringBuilder sb = new StringBuilder();
        sb.append("Voici la liste de vos catégories actives. Vous pouvez les insérer dans le panel via `/ticketpanel`.\n\n");

        for (TicketCategory cat : categories) {
            String emojiStr = cat.emoji() != null ? cat.emoji() : "🎫";
            sb.append(emojiStr).append(" **").append(cat.label()).append("** (`").append(cat.categoryId()).append("`)\n")
              .append("└ *").append(cat.description() != null ? cat.description() : "Aucune description").append("*\n\n");
        }

        embed.setDescription(sb.toString());
        EmbedStyle.setFooter(embed, "TicketBot Customization", event.getGuild().getIconUrl());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private boolean isPremium(String guildId, String userId) {
        return FOUNDER_ID.equals(userId) || settingsManager.isGuildPremium(guildId);
    }

    private void sendPremiumAlert(SlashCommandInteractionEvent event) {
        event.reply("❌ La personnalisation des catégories est une **fonctionnalité Premium**.\n" +
                "👉 *Rejoignez notre serveur de support ou souscrivez à l'offre à 2.99€/mois pour débloquer cette option !*")
                .setEphemeral(true)
                .queue();
    }
}
