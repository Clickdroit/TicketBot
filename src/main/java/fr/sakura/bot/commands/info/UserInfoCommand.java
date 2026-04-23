package fr.sakura.bot.commands.info;



import fr.sakura.bot.commands.ICommand;

import fr.sakura.bot.core.util.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

public class UserInfoCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(UserInfoCommand.class);

    @Override
    public String getName() {
        return "userinfo";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche les informations d'un membre")
                .addOptions(new OptionData(OptionType.USER, "membre", "Le membre ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â  inspecter", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        logger.debug("Execution /userinfo par userId={}", event.getUser().getId());
        // Si aucun membre spÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©cifiÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©, on prend l'auteur de la commande
        Member target = event.getOption("membre") != null
                ? event.getOption("membre").getAsMember()
                : event.getMember();

        if (target == null) {
            logger.warn("/userinfo cible introuvable userId={}", event.getUser().getId());
            event.reply("ÃƒÆ’Ã‚Â¢Ãƒâ€šÃ‚ÂÃƒâ€¦Ã¢â‚¬â„¢ Utilisateur introuvable.").setEphemeral(true).queue();
            return;
        }

        String roles = target.getRoles().stream()
                .map(Role::getAsMention)
                .collect(Collectors.joining(", "));
        if (roles.isEmpty()) roles = "Aucun rÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´le";

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("\uD83D\uDCCB", "Informations sur " + target.getUser().getName());
        String avatarUrl = target.getUser().getEffectiveAvatarUrl();
        if (avatarUrl != null) {
            embed.setThumbnail(avatarUrl + "?size=512");
        }

        embed.addField("\uD83D\uDCDB Pseudo", target.getUser().getName(), true);
        embed.addField("\uD83C\uDFF7ÃƒÆ’Ã‚Â¯Ãƒâ€šÃ‚Â¸Ãƒâ€šÃ‚Â Surnom", target.getNickname() != null ? target.getNickname() : "Aucun", true);
        embed.addField("\uD83E\uDD16 Bot", target.getUser().isBot() ? "Oui" : "Non", true);
        embed.addField("\uD83D\uDCC5 Compte crÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© le", EmbedStyle.formatInfoDate(target.getUser().getTimeCreated()), true);
        embed.addField("\uD83D\uDCE5 A rejoint le serveur le", EmbedStyle.formatInfoDate(target.getTimeJoined()), true);
        embed.addField("\uD83C\uDFAD RÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â´les (" + target.getRoles().size() + ")", roles, false);

        EmbedStyle.setInfoFooterWithId(embed, target.getId());

        event.replyEmbeds(embed.build()).queue();
        logger.info("/userinfo envoye cibleId={} demandeurId={}", target.getId(), event.getUser().getId());
    }
}
