package fr.sakura.bot.commands.info;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.util.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.EnumSet;
import java.util.stream.Collectors;

/**
 * Commande pour afficher les permissions d'un membre dans un salon.
 */
public class PermissionsCommand implements ICommand {

    @Override
    public String getName() {
        return "permissions";
    }

    @Override
    public String getCategory() {
        return "Informations";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche les permissions d'un membre dans un salon")
                .addOption(OptionType.USER, "membre", "Le membre à vérifier", true)
                .addOption(OptionType.CHANNEL, "salon", "Le salon à vérifier (défaut: salon actuel)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;

        Member target = event.getOption("membre", OptionMapping::getAsMember);
        GuildChannel channel = null;
        OptionMapping channelOption = event.getOption("salon");
        if (channelOption != null) {
            var ch = channelOption.getAsChannel();
            if (ch instanceof GuildChannel) {
                channel = (GuildChannel) ch;
            }
        } else {
            channel = event.getGuildChannel();
        }

        if (target == null) {
            event.reply("❌ Membre introuvable.").setEphemeral(true).queue();
            return;
        }

        if (channel == null) {
            event.reply("❌ Salon invalide.").setEphemeral(true).queue();
            return;
        }

        EnumSet<Permission> permissions = target.getPermissions(channel);

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("🔑", "Permissions de " + target.getUser().getName());
        embed.addField("Salon", channel.getAsMention(), true);
        embed.addField("Admin", permissions.contains(Permission.ADMINISTRATOR) ? "✅ Oui" : "❌ Non", true);

        String permsList = permissions.stream()
                .map(Permission::getName)
                .sorted()
                .collect(Collectors.joining("\n"));

        if (permsList.isEmpty()) permsList = "*Aucune*";
        if (permsList.length() > 1024) permsList = permsList.substring(0, 1021) + "...";
        
        embed.addField("Liste des permissions", "```\n" + permsList + "\n```", false);
        
        event.replyEmbeds(embed.build()).queue();
    }
}
