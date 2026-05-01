package fr.sakura.bot.commands.moderation;



import fr.sakura.bot.commands.ICommand;

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
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BanCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(BanCommand.class);
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

    public BanCommand(ModerationLogListener moderationLogListener) {
        this.moderationLogListener = moderationLogListener;
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
                        new SubcommandData("list", "Affiche les membres actuellement bannis du serveur")
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS));
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        if (event.getSubcommandName() != null && event.getSubcommandName().equals("add") && event.getFocusedOption().getName().equals("raison")) {
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

        // L'utilisateur a peut-être quitté le serveur : fallback sur User pour quand même bannir
        if (target == null) {
            logger.warn("/ban add cible non membre du serveur, tentative ban par userId={} demandeurId={}",
                    targetUser.getId(), event.getUser().getId());

            guild.ban(targetUser, 0, TimeUnit.SECONDS).reason(reason).queue(
                    success -> {
                        event.reply("✅ **" + targetUser.getName() + "** a été banni (hors serveur). Raison : " + reason).queue();
                        logger.info("/ban add reussi (hors serveur): modId={}, targetId={}", event.getUser().getId(), targetUser.getId());
                        moderationLogListener.logAction(event.getGuild(), "BAN", event.getMember(), targetUser, reason, "(hors serveur)");
                    },
                    error -> {
                        logger.error("/ban add echec API (hors serveur): modId={}, targetId={}", event.getUser().getId(), targetUser.getId(), error);
                        event.reply("❌ Impossible de bannir cet utilisateur.").setEphemeral(true).queue();
                    }
            );
            return;
        }

        if (event.getMember() == null || !event.getMember().canInteract(target)) {
            logger.warn("/ban add refuse hierarchie: modId={}, targetId={}", event.getUser().getId(), target.getId());
            event.reply("❌ Vous ne pouvez pas bannir cet utilisateur (rôle supérieur).").setEphemeral(true).queue();
            return;
        }

        logger.info("/ban add demande: modId={}, targetId={}, reason={}", event.getUser().getId(), target.getId(), reason);

        guild.ban(target, 0, TimeUnit.SECONDS).reason(reason).queue(
                success -> {
                    event.reply("✅ **" + target.getUser().getName() + "** a été banni. Raison : " + reason).queue();
                    logger.info("/ban add reussi: modId={}, targetId={}", event.getUser().getId(), target.getId());

                    moderationLogListener.logAction(event.getGuild(), "BAN", event.getMember(), target, reason, null);
                },
                error -> {
                    logger.error("/ban add echec API: modId={}, targetId={}", event.getUser().getId(), target.getId(), error);
                    event.reply("❌ Une erreur est survenue (Ai-je les bonnes permissions ?).").setEphemeral(true).queue();
                }
        );
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
            
            // On limite à 10 pour l'affichage initial pour éviter de dépasser les limites d'embed
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
        }, error -> {
            logger.error("Erreur lors de la récupération de la liste des bans", error);
            event.getHook().sendMessage("❌ Impossible de récupérer la liste des bannissements.").queue();
        });
    }
}
