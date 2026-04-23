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
        return Commands.slash(getName(), "Envoie le rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¨glement officiel du serveur")
                .addOptions(
                        new OptionData(OptionType.CHANNEL, "salon", "Salon cible (dÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©faut : salon courant)", false)
                                .setChannelTypes(ChannelType.TEXT)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Commande utilisable uniquement sur un serveur.").setEphemeral(true).queue();
            return;
        }

        TextChannel channel;
        try {
            OptionMapping channelOption = event.getOption("salon");
            channel = channelOption != null
                    ? channelOption.getAsChannel().asTextChannel()
                    : event.getChannel().asTextChannel();
        } catch (IllegalStateException e) {
            event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Le salon sÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©lectionnÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© n'est pas un salon texte.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒâ€¦Ã¢â‚¬Å“", "RÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¨glement officiel");

        embed.setDescription(
                "Bienvenue sur **Sakura** !\n" +
                        "En rejoignant ce serveur, tu acceptes les rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¨gles ci-dessous.\n" +
                        "Le non-respect entraÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â®ne des sanctions immÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©diates."
        );

        embed.addField(
                "ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã¢â‚¬â„¢Ãƒâ€šÃ‚Â¸ I ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â Respect",
                "Respectez tout le monde, sans exception.\n" +
                        "ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¾Ãƒâ€šÃ‚Â¥ Les discriminations et insultes entraÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â®nent un **ban immÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©diat et dÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©finitif**.",
                false
        );

        embed.addField(
                "ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã¢â‚¬â„¢Ãƒâ€šÃ‚Â¸ II ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â Contenu",
                "Aucun contenu NSFW, choquant ou illÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©gal n'est tolÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©.\n" +
                        "ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¾Ãƒâ€šÃ‚Â¥ Un langage correct est exigÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© en toutes circonstances.",
                false
        );

        embed.addField(
                "ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã¢â‚¬â„¢Ãƒâ€šÃ‚Â¸ III ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â Spam & Pub",
                "Pas de spam, flood ou abus de mentions.\n" +
                        "ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¾Ãƒâ€šÃ‚Â¥ Toute publicitÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© est **interdite** sans accord prÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©alable du staff.",
                false
        );

        embed.addField(
                "ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã¢â‚¬â„¢Ãƒâ€šÃ‚Â¸ IV ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â Sanctions",
                "ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã‚Â¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Avertissement ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ warn\n" +
                        "ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¡ RÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©pÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©tition ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ mute temporaire\n" +
                        "ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‹Å“Ãƒâ€šÃ‚Â¢ Grave ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ kick\n" +
                        "ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒâ€šÃ‚Â¨ TrÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¨s grave ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ ban dÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©finitif",
                false
        );

        // Champ vide pour espacer visuellement avant le message de fin
        embed.addField("\u200B", "ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“Ãƒâ€šÃ‚Â¦ Bonne aventure sur **Sakura** ! ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“Ãƒâ€šÃ‚Â¦", false);

        EmbedStyle.setFooter(embed, "Embed staff");

        channel.sendMessageEmbeds(embed.build()).queue(
                ok -> {
                    event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ RÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¨glement envoyÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© dans " + channel.getAsMention() + ".").setEphemeral(true).queue();
                    logger.info("/reglements envoye: userId={}, channelId={}", event.getUser().getId(), channel.getId());
                },
                err -> {
                    logger.warn("/reglements echec: userId={}, channelId={}", event.getUser().getId(), channel.getId(), err);
                    event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Impossible d'envoyer le rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¨glement dans ce salon.").setEphemeral(true).queue();
                }
        );
    }
}