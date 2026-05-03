package fr.sakura.bot.commands.staff;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import fr.sakura.bot.listeners.WelcomeListener;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
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

import java.awt.Color;
import java.util.Locale;
import java.util.Map;

/**
 * Commande centralisée pour l'envoi de messages et d'embeds.
 */
public class MessageCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(MessageCommand.class);
    private static final Map<String, Color> NAMED_COLORS = Map.of(
            "sakura", new Color(255, 168, 204),
            "rouge", Color.RED,
            "bleu", Color.BLUE,
            "vert", Color.GREEN,
            "jaune", Color.YELLOW,
            "noir", Color.BLACK,
            "blanc", Color.WHITE
    );

    private final ModerationLogListener moderationLogListener;

    public MessageCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "message";
    }

    @Override
    public String getCategory() {
        return "Staff";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Gère l'envoi de messages et d'embeds")
                .addSubcommands(
                        new SubcommandData("say", "Envoie un message simple")
                                .addOption(OptionType.STRING, "message", "Le texte à envoyer", true),
                        new SubcommandData("embed", "Envoie un embed personnalisé")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "titre", "Titre", true),
                                        new OptionData(OptionType.STRING, "description", "Description", true),
                                        new OptionData(OptionType.STRING, "couleur", "Hex ou nom (sakura...)", false),
                                        new OptionData(OptionType.CHANNEL, "salon", "Salon cible", false).setChannelTypes(ChannelType.TEXT)
                                ),
                        new SubcommandData("announce", "Envoie une annonce avec mention")
                                .addOptions(
                                        new OptionData(OptionType.CHANNEL, "salon", "Salon cible", true),
                                        new OptionData(OptionType.STRING, "message", "Contenu", true),
                                        new OptionData(OptionType.ROLE, "mention", "Rôle à mentionner", false)
                                ),
                        new SubcommandData("broadcast", "Diffuse un message dans plusieurs salons")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "message", "Message", true),
                                        new OptionData(OptionType.STRING, "salons", "IDs séparés par des espaces", true)
                                ),
                        new SubcommandData("pub", "Envoie l'embed de publicité"),
                        new SubcommandData("condition", "Envoie les conditions de partenariat"),
                        new SubcommandData("reglements", "Envoie le règlement officiel")
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String sub = event.getSubcommandName();
        if (sub == null) return;

        switch (sub) {
            case "say" -> handleSay(event);
            case "embed" -> handleEmbed(event);
            case "announce" -> handleAnnounce(event);
            case "broadcast" -> handleBroadcast(event);
            case "pub" -> handlePub(event);
            case "condition" -> handleCondition(event);
            case "reglements" -> handleReglements(event);
        }
    }

    private void handleSay(SlashCommandInteractionEvent event) {
        String msg = event.getOption("message", "", OptionMapping::getAsString);
        event.getChannel().sendMessage(msg).queue();
        event.reply("✅ Envoyé.").setEphemeral(true).queue();
    }

    private void handleEmbed(SlashCommandInteractionEvent event) {
        TextChannel channel = event.getOption("salon") != null ? event.getOption("salon").getAsChannel().asTextChannel() : event.getChannel().asTextChannel();
        String title = event.getOption("titre").getAsString();
        String desc = event.getOption("description").getAsString();
        String color = event.getOption("couleur", "", OptionMapping::getAsString);

        EmbedBuilder eb = new EmbedBuilder().setTitle(title).setDescription(desc).setColor(parseColor(color));
        channel.sendMessageEmbeds(eb.build()).queue();
        event.reply("✅ Embed envoyé.").setEphemeral(true).queue();
    }

    private void handleAnnounce(SlashCommandInteractionEvent event) {
        GuildMessageChannel channel = event.getOption("salon").getAsChannel().asGuildMessageChannel();
        String msg = event.getOption("message").getAsString();
        Role role = event.getOption("mention", OptionMapping::getAsRole);

        String finalMsg = (role != null ? role.getAsMention() + "\n\n" : "") + msg;
        channel.sendMessage(finalMsg).queue();
        event.reply("✅ Annonce envoyée.").setEphemeral(true).queue();
    }

    private void handleBroadcast(SlashCommandInteractionEvent event) {
        String msg = event.getOption("message").getAsString();
        String ids = event.getOption("salons").getAsString();
        event.deferReply(true).queue();

        int count = 0;
        for (String id : ids.split("\\s+")) {
            GuildMessageChannel ch = event.getGuild().getChannelById(GuildMessageChannel.class, id);
            if (ch != null) { ch.sendMessage(msg).queue(); count++; }
        }
        event.getHook().sendMessage("✅ Diffusé dans " + count + " salons.").queue();
    }

    private void handlePub(SlashCommandInteractionEvent event) {
        EmbedBuilder eb = EmbedStyle.newInfoEmbed("🌸", "L'envol des pétales...");
        eb.setDescription("*Un petit cocon de douceur...*\n\n**Rejoindre :** https://discord.gg/vXt3BZs2fh");
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
        event.reply("✅ Pub envoyée.").setEphemeral(true).queue();
    }

    private void handleCondition(SlashCommandInteractionEvent event) {
        EmbedBuilder eb = EmbedStyle.newInfoEmbed("🤝", "Alliances & Partenariats");
        eb.setDescription("*Pour préserver l'harmonie de notre jardin...*");
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
        event.reply("✅ Conditions envoyées.").setEphemeral(true).queue();
    }

    private void handleReglements(SlashCommandInteractionEvent event) {
        EmbedBuilder eb = EmbedStyle.newInfoEmbed("📜", "Règlement officiel");
        eb.setDescription("Bienvenue sur **Sakura** !");
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
        event.reply("✅ Règlement envoyé.").setEphemeral(true).queue();
    }

    private Color parseColor(String input) {
        if (input == null || input.isBlank()) return new Color(255, 168, 204);
        Color named = NAMED_COLORS.get(input.toLowerCase());
        if (named != null) return named;
        try { return Color.decode(input.startsWith("#") ? input : "#" + input); }
        catch (Exception e) { return new Color(255, 168, 204); }
    }
}

