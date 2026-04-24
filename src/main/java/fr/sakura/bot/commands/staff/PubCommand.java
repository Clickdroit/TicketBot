package fr.sakura.bot.commands.staff;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PubCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(PubCommand.class);
    private final ModerationLogListener moderationLogListener;

    public PubCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "pub";
    }

    @Override
    public String getCategory() {
        return "Staff & Divers";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Envoie l'embed de publicité du serveur")
                .addOptions(
                        new OptionData(OptionType.CHANNEL, "salon", "Salon cible (défaut : salon courant)", false)
                                .setChannelTypes(ChannelType.TEXT)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("❌ Commande utilisable uniquement sur un serveur.").setEphemeral(true).queue();
            return;
        }

        TextChannel channel;
        try {
            OptionMapping channelOption = event.getOption("salon");
            channel = channelOption != null
                    ? channelOption.getAsChannel().asTextChannel()
                    : event.getChannel().asTextChannel();
        } catch (IllegalStateException e) {
            event.reply("❌ Le salon sélectionné n'est pas un salon texte.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("🌸", "L'envol des pétales...");

        embed.setDescription(
                "*Un petit cocon de douceur et de sérénité, où les discussions fleurissent dans une ambiance paisible...* ✨\n\n" +
                EmbedStyle.sectionHeader("🏮", "Dans notre jardin") + "\n" +
                EmbedStyle.bullet("Une communauté **fleurissante** et respectueuse\n") +
                EmbedStyle.bullet("Des échanges **sereins**, sans prise de tête\n") +
                EmbedStyle.bullet("Une atmosphère calme comme une **brise printanière**\n") +
                EmbedStyle.bullet("Du partage authentique en vocal et à l’écrit\n\n") +
                EmbedStyle.sectionHeader("🌙", "Notre essence") + "\n" +
                "Un sanctuaire simple pour être soi-même, s'épanouir et rencontrer des âmes merveilleuses, à son propre rythme.\n\n" +
                "🌸 **Installe-toi et laisse-toi porter...** 🌸\n\n" +
                "**Rejoindre le voyage :**\n" +
                "https://discord.gg/vXt3BZs2fh"
        );

        if (event.getGuild().getIconUrl() != null) {
            embed.setThumbnail(event.getGuild().getIconUrl());
        }

        embed.setImage("https://media.discordapp.net/attachments/1119565011728257045/1120037191146618950/sakura_banner.png");
        
        EmbedStyle.setFooter(embed, "Publicité Officielle");

        channel.sendMessageEmbeds(embed.build()).queue(
                ok -> {
                    event.reply("✅ Publicité envoyée dans " + channel.getAsMention() + ".").setEphemeral(true).queue();
                    if (event.getGuild() != null) {
                        moderationLogListener.logAction(event.getGuild(), "PUB", event.getMember(), event.getUser(), "Publicité envoyée", "Salon: <#" + channel.getId() + ">");
                    }
                },
                err -> {
                    logger.warn("/pub echec: userId={}, channelId={}", event.getUser().getId(), channel.getId(), err);
                    event.reply("❌ Impossible d'envoyer la publicité dans ce salon.").setEphemeral(true).queue();
                }
        );
    }
}
