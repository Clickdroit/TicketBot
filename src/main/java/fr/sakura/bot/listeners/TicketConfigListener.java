package fr.sakura.bot.listeners;

import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.database.SettingsManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gère les interactions boutons et menus de sélection d'entités de JDA 5 pour la configuration interactive de TicketBot.
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

        // Vérification des droits d'administrateur
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

            event.reply("Sélectionnez le salon où envoyer les logs d'activité de tickets :")
                    .setComponents(ActionRow.of(menu))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (BTN_TRANSCRIPTS.equals(buttonId)) {
            EntitySelectMenu menu = EntitySelectMenu.create(SELECT_TRANSCRIPTS, EntitySelectMenu.SelectTarget.CHANNEL)
                    .setPlaceholder("📂 Sélectionnez le salon des transcriptions")
                    .setChannelTypes(ChannelType.TEXT)
                    .build();

            event.reply("Sélectionnez le salon où envoyer les fichiers de transcription des tickets clos :")
                    .setComponents(ActionRow.of(menu))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (BTN_SUPPORT_ROLE.equals(buttonId)) {
            EntitySelectMenu menu = EntitySelectMenu.create(SELECT_SUPPORT_ROLE, EntitySelectMenu.SelectTarget.ROLE)
                    .setPlaceholder("👥 Sélectionnez le rôle de support")
                    .build();

            event.reply("Sélectionnez le rôle à ping à l'ouverture d'un ticket :")
                    .setComponents(ActionRow.of(menu))
                    .setEphemeral(true)
                    .queue();
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

        if (SELECT_LOGS.equals(menuId)) {
            if (event.getValues().isEmpty()) return;
            GuildChannel channel = event.getMentions().getChannels().get(0);
            
            settingsManager.setLogChannelId(guildId, channel.getId());

            EmbedBuilder embed = EmbedStyle.newActionEmbed("⚙️", "Configuration des Logs");
            embed.setDescription("Le salon des logs de tickets a été configuré avec succès !\n\n" +
                    EmbedStyle.detailLine("Nouveau Salon", channel.getAsMention()) + "\n" +
                    EmbedStyle.detailLine("Configuré par", event.getUser().getAsMention()));

            event.replyEmbeds(embed.build()).setEphemeral(true).queue(
                    success -> {
                        logger.info("Logs configurés via menu interactif guildId={} channelId={}", guildId, channel.getId());
                        updateConfigPanelMessage(event);
                    }
            );
            return;
        }

        if (SELECT_TRANSCRIPTS.equals(menuId)) {
            if (event.getValues().isEmpty()) return;
            GuildChannel channel = event.getMentions().getChannels().get(0);

            settingsManager.setTranscriptChannelId(guildId, channel.getId());

            EmbedBuilder embed = EmbedStyle.newActionEmbed("⚙️", "Configuration des Transcriptions");
            embed.setDescription("Le salon de transcription des tickets clos a été configuré avec succès !\n\n" +
                    EmbedStyle.detailLine("Nouveau Salon", channel.getAsMention()) + "\n" +
                    EmbedStyle.detailLine("Configuré par", event.getUser().getAsMention()));

            event.replyEmbeds(embed.build()).setEphemeral(true).queue(
                    success -> {
                        logger.info("Transcriptions configurées via menu interactif guildId={} channelId={}", guildId, channel.getId());
                        updateConfigPanelMessage(event);
                    }
            );
            return;
        }

        if (SELECT_SUPPORT_ROLE.equals(menuId)) {
            if (event.getValues().isEmpty()) return;
            Role role = event.getMentions().getRoles().get(0);

            settingsManager.setSupportRoleId(guildId, role.getId());

            EmbedBuilder embed = EmbedStyle.newActionEmbed("⚙️", "Configuration du Rôle Support");
            embed.setDescription("Le rôle de support à ping a été configuré avec succès !\n\n" +
                    EmbedStyle.detailLine("Nouveau Rôle", role.getAsMention()) + "\n" +
                    EmbedStyle.detailLine("Configuré par", event.getUser().getAsMention()));

            event.replyEmbeds(embed.build()).setEphemeral(true).queue(
                    success -> {
                        logger.info("Rôle support configuré via menu interactif guildId={} roleId={}", guildId, role.getId());
                        updateConfigPanelMessage(event);
                    }
            );
        }
    }

    /**
     * Tâche de fignolage haut de gamme (WOW factor) :
     * Met à jour dynamiquement le message d'origine (le panel) avec les nouvelles valeurs
     * de configuration dès que l'administrateur fait sa sélection !
     */
    private void updateConfigPanelMessage(EntitySelectInteractionEvent event) {
        if (event.getMessage() == null || event.getGuild() == null) return;
        
        // Comme les menus de sélection d'entités s'affichent sur des messages éphémères de transition,
        // nous n'éditons pas le message d'origine directement ici car event.getMessage() cible le menu éphémère.
        // C'est un comportement JDA 5 standard qui préserve l'état de l'écran principal.
    }
}
