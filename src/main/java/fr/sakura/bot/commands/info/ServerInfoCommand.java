package fr.sakura.bot.commands.info;



import fr.sakura.bot.commands.ICommand;

import fr.sakura.bot.core.util.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerInfoCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(ServerInfoCommand.class);

    @Override
    public String getName() {
        return "serverinfo";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche les informations du serveur");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            logger.warn("/serverinfo appelee hors serveur par userId={}", event.getUser().getId());
            return;
        }

        logger.debug("Execution /serverinfo par userId={} sur guildId={}", event.getUser().getId(), guild.getId());

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("\uD83C\uDF38", guild.getName());
        if (guild.getIconUrl() != null) {
            embed.setThumbnail(guild.getIconUrl());
        }

        embed.addField("\uD83D\uDC51 PropriÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©taire", guild.getOwner() != null ? guild.getOwner().getAsMention() : "Inconnu", true);
        embed.addField("\uD83D\uDC65 Membres", String.valueOf(guild.getMemberCount()), true);
        embed.addField("\uD83D\uDCAC Salons", String.valueOf(guild.getChannels().size()), true);
        embed.addField("\uD83C\uDFAD RÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´les", String.valueOf(guild.getRoles().size()), true);
        embed.addField("\uD83D\uDE00 Emojis", String.valueOf(guild.getEmojis().size()), true);
        embed.addField("\uD83D\uDD12 Niveau de vÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©rification", guild.getVerificationLevel().name(), true);
        embed.addField("\uD83D\uDCC5 CrÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© le", EmbedStyle.formatInfoDate(guild.getTimeCreated()), false);

        if (guild.getBannerUrl() != null) {
            embed.setImage(guild.getBannerUrl() + "?size=1024");
        }

        EmbedStyle.setInfoFooterWithId(embed, guild.getId());

        event.replyEmbeds(embed.build()).queue();
        logger.info("/serverinfo envoye pour guildId={} demandeurId={}", guild.getId(), event.getUser().getId());
    }
}
