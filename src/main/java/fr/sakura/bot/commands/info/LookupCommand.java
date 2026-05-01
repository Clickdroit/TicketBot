package fr.sakura.bot.commands.info;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.util.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Commande pour obtenir des informations sur un utilisateur Discord via son ID,
 * même s'il n'est pas présent sur le serveur.
 */
public class LookupCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(LookupCommand.class);

    @Override
    public String getName() {
        return "lookup";
    }

    @Override
    public String getCategory() {
        return "Informations";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Cherche un utilisateur Discord par son ID (même hors serveur)")
                .addOptions(new OptionData(OptionType.STRING, "id", "L'ID Discord de l'utilisateur", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userId = event.getOption("id", OptionMapping::getAsString);
        if (userId == null || userId.isBlank()) return;

        // On répond de manière différée car retrieveUserById fait un appel API réseau
        event.deferReply().queue();

        event.getJDA().retrieveUserById(userId).queue(
                user -> {
                    EmbedBuilder embed = EmbedStyle.newInfoEmbed("🔍", "Recherche d'utilisateur");
                    embed.setThumbnail(user.getEffectiveAvatarUrl());
                    
                    embed.addField("👤 Nom d'affichage", user.getEffectiveName(), true);
                    embed.addField("🏷️ Tag / Pseudo", user.getName(), true);
                    embed.addField("🆔 ID", "`" + user.getId() + "`", true);
                    embed.addField("🤖 Bot", user.isBot() ? "Oui" : "Non", true);
                    embed.addField("📅 Création du compte", "<t:" + user.getTimeCreated().toEpochSecond() + ":D> (<t:" + user.getTimeCreated().toEpochSecond() + ":R>)", false);
                    
                    String bannerUrl = user.retrieveProfile().complete().getBannerUrl();
                    if (bannerUrl != null) {
                        embed.setImage(bannerUrl + "?size=512");
                    }

                    event.getHook().sendMessageEmbeds(embed.build()).queue();
                },
                error -> {
                    logger.warn("Échec du lookup pour l'ID {} : {}", userId, error.getMessage());
                    event.getHook().sendMessage("❌ Impossible de trouver un utilisateur avec l'ID `" + userId + "`. Assurez-vous que l'ID est correct.").queue();
                }
        );
    }
}
