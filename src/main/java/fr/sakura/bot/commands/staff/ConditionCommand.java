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

public class ConditionCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(ConditionCommand.class);
    private final ModerationLogListener moderationLogListener;

    public ConditionCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "condition";
    }

    @Override
    public String getCategory() {
        return "Staff & Divers";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche les conditions de partenariat du serveur")
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

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("🤝", "Alliances & Partenariats");

        embed.setDescription(
                "*Pour préserver l’harmonie de notre jardin et tisser des liens durables, certaines règles doivent être respectées...*\n\n" +
                EmbedStyle.sectionHeader("🌸", "Nos Critères") + "\n" +
                EmbedStyle.bullet("**Esprit Communautaire :** Serveur actif avec une présence réelle.\n") +
                EmbedStyle.bullet("**Thématique :** Univers compatible (Chill, Anime, Gaming, Entraide).\n") +
                EmbedStyle.bullet("**Sérénité :** Respect et ambiance saine obligatoires.\n") +
                EmbedStyle.bullet("**Sécurité :** Aucun contenu toxique, NSFW ou problématique.\n") +
                EmbedStyle.bullet("**Équilibre :** Échange équitable et mutuel.\n") +
                EmbedStyle.bullet("**Esthétique :** Serveur organisé, agréable et soigné.\n\n") +
                EmbedStyle.sectionHeader("📜", "Notes") + "\n" +
                "*Chaque demande sera étudiée avec une attention particulière par notre équipe...*\n\n" +
                "💫 **Que cette alliance soit bénéfique à nos deux royaumes !** 💫"
        );

        // Intégration de ta bannière GIF
        embed.setImage("https://cdn.discordapp.com/attachments/1495014772452622416/1496923126993260555/anime-gif-collection-v0-x7rt5z1qx90e1.gif?ex=69eba5cc&is=69ea544c&hm=66c99aa897b96b2338907cd55819e5fae24222445923e269c805e99bd11b6b2b&");
        
        EmbedStyle.setFooter(embed, "Relations Diplomatiques");

        channel.sendMessageEmbeds(embed.build()).queue(
                ok -> {
                    event.reply("✅ Conditions de partenariat envoyées dans " + channel.getAsMention() + ".").setEphemeral(true).queue();
                    if (event.getGuild() != null) {
                        moderationLogListener.logAction(event.getGuild(), "CONDITION", event.getMember(), event.getUser(), "Conditions de partenariat publiées", "Salon: <#" + channel.getId() + ">");
                    }
                },
                err -> {
                    logger.warn("/condition echec: userId={}, channelId={}", event.getUser().getId(), channel.getId(), err);
                    event.reply("❌ Impossible d'envoyer les conditions dans ce salon.").setEphemeral(true).queue();
                }
        );
    }
}
