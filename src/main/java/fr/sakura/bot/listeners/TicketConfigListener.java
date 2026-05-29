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
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gère les interactions dynamiques JDA 5 pour le panneau de configuration (/ticketconfig).
 * Actualise l'embed en temps réel sur le même message, remplaçant temporairement les boutons par des menus de sélection.
 */
public class TicketConfigListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(TicketConfigListener.class);

    private static final String BTN_LOGS = "config:logs";
    private static final String BTN_TRANSCRIPTS = "config:transcripts";
    private static final String BTN_SUPPORT_ROLE = "config:support-role";

    private static final String SELECT_LOGS = "config:select:logs";
    private static final String SELECT_TRANSCRIPTS = "config:select:transcripts";
    private static final String SELECT_SUPPORT_ROLE = "config:select:support-role";

    private final SettingsManager settingsManager;

    public TicketConfigListener(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null || event.getMember() == null) {
            return;
        }

        // Seuls les administrateurs peuvent interagir
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

            // Remplacement dynamique des boutons par le menu déroulant sur le même embed
            event.editMessageEmbeds(event.getMessage().getEmbeds().get(0))
                    .setComponents(ActionRow.of(menu))
                    .queue(
                            success -> logger.info("Bouton logs cliqué : affichage du menu déroulant sur le panneau d'origine"),
                            error -> logger.error("Échec édition message pour menu logs", error)
                    );
            return;
        }

        if (BTN_TRANSCRIPTS.equals(buttonId)) {
            EntitySelectMenu menu = EntitySelectMenu.create(SELECT_TRANSCRIPTS, EntitySelectMenu.SelectTarget.CHANNEL)
                    .setPlaceholder("📂 Sélectionnez le salon des transcriptions")
                    .setChannelTypes(ChannelType.TEXT)
                    .build();

            event.editMessageEmbeds(event.getMessage().getEmbeds().get(0))
                    .setComponents(ActionRow.of(menu))
                    .queue(
                            success -> logger.info("Bouton transcriptions cliqué : affichage du menu déroulant sur le panneau d'origine"),
                            error -> logger.error("Échec édition message pour menu transcripts", error)
                    );
            return;
        }

        if (BTN_SUPPORT_ROLE.equals(buttonId)) {
            EntitySelectMenu menu = EntitySelectMenu.create(SELECT_SUPPORT_ROLE, EntitySelectMenu.SelectTarget.ROLE)
                    .setPlaceholder("👥 Sélectionnez le rôle de support")
                    .build();

            event.editMessageEmbeds(event.getMessage().getEmbeds().get(0))
                    .setComponents(ActionRow.of(menu))
                    .queue(
                            success -> logger.info("Bouton support cliqué : affichage du menu déroulant sur le panneau d'origine"),
                            error -> logger.error("Échec édition message pour menu support-role", error)
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
        } else if (SELECT_SUPPORT_ROLE.equals(menuId)) {
            if (!event.getValues().isEmpty()) {
                Role role = event.getMentions().getRoles().get(0);
                settingsManager.setSupportRoleId(guildId, role.getId());
                logger.info("Rôle support configuré via menu interactif guildId={} roleId={}", guildId, role.getId());
                updated = true;
            }
        }

        if (updated) {
            // Reconstruction de l'embed mis à jour
            EmbedBuilder updatedEmbed = buildConfigEmbed(event.getGuild(), settingsManager);

            // Rétablissement des boutons d'origine avec l'embed mis à jour
            Button btnLogs = Button.secondary(BTN_LOGS, "📝 Salon de Logs");
            Button btnTranscripts = Button.secondary(BTN_TRANSCRIPTS, "📂 Salon Transcriptions");
            Button btnSupportRole = Button.secondary(BTN_SUPPORT_ROLE, "👥 Rôle Support");

            event.editMessageEmbeds(updatedEmbed.build())
                    .setComponents(ActionRow.of(btnLogs, btnTranscripts, btnSupportRole))
                    .queue(
                            success -> logger.info("Message d'embed mis à jour avec succès après sélection"),
                            error -> logger.error("Échec de l'actualisation de l'embed après sélection", error)
                    );
        }
    }

    /**
     * Reconstruit l'embed de configuration dynamique.
     */
    public static EmbedBuilder buildConfigEmbed(Guild guild, SettingsManager settingsManager) {
        String guildId = guild.getId();

        String logsChannelMention = settingsManager.getLogChannelId(guildId)
                .map(id -> "<#" + id + ">")
                .orElse("❌ *Non configuré*");

        String transcriptsChannelMention = settingsManager.getTranscriptChannelId(guildId)
                .map(id -> "<#" + id + ">")
                .orElse("❌ *Non configuré*");

        String supportRoleMention = settingsManager.getSupportRoleId(guildId)
                .map(id -> "<@&" + id + ">")
                .orElse("⚠️ *Détection automatique (fallback)*");

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("⚙️", "Configuration de TicketBot");
        embed.setAuthor("Panneau d'Administration • " + guild.getName(), null, guild.getIconUrl());
        
        embed.setDescription("Bienvenue dans le panneau de configuration interactif de votre système de support.\n" +
                "Cliquez sur les boutons ci-dessous pour modifier les salons de logs, de transcriptions ou configurer le rôle de support à ping.\n\n" +
                EmbedStyle.sectionHeader("🔧", "Paramètres des salons") + "\n" +
                EmbedStyle.detailLine("Salon de Logs", logsChannelMention) + "\n" +
                EmbedStyle.detailLine("Salon Transcriptions", transcriptsChannelMention) + "\n\n" +
                EmbedStyle.sectionHeader("👥", "Rôles de support") + "\n" +
                EmbedStyle.detailLine("Rôle à ping", supportRoleMention) + "\n\n" +
                "💡 *Les boutons ci-dessous afficheront des menus de sélection privés et sécurisés.*");

        EmbedStyle.setFooter(embed, "TicketBot Administration", guild.getIconUrl());
        return embed;
    }
}
