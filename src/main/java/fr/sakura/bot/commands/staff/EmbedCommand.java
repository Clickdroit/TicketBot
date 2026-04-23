package fr.sakura.bot.commands.staff;



import fr.sakura.bot.commands.ICommand;

import fr.sakura.bot.listeners.WelcomeListener;
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

import java.awt.Color;
import java.util.Locale;
import java.util.Map;

public class EmbedCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(EmbedCommand.class);

    private static final Map<String, Color> NAMED_COLORS = Map.of(
            "sakura", new Color(255, 168, 204),
            "rouge",  new Color(220,  20,  60),
            "vert",   new Color( 46, 204, 113),
            "bleu",   new Color( 52, 152, 219),
            "orange", new Color(230, 126,  34),
            "violet", new Color(155,  89, 182),
            "gris",   new Color(149, 165, 166)
    );

    private final ModerationLogListener moderationLogListener;

    public EmbedCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "embed";
    }

    @Override
    public String getCategory() {
        return "Staff & Divers";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Envoie un embed personnalisÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© dans un salon")
                .addOptions(
                        new OptionData(OptionType.STRING,  "titre",       "Titre de l'embed",                        true).setMaxLength(256),
                        new OptionData(OptionType.STRING,  "description", "Description de l'embed",                  true).setMaxLength(2000),
                        new OptionData(OptionType.STRING,  "couleur",     "Hex (#RRGGBB) ou nom (sakura, rouge...)", false),
                        new OptionData(OptionType.STRING,  "image",       "URL HTTPS d'une image (banniÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¨re)",        false),
                        new OptionData(OptionType.STRING,  "miniature",   "URL HTTPS d'une miniature",               false),
                        new OptionData(OptionType.STRING,  "footer",      "Texte du footer",                         false),
                        new OptionData(OptionType.CHANNEL, "salon",       "Salon cible (dÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©faut : salon courant)",    false)
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

        // RÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©solution du salon cible avec gestion d'erreur de cast
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

        String title       = event.getOption("titre",       "",  OptionMapping::getAsString);
        String description = event.getOption("description", "",  OptionMapping::getAsString);
        String colorInput  = event.getOption("couleur",     "",  OptionMapping::getAsString);
        String image       = event.getOption("image",       "",  OptionMapping::getAsString).trim();
        String thumbnail   = event.getOption("miniature",   "",  OptionMapping::getAsString).trim();
        String footer      = event.getOption("footer",      "",  OptionMapping::getAsString).trim();

        // Validation des URLs
        if (!image.isBlank() && !WelcomeListener.isValidHttpsUrl(image)) {
            event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ URL d'image invalide (HTTPS requis).").setEphemeral(true).queue();
            return;
        }
        if (!thumbnail.isBlank() && !WelcomeListener.isValidHttpsUrl(thumbnail)) {
            event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ URL de miniature invalide (HTTPS requis).").setEphemeral(true).queue();
            return;
        }

        // Construction de l'embed
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmbedStyle.truncate(title, 256));
        embed.setDescription(EmbedStyle.truncate(description, 4000));
        embed.setColor(parseColor(colorInput));

        if (!image.isBlank())     embed.setImage(image);
        if (!thumbnail.isBlank()) embed.setThumbnail(thumbnail);

        EmbedStyle.setFooter(embed, footer.isBlank() ? "Embed staff" : footer);

        // Envoi
        channel.sendMessageEmbeds(embed.build()).queue(
                ok -> {
                    event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ Embed envoyÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© dans " + channel.getAsMention() + ".").setEphemeral(true).queue();
                    logger.info("/embed envoye: userId={}, channelId={}, title={}",
                            event.getUser().getId(), channel.getId(), EmbedStyle.truncate(title, 80));
                    moderationLogListener.logAction(
                            event.getGuild(),
                            "EMBED",
                            event.getMember(),
                            event.getUser(),
                            "Embed personnalisÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© publiÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©",
                            "Salon : " + channel.getAsMention() + "\nTitre : " + EmbedStyle.truncate(title, 120)
                    );
                },
                err -> {
                    logger.warn("/embed echec envoi: userId={}, channelId={}", event.getUser().getId(), channel.getId(), err);
                    event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Impossible d'envoyer l'embed dans ce salon (permissions manquantes ?).").setEphemeral(true).queue();
                }
        );
    }

    private Color parseColor(String input) {
        if (input == null || input.isBlank()) {
            return new Color(255, 168, 204);
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT);

        Color named = NAMED_COLORS.get(normalized);
        if (named != null) {
            return named;
        }

        String hex = normalized.startsWith("#") ? normalized.substring(1) : normalized;
        if (hex.matches("[0-9a-f]{6}")) {
            try {
                return Color.decode("#" + hex);
            } catch (NumberFormatException ignored) {
                // fallback ci-dessous
            }
        }

        return new Color(255, 168, 204);
    }
}
