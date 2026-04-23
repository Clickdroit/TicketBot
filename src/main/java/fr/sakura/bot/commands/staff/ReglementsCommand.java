package fr.sakura.bot.commands.staff;



import fr.sakura.bot.commands.ICommand;

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
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReglementsCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(ReglementsCommand.class);

    @Override
    public String getName() {
        return "reglements";
    }

    @Override
    public String getCategory() {
        return "Staff & Divers";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Envoie le règlement officiel du serveur")
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

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("📜", "Règlement officiel");

        embed.setDescription(
                "Bienvenue sur **Sakura** !\n" +
                        "En rejoignant ce serveur, tu acceptes les règles ci-dessous.\n" +
                        "Le non-respect entraîne des sanctions immédiates."
        );

        embed.addField(
                "🌸 I ❌ Respect",
                "Respectez tout le monde, sans exception.\n" +
                        "➤ Les discriminations et insultes entraînent un **ban immédiat et définitif**.",
                false
        );

        embed.addField(
                "🌸 II ❌ Contenu",
                "Aucun contenu NSFW, choquant ou illégal n'est toléré.\n" +
                        "➤ Un langage correct est exigé en toutes circonstances.",
                false
        );

        embed.addField(
                "🌸 III ❌ Spam & Pub",
                "Pas de spam, flood ou abus de mentions.\n" +
                        "➤ Toute publicité est **interdite** sans accord préalable du staff.",
                false
        );

        embed.addField(
                "🌸 IV ❌ Sanctions",
                "❌ ❌ Avertissement → warn\n" +
                        "❌ Répétition → mute temporaire\n" +
                        "👢 Grave → kick\n" +
                        "❌ Très grave → ban définitif",
                false
        );

        // Champ vide pour espacer visuellement avant le message de fin
        embed.addField("\u200B", "🌸 Bonne aventure sur **Sakura** ! 🌸", false);

        EmbedStyle.setFooter(embed, "Embed staff");

        channel.sendMessageEmbeds(embed.build()).queue(
                ok -> {
                    event.reply("✅ Règlement envoyé dans " + channel.getAsMention() + ".").setEphemeral(true).queue();
                    logger.info("/reglements envoye: userId={}, channelId={}", event.getUser().getId(), channel.getId());
                },
                err -> {
                    logger.warn("/reglements echec: userId={}, channelId={}", event.getUser().getId(), channel.getId(), err);
                    event.reply("❌ Impossible d'envoyer le règlement dans ce salon.").setEphemeral(true).queue();
                }
        );
    }
}