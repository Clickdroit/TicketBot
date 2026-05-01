package fr.sakura.bot.commands.staff;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Commande d'annonce enrichie avec mention et salon cible.
 */
public class AnnounceCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(AnnounceCommand.class);
    private final ModerationLogListener moderationLogListener;

    public AnnounceCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "announce";
    }

    @Override
    public String getCategory() {
        return "Staff & Divers";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Envoie une annonce stylisée dans un salon spécifique")
                .addOptions(
                        new OptionData(OptionType.CHANNEL, "salon", "Le salon où envoyer l'annonce", true),
                        new OptionData(OptionType.STRING, "message", "Le contenu de l'annonce", true).setMaxLength(1900),
                        new OptionData(OptionType.ROLE, "mention", "Rôle à mentionner (optionnel)", false)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        GuildMessageChannel targetChannel = event.getOption("salon", OptionMapping::getAsChannel).asGuildMessageChannel();
        String message = event.getOption("message", OptionMapping::getAsString);
        Role mentionRole = event.getOption("mention", OptionMapping::getAsRole);

        if (targetChannel == null || message == null) return;

        String finalMessage = (mentionRole != null ? mentionRole.getAsMention() + "\n\n" : "") + message;

        targetChannel.sendMessage(finalMessage).queue(
                success -> {
                    event.reply("✅ Annonce publiée dans " + targetChannel.getAsMention() + ".").setEphemeral(true).queue();
                    moderationLogListener.logAction(event.getGuild(), "ANNOUNCE", event.getMember(), "Annonce publiée", "Salon: " + targetChannel.getAsMention() + (mentionRole != null ? "\nMention: " + mentionRole.getName() : ""));
                },
                error -> {
                    logger.error("Erreur lors de l'envoi de l'annonce", error);
                    event.reply("❌ Impossible d'envoyer l'annonce dans ce salon.").setEphemeral(true).queue();
                }
        );
    }
}
