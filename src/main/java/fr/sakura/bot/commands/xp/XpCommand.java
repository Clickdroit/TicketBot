package fr.sakura.bot.commands.xp;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.model.LevelProfile;
import fr.sakura.bot.core.service.LevelService;
import fr.sakura.bot.core.store.LevelStore;
import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.database.SettingsManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Commande XP regroupant top, levelcard, history et roles.
 */
public class XpCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(XpCommand.class);
    private final LevelService levelService;
    private final SettingsManager settingsManager;

    public XpCommand(LevelService levelService, SettingsManager settingsManager) {
        this.levelService = levelService;
        this.settingsManager = settingsManager;
    }

    @Override
    public String getName() {
        return "xp";
    }

    @Override
    public String getCategory() {
        return "XP & niveaux";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Gère le système d'expérience")
                .addSubcommands(
                        new SubcommandData("top", "Affiche le top des membres avec podium"),
                        new SubcommandData("card", "Affiche votre carte de niveau")
                                .addOptions(new OptionData(OptionType.USER, "membre", "Le membre cible", false)),
                        new SubcommandData("history", "Affiche l'historique de vos derniers gains d'XP")
                                .addOptions(new OptionData(OptionType.USER, "membre", "Le membre cible", false)),
                        new SubcommandData("roles", "Affiche les rôles de niveau configurés")
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;

        if (!levelService.isLevelsEnabled(event.getGuild().getId())) {
            event.reply("❌ Le système de niveaux est désactivé sur ce serveur.").setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        switch (subcommand) {
            case "top" -> handleTop(event);
            case "card" -> handleCard(event);
            case "history" -> handleHistory(event);
            case "roles" -> handleRoles(event);
        }
    }

    private void handleTop(SlashCommandInteractionEvent event) {
        List<LevelProfile> profiles = levelService.getLeaderboard(event.getGuild().getId(), 10);
        if (profiles.isEmpty()) {
            event.reply("ℹ️ Aucun membre n'a encore gagné d'XP.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("🏆", "Classement XP du serveur");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < profiles.size(); i++) {
            LevelProfile profile = profiles.get(i);
            int rank = i + 1;
            String medal = switch (rank) {
                case 1 -> "🥇";
                case 2 -> "🥈";
                case 3 -> "🥉";
                default -> "🔹";
            };
            
            Member m = event.getGuild().getMemberById(profile.userId());
            String name = m != null ? m.getEffectiveName() : "Inconnu (" + profile.userId() + ")";
            
            sb.append("**#").append(rank).append("** ").append(medal).append(" ").append(name)
              .append(" — Niv. **").append(profile.level()).append("** (").append(profile.xp()).append(" XP)\n");
        }
        
        embed.setDescription(sb.toString());
        event.replyEmbeds(embed.build()).queue();
    }

    private void handleCard(SlashCommandInteractionEvent event) {
        Member target = event.getOption("membre", event.getMember(), OptionMapping::getAsMember);
        LevelProfile profile = levelService.getProfile(event.getGuild().getId(), target.getId());

        int currentLevelFloor = levelService.getXpThresholdForLevel(profile.level());
        int nextLevelThreshold = levelService.getXpThresholdForLevel(profile.level() + 1);
        int totalToNext = nextLevelThreshold - currentLevelFloor;
        int currentProgress = (int)(profile.xp() - currentLevelFloor);
        
        double percentage = (double) currentProgress / totalToNext;
        int barLength = 20;
        int filled = (int) (percentage * barLength);
        
        StringBuilder bar = new StringBuilder("`[");
        for (int i = 0; i < barLength; i++) {
            if (i < filled) bar.append("■");
            else bar.append("□");
        }
        bar.append("]` (").append((int)(percentage * 100)).append("%)");

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("📊", "Profil XP : " + target.getEffectiveName());
        embed.setThumbnail(target.getEffectiveAvatarUrl());
        embed.addField("🎖️ Niveau", "**" + profile.level() + "**", true);
        embed.addField("✨ XP total", "**" + profile.xp() + "**", true);
        embed.addField("🎯 Prochain niveau", "**" + (nextLevelThreshold - profile.xp()) + "** XP manquants", true);
        embed.addField("📈 Progression", bar.toString() + "\n" + currentProgress + " / " + totalToNext, false);
        
        event.replyEmbeds(embed.build()).queue();
    }

    private void handleHistory(SlashCommandInteractionEvent event) {
        Member target = event.getOption("membre", event.getMember(), OptionMapping::getAsMember);
        List<LevelStore.XpHistoryEntry> history = levelService.getXpHistory(event.getGuild().getId(), target.getId(), 15);
        
        if (history.isEmpty()) {
            event.reply("ℹ️ Aucun historique trouvé pour cet utilisateur.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("⏳", "Historique XP : " + target.getEffectiveName());
        StringBuilder sb = new StringBuilder();
        
        for (LevelStore.XpHistoryEntry entry : history) {
            String time = entry.timestamp(); // ISO string
            try {
                java.time.OffsetDateTime dt = java.time.OffsetDateTime.parse(time);
                time = "<t:" + dt.toEpochSecond() + ":R>";
            } catch (Exception ignored) {}
            
            sb.append("• **+").append(entry.amount()).append("** XP — ").append(time).append("\n");
        }
        
        embed.setDescription(sb.toString());
        event.replyEmbeds(embed.build()).queue();
    }

    private void handleRoles(SlashCommandInteractionEvent event) {
        Map<Integer, String> mappings = settingsManager.getLevelRoleMappings(event.getGuild().getId());
        if (mappings.isEmpty()) {
            event.reply("ℹ️ Aucun rôle de niveau n'est configuré sur ce serveur.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("🎭", "Rôles de niveau");
        String list = mappings.entrySet().stream()
                .map(e -> "• Niveau **" + e.getKey() + "** : <@&" + e.getValue() + ">")
                .collect(Collectors.joining("\n"));
        
        embed.setDescription(list);
        event.replyEmbeds(embed.build()).queue();
    }
}
