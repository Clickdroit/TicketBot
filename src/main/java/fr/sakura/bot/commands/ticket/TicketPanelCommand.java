package fr.sakura.bot.commands.ticket;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.core.service.TicketService;
import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.core.model.TicketCategory;
import fr.sakura.bot.core.model.PanelEntry;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TicketPanelCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(TicketPanelCommand.class);
    private final TicketService ticketService;
    private final SettingsManager settingsManager;

    public TicketPanelCommand(TicketService ticketService, SettingsManager settingsManager) {
        this.ticketService = ticketService;
        this.settingsManager = settingsManager;
    }

    @Override
    public String getName() {
        return "ticketpanel";
    }

    @Override
    public String getCategory() {
        return "Tickets";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche le panel de tickets")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_CHANNEL));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("❌ Commande utilisable uniquement sur un serveur.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = EmbedStyle.newActionEmbed("🎫", "Système de Support");
        embed.setAuthor("Support • " + event.getGuild().getName(), null, event.getGuild().getIconUrl());
        
        embed.setDescription("Besoin d'aide ? Créez un ticket et notre équipe vous assistera dans les plus brefs délais.\n\n" + 
                EmbedStyle.sectionHeader("🤔", "Comment ça marche ?"));
        
        embed.addField("1️⃣ Étape 1", "Sélectionnez une catégorie ci-dessous", false);
        embed.addField("2️⃣ Étape 2", "Un salon privé sera créé pour vous", false);
        embed.addField("3️⃣ Étape 3", "Expliquez votre demande en détail", false);
        embed.addField("4️⃣ Étape 4", "Attendez qu'un membre du staff vous réponde", false);
        embed.addField("⚠️ Important", "N'ouvrez qu'un seul ticket à la fois.", false);

        if (event.getJDA().getSelfUser().getEffectiveAvatarUrl() != null) {
            embed.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl());
        }
        EmbedStyle.setFooter(embed, "Support disponible 24/7", event.getGuild().getIconUrl());

        List<TicketCategory> categories = settingsManager.getCategories(event.getGuild().getId());

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("ticket:category")
                .setPlaceholder("🎫 Choisissez une catégorie");

        for (TicketCategory cat : categories) {
            Emoji emojiObj = null;
            if (cat.emoji() != null && !cat.emoji().isBlank()) {
                try {
                    emojiObj = Emoji.fromUnicode(cat.emoji());
                } catch (Exception ignored) {}
            }

            menuBuilder.addOption(
                    cat.label(),
                    "ticket:" + cat.categoryId(),
                    cat.description() != null ? cat.description() : "",
                    emojiObj
            );
        }

        StringSelectMenu menu = menuBuilder.build();

        event.getChannel().sendMessageEmbeds(embed.build())
                .setComponents(ActionRow.of(menu))
                .queue(
                        msg -> {
                            settingsManager.savePanel(event.getGuild().getId(), msg.getChannel().getId(), msg.getId());
                            event.reply("✅ Panel de tickets envoyé.").setEphemeral(true).queue();
                            logger.info("/ticketpanel envoye guildId={} userId={}", event.getGuild().getId(), event.getUser().getId());
                        },
                        error -> {
                            logger.error("Echec envoi panel tickets guildId={}", event.getGuild().getId(), error);
                            event.reply("❌ Impossible d'envoyer le panel de tickets.").setEphemeral(true).queue();
                        }
                );
    }

    /**
     * Actualise dynamiquement tous les panels de tickets actifs envoyés sur la guilde
     * pour refléter instantanément les modifications de configuration ou de catégories.
     */
    public static void updateAllGuildPanels(Guild guild, SettingsManager settingsManager) {
        String guildId = guild.getId();
        List<PanelEntry> panels = settingsManager.getPanels(guildId);
        if (panels.isEmpty()) return;

        List<TicketCategory> categories = settingsManager.getCategories(guildId);

        EmbedBuilder embed = EmbedStyle.newActionEmbed("🎫", "Système de Support");
        embed.setAuthor("Support • " + guild.getName(), null, guild.getIconUrl());
        
        embed.setDescription("Besoin d'aide ? Créez un ticket et notre équipe vous assistera dans les plus brefs délais.\n\n" + 
                EmbedStyle.sectionHeader("🤔", "Comment ça marche ?"));
        
        embed.addField("1️⃣ Étape 1", "Sélectionnez une catégorie ci-dessous", false);
        embed.addField("2️⃣ Étape 2", "Un salon privé sera créé pour vous", false);
        embed.addField("3️⃣ Étape 3", "Expliquez votre demande en détail", false);
        embed.addField("4️⃣ Étape 4", "Attendez qu'un membre du staff vous réponde", false);
        embed.addField("⚠️ Important", "N'ouvrez qu'un seul ticket à la fois.", false);

        if (guild.getJDA().getSelfUser().getEffectiveAvatarUrl() != null) {
            embed.setThumbnail(guild.getJDA().getSelfUser().getEffectiveAvatarUrl());
        }
        EmbedStyle.setFooter(embed, "Support disponible 24/7", guild.getIconUrl());

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("ticket:category")
                .setPlaceholder("🎫 Choisissez une catégorie");

        for (TicketCategory cat : categories) {
            Emoji emojiObj = null;
            if (cat.emoji() != null && !cat.emoji().isBlank()) {
                try {
                    emojiObj = Emoji.fromUnicode(cat.emoji());
                } catch (Exception ignored) {}
            }

            menuBuilder.addOption(
                    cat.label(),
                    "ticket:" + cat.categoryId(),
                    cat.description() != null ? cat.description() : "",
                    emojiObj
            );
        }

        StringSelectMenu menu = menuBuilder.build();

        for (PanelEntry panel : panels) {
            TextChannel channel = guild.getTextChannelById(panel.channelId());
            if (channel == null) {
                settingsManager.deletePanel(guildId, panel.channelId(), panel.messageId());
                continue;
            }

            channel.retrieveMessageById(panel.messageId()).queue(
                    msg -> {
                        msg.editMessageEmbeds(embed.build())
                                .setComponents(ActionRow.of(menu))
                                .queue(
                                        success -> logger.info("Panel de tickets actualisé dynamiquement : channelId={}, msgId={}", panel.channelId(), panel.messageId()),
                                        error -> logger.error("Échec actualisation message panel : channelId={}, msgId={}", panel.channelId(), panel.messageId(), error)
                                );
                    },
                    error -> {
                        logger.warn("Message panel introuvable (supprimé ?). Nettoyage en BDD : channelId={}, msgId={}", panel.channelId(), panel.messageId());
                        settingsManager.deletePanel(guildId, panel.channelId(), panel.messageId());
                    }
            );
        }
    }
}
