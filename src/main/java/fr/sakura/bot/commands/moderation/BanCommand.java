package fr.sakura.bot.commands.moderation;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.service.TempBanService;
import fr.sakura.bot.listeners.log.ModerationLogListener;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BanCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(BanCommand.class);
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([smhd])$");
    private static final String[] PREDEFINED_REASONS = {
            "Spam / Flood",
            "Publicité non autorisée (MP/Salons)",
            "Contenu NSFW / Inapproprié",
            "Non-respect des membres / Insultes",
            "Tentative de hack / Phishing",
            "Troll / Comportement nuisible",
            "Non-respect répété du règlement"
    };

    private final ModerationLogListener moderationLogListener;
    private final TempBanService tempBanService;

    public BanCommand(ModerationLogListener moderationLogListener, TempBanService tempBanService) {
        this.moderationLogListener = moderationLogListener;
        this.tempBanService = tempBanService;
    }

    @Override
    public String getName() {
        return "ban";
    }

    @Override
    public String getCategory() {
        return "Modération";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Gère les bannissements du serveur")
                .addSubcommands(
                        new SubcommandData("add", "Bannit un membre du serveur")
                                .addOptions(
                                        new OptionData(OptionType.USER, "membre", "Le membre à bannir", true),
                                        new OptionData(OptionType.STRING, "raison", "La raison du bannissement", false).setAutoComplete(true)
                                ),
                        new SubcommandData("temp", "Bannit temporairement un membre")
                                .addOptions(
                                        new OptionData(OptionType.USER, "membre", "Le membre à bannir", true),
                                        new OptionData(OptionType.STRING, "duree", "Durée (ex: 1h, 1d, 30m)", true),
                                        new OptionData(OptionType.STRING, "raison", "La raison du bannissement", false).setAutoComplete(true)
                                ),
                        new SubcommandData("mass", "Bannit plusieurs utilisateurs via leurs IDs")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "ids", "Les IDs séparés par des espaces", true),
                                        new OptionData(OptionType.STRING, "raison", "La raison du bannissement collectif", false)
                                ),
                        new SubcommandData("list", "Affiche les membres actuellement bannis du serveur")
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS));
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        String sub = event.getSubcommandName();
        if (sub == null) return;
        
        if ((sub.equals("add") || sub.equals("temp")) && event.getFocusedOption().getName().equals("raison")) {
            List<Command.Choice> options = Stream.of(PREDEFINED_REASONS)
                    .filter(word -> word.toLowerCase().startsWith(event.getFocusedOption().getValue().toLowerCase()))
                    .map(word -> new Command.Choice(word, word))
                    .collect(Collectors.toList());
            event.replyChoices(options).queue();
        }
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        if (event.getGuild() == null) {
            event.reply("❌ Cette commande doit être utilisée dans un serveur.").setEphemeral(true).queue();
            return;
        }

        switch (subcommand) {
            case "add" -> handleAdd(event);
            case "temp" -> handleTemp(event);
            case "mass" -> handleMass(event);
            case "list" -> handleList(event);
        }
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        logger.debug("Execution /ban add par userId={}", event.getUser().getId());
        var guild = event.getGuild();

        OptionMapping memberOption = event.getOption("membre");
        OptionMapping reasonOption = event.getOption("raison");
        String reason = reasonOption != null ? reasonOption.getAsString() : "Aucune raison spécifiée";

        if (memberOption == null) {
            event.reply("❌ Membre introuvable.").setEphemeral(true).queue();
            return;
        }

        User targetUser = memberOption.getAsUser();
        Member target = guild.getMember(targetUser);

        if (target == null) {
            guild.ban(targetUser, 0, TimeUnit.SECONDS).reason(reason).queue(
                    success -> {
                        event.reply("✅ **" + targetUser.getName() + "** a été banni (hors serveur). Raison : " + reason).queue();
                        moderationLogListener.logAction(event.getGuild(), "BAN", event.getMember(), targetUser, reason, "(hors serveur)");
                    },
                    error -> event.reply("❌ Impossible de bannir cet utilisateur.").setEphemeral(true).queue()
            );
            return;
        }

        if (event.getMember() == null || !event.getMember().canInteract(target)) {
            event.reply("❌ Vous ne pouvez pas bannir cet utilisateur (rôle supérieur).").setEphemeral(true).queue();
            return;
        }

        guild.ban(target, 0, TimeUnit.SECONDS).reason(reason).queue(
                success -> {
                    event.reply("✅ **" + target.getUser().getName() + "** a été banni. Raison : " + reason).queue();
                    moderationLogListener.logAction(event.getGuild(), "BAN", event.getMember(), target, reason, null);
                },
                error -> event.reply("❌ Une erreur est survenue (Ai-je les bonnes permissions ?).").setEphemeral(true).queue()
        );
    }

    private void handleTemp(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("membre").getAsUser();
        String dureeStr = event.getOption("duree").getAsString();
        String reason = event.getOption("raison") != null ? event.getOption("raison").getAsString() : "Aucune raison spécifiée";

        long durationMs = parseDuration(dureeStr);
        if (durationMs <= 0) {
            event.reply("❌ Format de durée invalide (ex: 1h, 1d).").setEphemeral(true).queue();
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

    private void handleMass(SlashCommandInteractionEvent event) {
        String idsString = event.getOption("ids", OptionMapping::getAsString);
        String reason = event.getOption("raison", "Massban (Raid/Abus)", OptionMapping::getAsString);

        if (idsString == null || idsString.isBlank()) {
            event.reply("❌ Aucun ID fourni.").setEphemeral(true).queue();
            return;
        }

        String[] ids = idsString.split("\\s+");
        event.deferReply().queue();

        int successCount = 0;
        int failCount = 0;

        for (String id : ids) {
            try {
                event.getGuild().ban(UserSnowflake.fromId(id), 0, TimeUnit.DAYS).reason(reason).complete();
                successCount++;
            } catch (Exception e) {
                logger.error("Erreur lors du massban pour l'ID {}", id, e);
                failCount++;
            }
        }

        event.getHook().sendMessage("✅ Massban terminé.\n• **Succès :** " + successCount + "\n• **Échecs :** " + failCount).queue();
        moderationLogListener.logAction(event.getGuild(), "MASSBAN", event.getMember(), reason, "Utilisateurs bannis : " + successCount);
    }

    private void handleList(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        event.getGuild().retrieveBanList().queue(bans -> {
            if (bans.isEmpty()) {
                event.getHook().sendMessage("ℹ️ Aucun membre n'est banni de ce serveur.").queue();
                return;
            }

            net.dv8tion.jda.api.EmbedBuilder embed = fr.sakura.bot.core.util.EmbedStyle.newInfoEmbed("🔨", "Liste des bannissements (" + bans.size() + ")");
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (net.dv8tion.jda.api.entities.Guild.Ban ban : bans) {
                if (count >= 15) {
                    sb.append("\n*Et ").append(bans.size() - 15).append(" autres...*");
                    break;
                }
                sb.append("• **").append(ban.getUser().getName()).append("** (").append(ban.getUser().getId()).append(")\n")
                  .append("  └ Raison : ").append(ban.getReason() != null ? ban.getReason() : "Aucune").append("\n");
                count++;
            }
            embed.setDescription(sb.toString());
            event.getHook().sendMessageEmbeds(embed.build()).queue();
        }, error -> event.getHook().sendMessage("❌ Impossible de récupérer la liste des bannissements.").queue());
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

