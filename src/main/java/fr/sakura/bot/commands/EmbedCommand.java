package fr.sakura.bot.commands;

import fr.sakura.bot.listeners.WelcomeListener;
import fr.sakura.bot.utils.EmbedStyle;
import fr.sakura.bot.utils.ModerationLogger;
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

import java.awt.Color;
import java.util.Locale;
import java.util.Map;

public class EmbedCommand implements ICommand {

    private static final Map<String, Color> NAMED_COLORS = Map.of(
            "sakura", new Color(255, 168, 204),
            "rouge", new Color(220, 20, 60),
            "vert", new Color(46, 204, 113),
            "bleu", new Color(52, 152, 219),
            "orange", new Color(230, 126, 34),
            "violet", new Color(155, 89, 182),
            "gris", new Color(149, 165, 166)
    );

    private final ModerationLogger moderationLogger;

    public EmbedCommand(ModerationLogger moderationLogger) {
        this.moderationLogger = moderationLogger;
    }

    @Override
    public String getName() {
        return "embed";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Envoie un embed personnalisé")
                .addOptions(
                        new OptionData(OptionType.STRING, "titre", "Titre de l'embed", true).setMaxLength(256),
                        new OptionData(OptionType.STRING, "description", "Description de l'embed", true).setMaxLength(2000),
                        new OptionData(OptionType.STRING, "couleur", "Hex (#RRGGBB) ou nom (sakura, rouge, vert, bleu...)"),
                        new OptionData(OptionType.STRING, "image", "URL HTTPS d'image"),
                        new OptionData(OptionType.STRING, "miniature", "URL HTTPS de miniature"),
                        new OptionData(OptionType.STRING, "footer", "Texte du footer"),
                        new OptionData(OptionType.CHANNEL, "salon", "Salon cible").setChannelTypes(ChannelType.TEXT)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("❌ Commande utilisable uniquement sur un serveur.").setEphemeral(true).queue();
            return;
        }

        OptionMapping channelOption = event.getOption("salon");
        TextChannel channel = channelOption != null ? channelOption.getAsChannel().asTextChannel() : event.getChannel().asTextChannel();
        String title = event.getOption("titre", "", OptionMapping::getAsString);
        String description = event.getOption("description", "", OptionMapping::getAsString);
        String colorInput = event.getOption("couleur", "", OptionMapping::getAsString);
        String image = event.getOption("image", "", OptionMapping::getAsString).trim();
        String thumbnail = event.getOption("miniature", "", OptionMapping::getAsString).trim();
        String footer = event.getOption("footer", "", OptionMapping::getAsString).trim();

        if (!image.isBlank() && !WelcomeListener.isValidHttpsUrl(image)) {
            event.reply("❌ URL d'image invalide (HTTPS requis).").setEphemeral(true).queue();
            return;
        }
        if (!thumbnail.isBlank() && !WelcomeListener.isValidHttpsUrl(thumbnail)) {
            event.reply("❌ URL de miniature invalide (HTTPS requis).").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmbedStyle.truncate(title, 256));
        embed.setDescription(EmbedStyle.truncate(description, 4000));
        embed.setColor(parseColor(colorInput));
        if (!image.isBlank()) {
            embed.setImage(image);
        }
        if (!thumbnail.isBlank()) {
            embed.setThumbnail(thumbnail);
        }
        if (!footer.isBlank()) {
            EmbedStyle.setFooter(embed, footer);
        } else {
            EmbedStyle.setFooter(embed, "Embed staff");
        }

        channel.sendMessageEmbeds(embed.build()).queue(
                ok -> {
                    event.reply("✅ Embed envoyé dans " + channel.getAsMention() + ".").setEphemeral(true).queue();
                    moderationLogger.logInGuild(
                            event.getGuild(),
                            "EMBED",
                            event.getMember(),
                            null,
                            "Embed personnalisé publié",
                            "Salon: " + channel.getAsMention() + "\nTitre: " + EmbedStyle.truncate(title, 120)
                    );
                },
                err -> event.reply("❌ Impossible d'envoyer l'embed dans ce salon.").setEphemeral(true).queue()
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
                return new Color(255, 168, 204);
            }
        }
        return new Color(255, 168, 204);
    }
}
