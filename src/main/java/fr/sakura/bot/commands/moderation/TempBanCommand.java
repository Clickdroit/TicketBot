package fr.sakura.bot.commands.moderation;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.service.TempBanService;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TempBanCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(TempBanCommand.class);
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([smhd])$");
    private static final String[] PREDEFINED_REASONS = {
            "Spam / Flood",
            "Publicité non autorisée (MP/Salons)",
            "Contenu NSFW / Inapproprié",
            "Non-respect des membres / Insultes",
            "Troll / Comportement nuisible"
    };

    private final TempBanService tempBanService;
    private final ModerationLogListener moderationLogListener;

    public TempBanCommand(TempBanService tempBanService, ModerationLogListener moderationLogListener) {
        this.tempBanService = tempBanService;
        this.moderationLogListener = moderationLogListener;
    }

    @Override
    public String getName() {
        return "tempban";
    }

    @Override
    public String getCategory() {
        return "Modération";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Bannit temporairement un membre du serveur")
                .addOptions(
                        new OptionData(OptionType.USER, "membre", "Le membre à bannir", true),
                        new OptionData(OptionType.STRING, "duree", "Durée (ex: 1h, 1d, 30m)", true),
                        new OptionData(OptionType.STRING, "raison", "La raison du bannissement", false).setAutoComplete(true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS));
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        if (event.getFocusedOption().getName().equals("raison")) {
            List<Command.Choice> options = Stream.of(PREDEFINED_REASONS)
                    .filter(word -> word.toLowerCase().startsWith(event.getFocusedOption().getValue().toLowerCase()))
                    .map(word -> new Command.Choice(word, word))
                    .collect(Collectors.toList());
            event.replyChoices(options).queue();
        }
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;

        User targetUser = event.getOption("membre").getAsUser();
        String dureeStr = event.getOption("duree").getAsString();
        String reason = event.getOption("raison") != null ? event.getOption("raison").getAsString() : "Aucune raison spécifiée";

        long durationMs = parseDuration(dureeStr);
        if (durationMs <= 0) {
            event.reply("❌ Format de durée invalide. Utilisez `s` (sec), `m` (min), `h` (heures), `d` (jours). Ex: `1h`").setEphemeral(true).queue();
            return;
        }

        Member target = event.getGuild().getMember(targetUser);
        if (target != null && !event.getMember().canInteract(target)) {
            event.reply("❌ Vous ne pouvez pas bannir cet utilisateur (hiérarchie).").setEphemeral(true).queue();
            return;
        }

        tempBanService.addTempBan(event.getGuild(), targetUser, durationMs, reason);
        
        event.reply("✅ **" + targetUser.getName() + "** a été banni temporairement pour **" + dureeStr + "**. Raison : " + reason).queue();
        moderationLogListener.logAction(event.getGuild(), "BAN", event.getMember(), targetUser, reason, "(Temporaire: " + dureeStr + ")");
    }

    private long parseDuration(String input) {
        Matcher matcher = DURATION_PATTERN.matcher(input.toLowerCase());
        if (!matcher.matches()) return -1;

        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        return switch (unit) {
            case "s" -> value * 1000;
            case "m" -> value * 60000;
            case "h" -> value * 3600000;
            case "d" -> value * 86400000;
            default -> -1;
        };
    }
}
