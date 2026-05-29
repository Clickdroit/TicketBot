package fr.sakura.bot.listeners;

import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.database.SettingsManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Gère les interactions JDA 5 pour la configuration multi-rôles et catégorisée de TicketBot (/ticketconfig).
 */
public class TicketConfigListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(TicketConfigListener.class);

    private static final String BTN_LOGS = "config:logs";
    private static final String BTN_TRANSCRIPTS = "config:transcripts";
    private static final String BTN_SUPPORT_ROLE = "config:support-role";

    private static final String SELECT_LOGS = "config:select:logs";
    private static final String SELECT_TRANSCRIPTS = "config:select:transcripts";
    private static final String SELECT_SUPPORT_ROLE_PREFIX = "config:select:support-role:";
    
    private static final String MENU_SELECT_CATEGORY = "config:support-role:select-category";

    private final SettingsManager settingsManager;

    public TicketConfigListener(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null || event.getMember() == null) {
            return;
        }

        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ Seul un administrateur du serveur peut modifier la configuration.").setEphemeral(true).queue();
            return;
        }

        String buttonId = event.getComponentId();

        if (BTN_LOGS.equals(buttonId)) {
            EntitySelectMenu menu = EntitySelectMenu.create(SELECT_LOGS, EntitySelectMenu.SelectTarget.CHANNEL)
                    .setPlaceholder("📝 Sélectionnez le salon des logs")
                    .setChannelTypes(ChannelType.TEXT)
                    .build();

            event.editMessageEmbeds(event.getMessage().getEmbeds().get(0))
                    .setComponents(ActionRow.of(menu))
                    .queue();
            return;
        }

        if (BTN_TRANSCRIPTS.equals(buttonId)) {
            EntitySelectMenu menu = EntitySelectMenu.create(SELECT_TRANSCRIPTS, EntitySelectMenu.SelectTarget.CHANNEL)
                    .setPlaceholder("📂 Sélectionnez le salon des transcriptions")
                    .setChannelTypes(ChannelType.TEXT)
                    .build();

            event.editMessageEmbeds(event.getMessage().getEmbeds().get(0))
                    .setComponents(ActionRow.of(menu))
                    .queue();
            return;
        }

        if (BTN_SUPPORT_ROLE.equals(buttonId)) {
            // Etape 1 : Renvoyer un StringSelectMenu pour choisir la catégorie à configurer
            StringSelectMenu categoryMenu = StringSelectMenu.create(MENU_SELECT_CATEGORY)
                    .setPlaceholder("📂 Choisissez la catégorie de ticket à configurer")
                    .addOption("🌍 Par défaut / Global", "config:cat:global", "Rôles de support par défaut pour tous les tickets", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🌍"))
                    .addOption("🤝 Partenariat", "config:cat:partnership", "Rôles de support pour les partenariats", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🤝"))
                    .addOption("🚨 Signalement", "config:cat:report", "Rôles de support pour les signalements", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🚨"))
                    .addOption("🛠️ Support Général", "config:cat:support", "Rôles de support général", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🛠️"))
                    .addOption("💡 Suggestion", "config:cat:suggestion", "Rôles de support pour les suggestions", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("💡"))
                    .addOption("❓ Autre", "config:cat:other", "Rôles de support pour les autres demandes", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("❓"))
                    .build();

            event.editMessageEmbeds(event.getMessage().getEmbeds().get(0))
                    .setComponents(ActionRow.of(categoryMenu))
                    .queue();
        }
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null || event.getMember() == null) {
            return;
        }

        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ Seul un administrateur du serveur peut modifier la configuration.").setEphemeral(true).queue();
            return;
        }

        if (MENU_SELECT_CATEGORY.equals(event.getComponentId())) {
            if (event.getValues().isEmpty()) return;
            
            String selectedCategory = event.getValues().get(0).replace("config:cat:", "");
            String categoryLabel = getCategoryLabel(selectedCategory);

            // Etape 2 : Envoyer le menu de rôles JDA 5 avec sélection multiple (1 à 10 rôles !)
            EntitySelectMenu roleMenu = EntitySelectMenu.create(SELECT_SUPPORT_ROLE_PREFIX + selectedCategory, EntitySelectMenu.SelectTarget.ROLE)
                    .setPlaceholder("👥 Sélectionnez les rôles de support (1 à 10) pour : " + categoryLabel)
                    .setMinValues(1)
                    .setMaxValues(10)
                    .build();

            event.editMessageEmbeds(event.getMessage().getEmbeds().get(0))
                    .setComponents(ActionRow.of(roleMenu))
                    .queue(
                            success -> logger.info("Menu de catégorie sélectionné : affichage du EntitySelectMenu multi-rôles pour {}", selectedCategory)
                    );
        }
    }

    @Override
    public void onEntitySelectInteraction(@NotNull EntitySelectInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null || event.getMember() == null) {
            return;
        }

        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ Seul un administrateur du serveur peut modifier la configuration.").setEphemeral(true).queue();
            return;
        }

        String menuId = event.getComponentId();
        String guildId = event.getGuild().getId();
        boolean updated = false;

        if (SELECT_LOGS.equals(menuId)) {
            if (!event.getValues().isEmpty()) {
                GuildChannel channel = event.getMentions().getChannels().get(0);
                settingsManager.setLogChannelId(guildId, channel.getId());
                logger.info("Logs configurés via menu interactif guildId={} channelId={}", guildId, channel.getId());
                updated = true;
            }
        } else if (SELECT_TRANSCRIPTS.equals(menuId)) {
            if (!event.getValues().isEmpty()) {
                GuildChannel channel = event.getMentions().getChannels().get(0);
                settingsManager.setTranscriptChannelId(guildId, channel.getId());
                logger.info("Transcriptions configurées via menu interactif guildId={} channelId={}", guildId, channel.getId());
                updated = true;
            }
        } else if (menuId.startsWith(SELECT_SUPPORT_ROLE_PREFIX)) {
            String category = menuId.substring(SELECT_SUPPORT_ROLE_PREFIX.length());
            
            // Récupérer la liste des rôles sélectionnés
            List<String> roleIds = event.getMentions().getRoles().stream()
                    .map(Role::getId)
                    .collect(Collectors.toList());

            settingsManager.setSupportRoles(guildId, category, roleIds);
            logger.info("Rôles de support configurés pour la catégorie {} : guildId={}, count={}", category, guildId, roleIds.size());
            updated = true;
        }

        if (updated) {
            // Reconstruction de l'embed mis à jour avec les nouveaux rôles et catégories
            EmbedBuilder updatedEmbed = buildConfigEmbed(event.getGuild(), settingsManager);

            // Rétablissement des boutons d'origine
            Button btnLogs = Button.secondary(BTN_LOGS, "📝 Salon de Logs");
            Button btnTranscripts = Button.secondary(BTN_TRANSCRIPTS, "📂 Salon Transcriptions");
            Button btnSupportRole = Button.secondary(BTN_SUPPORT_ROLE, "👥 Rôle Support");

            event.editMessageEmbeds(updatedEmbed.build())
                    .setComponents(ActionRow.of(btnLogs, btnTranscripts, btnSupportRole))
                    .queue(
                            success -> logger.info("Message d'embed mis à jour après configuration multi-rôles"),
                            error -> logger.error("Échec actualisation embed après configuration", error)
                    );
        }
    }

    private String getCategoryLabel(String cat) {
        return switch (cat) {
            case "global" -> "Global / Par défaut";
            case "partnership" -> "Partenariats";
            case "report" -> "Signalements";
            case "support" -> "Support Général";
            case "suggestion" -> "Suggestions";
            case "other" -> "Autres demandes";
            default -> "Support";
        };
    }

    private static String formatRolesList(Guild guild, List<String> roleIds) {
        if (roleIds.isEmpty()) {
            return "❌ *Hérite du rôle Global*";
        }
        return roleIds.stream()
                .map(id -> {
                    Role role = guild.getRoleById(id);
                    return role != null ? role.getAsMention() : "<@&" + id + ">";
                })
                .collect(Collectors.joining(" "));
    }

    /**
     * Reconstruit l'embed de configuration dynamique avec les multi-rôles et les catégories.
     */
    public static EmbedBuilder buildConfigEmbed(Guild guild, SettingsManager settingsManager) {
        String guildId = guild.getId();

        String logsChannelMention = settingsManager.getLogChannelId(guildId)
                .map(id -> "<#" + id + ">")
                .orElse("❌ *Non configuré*");

        String transcriptsChannelMention = settingsManager.getTranscriptChannelId(guildId)
                .map(id -> "<#" + id + ">")
                .orElse("❌ *Non configuré*");

        // Récupérer la liste des rôles par catégorie
        String globalRoles = formatRolesList(guild, settingsManager.getSupportRoles(guildId, "global"));
        String partnershipRoles = formatRolesList(guild, settingsManager.getSupportRoles(guildId, "partnership"));
        String reportRoles = formatRolesList(guild, settingsManager.getSupportRoles(guildId, "report"));
        String supportRoles = formatRolesList(guild, settingsManager.getSupportRoles(guildId, "support"));
        String suggestionRoles = formatRolesList(guild, settingsManager.getSupportRoles(guildId, "suggestion"));
        String otherRoles = formatRolesList(guild, settingsManager.getSupportRoles(guildId, "other"));

        // Fallback sur le rôle historique unique support_role_id pour la catégorie globale
        if (globalRoles.contains("Hérite")) {
            String legacyRoleId = settingsManager.getSupportRoleId(guildId).orElse(null);
            if (legacyRoleId != null) {
                Role role = guild.getRoleById(legacyRoleId);
                globalRoles = role != null ? role.getAsMention() : "<@&" + legacyRoleId + ">";
            } else {
                globalRoles = "❌ *Non configuré*";
            }
        }

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("⚙️", "Configuration de TicketBot");
        embed.setAuthor("Panneau d'Administration • " + guild.getName(), null, guild.getIconUrl());
        
        embed.setDescription("Bienvenue dans le panneau de configuration interactif de votre système de support.\n" +
                "Cliquez sur les boutons ci-dessous pour modifier les salons de logs/transcriptions ou attribuer des **rôles de support à ping par catégorie**.\n\n" +
                EmbedStyle.sectionHeader("🔧", "Paramètres des salons") + "\n" +
                EmbedStyle.detailLine("Logs d'activité", logsChannelMention) + "\n" +
                EmbedStyle.detailLine("Transcriptions clos", transcriptsChannelMention) + "\n\n" +
                EmbedStyle.sectionHeader("👥", "Rôles de support à ping") + "\n" +
                EmbedStyle.detailLine("🌍 Par défaut / Global", globalRoles) + "\n" +
                EmbedStyle.detailLine("🤝 Partenariats", partnershipRoles) + "\n" +
                EmbedStyle.detailLine("🚨 Signalements", reportRoles) + "\n" +
                EmbedStyle.detailLine("🛠️ Support Général", supportRoles) + "\n" +
                EmbedStyle.detailLine("💡 Suggestions", suggestionRoles) + "\n" +
                EmbedStyle.detailLine("❓ Autres demandes", otherRoles) + "\n\n" +
                "💡 *Vous pouvez sélectionner de 1 à 10 rôles en même temps pour chaque catégorie.*");

        EmbedStyle.setFooter(embed, "TicketBot Administration", guild.getIconUrl());
        return embed;
    }
}
