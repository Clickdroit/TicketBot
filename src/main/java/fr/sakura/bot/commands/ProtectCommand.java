package fr.sakura.bot.commands;

import fr.sakura.bot.core.util.EmbedStyle;
import fr.sakura.bot.database.ProtectSettingsManager;
import fr.sakura.bot.protect.JoinRiskScorer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public class ProtectCommand implements ICommand {

    private final ProtectSettingsManager protectSettingsManager;

    public ProtectCommand(ProtectSettingsManager protectSettingsManager) {
        this.protectSettingsManager = protectSettingsManager;
    }

    @Override
    public String getName() {
        return "protect";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Configure Sakura Protect")
                .addSubcommandGroups(
                        new SubcommandGroupData("whitelist", "Gère la liste blanche utilisateurs")
                                .addSubcommands(
                                        new SubcommandData("add", "Ajoute un utilisateur à la whitelist")
                                                .addOptions(new OptionData(OptionType.USER, "utilisateur", "Utilisateur", true)),
                                        new SubcommandData("remove", "Retire un utilisateur de la whitelist")
                                                .addOptions(new OptionData(OptionType.USER, "utilisateur", "Utilisateur", true)),
                                        new SubcommandData("list", "Affiche la whitelist")
                                ),
                        new SubcommandGroupData("staffrole", "Rôles staff de confiance (exclusion protect)")
                                .addSubcommands(
                                        new SubcommandData("add", "Ajoute un rôle staff de confiance")
                                                .addOptions(new OptionData(OptionType.ROLE, "role", "Rôle staff", true)),
                                        new SubcommandData("remove", "Retire un rôle staff de confiance")
                                                .addOptions(new OptionData(OptionType.ROLE, "role", "Rôle staff", true)),
                                        new SubcommandData("list", "Affiche les rôles staff de confiance")
                                ),
                        new SubcommandGroupData("allowlist", "Allowlist anti-phishing")
                                .addSubcommands(
                                        new SubcommandData("add", "Ajoute un domaine autorisé")
                                                .addOptions(new OptionData(OptionType.STRING, "domaine", "ex: docs.discord.com", true)),
                                        new SubcommandData("remove", "Retire un domaine autorisé")
                                                .addOptions(new OptionData(OptionType.STRING, "domaine", "Domaine", true)),
                                        new SubcommandData("list", "Affiche les domaines autorisés")
                                )
                )
                .addSubcommands(
                        new SubcommandData("module", "Active/désactive un module de protection")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "type", "Module", true)
                                                .addChoice("Anti-Bot (Joins)", "antibot")
                                                .addChoice("Anti-Raid (Vandalisme)", "antiraid")
                                                .addChoice("Anti-Phishing (Liens)", "antiphishing"),
                                        new OptionData(OptionType.BOOLEAN, "etat", "Etat", true)
                                ),
                        new SubcommandData("accountage", "Définit l'âge minimum du compte (heures)")
                                .addOptions(new OptionData(OptionType.INTEGER, "heures", "Âge min en heures", true).setMinValue(0)),
                        new SubcommandData("raidconfig", "Réglages anti-raid")
                                .addOptions(
                                        new OptionData(OptionType.INTEGER, "seuil", "Nombre de joins pour détecter une vague", true).setMinValue(3),
                                        new OptionData(OptionType.INTEGER, "fenetre", "Fenêtre en secondes", true).setMinValue(10),
                                        new OptionData(OptionType.INTEGER, "duree", "Durée du raid mode en secondes", true).setMinValue(30)
                                ),
                        new SubcommandData("raidstatus", "Affiche l'état du mode raid"),
                        new SubcommandData("dashboard", "Dashboard récapitulatif Protect"),
                        new SubcommandData("quarantine", "Définit ou retire le rôle de quarantaine")
                                .addOptions(new OptionData(OptionType.ROLE, "role", "Rôle de quarantaine (optionnel)", false)),
                        new SubcommandData("check", "Vérifie le niveau de risque d'un utilisateur")
                                .addOptions(new OptionData(OptionType.USER, "utilisateur", "Utilisateur à vérifier", true)),
                        new SubcommandData("status", "Affiche la configuration Protect active")
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("❌ Commande utilisable uniquement sur un serveur.").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String group = event.getSubcommandGroup();
        String subcommand = event.getSubcommandName();

        if (group != null && subcommand != null) {
            switch (group) {
                case "whitelist" -> {
                    handleWhitelist(event, guildId, subcommand);
                    return;
                }
                case "staffrole" -> {
                    handleStaffRole(event, guildId, subcommand);
                    return;
                }
                case "allowlist" -> {
                    handleAllowlist(event, guildId, subcommand);
                    return;
                }
                default -> {
                    event.reply("❌ Groupe inconnu.").setEphemeral(true).queue();
                    return;
                }
            }
        }

        if (subcommand == null) {
            event.reply("❌ Sous-commande manquante.").setEphemeral(true).queue();
            return;
        }

        switch (subcommand) {
            case "module" -> {
                String type = event.getOption("type", "", OptionMapping::getAsString);
                boolean etat = event.getOption("etat", false, OptionMapping::getAsBoolean);

                switch (type) {
                    case "antibot" -> protectSettingsManager.setAntiBotEnabled(guildId, etat);
                    case "antiraid" -> protectSettingsManager.setAntiRaidEnabled(guildId, etat);
                    case "antiphishing" -> protectSettingsManager.setAntiPhishingEnabled(guildId, etat);
                    default -> {
                        event.reply("❌ Type de module inconnu.").setEphemeral(true).queue();
                        return;
                    }
                }

                event.reply("✅ Module **" + type + "** désormais **" + (etat ? "activé" : "désactivé") + "**.")
                        .setEphemeral(true)
                        .queue();
            }
            case "accountage" -> {
                int heures = event.getOption("heures", 24, OptionMapping::getAsInt);
                protectSettingsManager.setMinAccountAgeHours(guildId, heures);
                event.reply("✅ Âge minimum réglé à **" + heures + "h**.").setEphemeral(true).queue();
            }
            case "raidconfig" -> {
                int threshold = event.getOption("seuil", 10, OptionMapping::getAsInt);
                int windowSec = event.getOption("fenetre", 60, OptionMapping::getAsInt);
                int durationSec = event.getOption("duree", 300, OptionMapping::getAsInt);
                protectSettingsManager.setRaidJoinThreshold(guildId, threshold);
                protectSettingsManager.setRaidWindowSeconds(guildId, windowSec);
                protectSettingsManager.setRaidModeDurationSeconds(guildId, durationSec);
                event.reply("✅ Anti-raid configuré : seuil=" + threshold + ", fenêtre=" + windowSec + "s, raid mode=" + durationSec + "s.")
                        .setEphemeral(true)
                        .queue();
            }
            case "raidstatus" -> {
                boolean active = protectSettingsManager.isRaidModeActive(guildId);
                long until = protectSettingsManager.getRaidModeUntil(guildId);
                StringBuilder sb = new StringBuilder("**État du Mode Raid**\n");
                sb.append("- Statut : ").append(active ? "🔴 ACTIF" : "🟢 INACTIF").append("\n");
                if (active) {
                    long remaining = (until - System.currentTimeMillis()) / 1000;
                    sb.append("- Fin dans : ").append(remaining > 0 ? remaining + "s" : "En cours d'extinction...");
                }
                event.reply(sb.toString()).setEphemeral(true).queue();
            }
            case "dashboard" -> {
                // Placeholder pour un dashboard plus complexe
                StringBuilder sb = new StringBuilder("**Protect Dashboard (24h)**\n")
                        .append("- État actuel : ").append(protectSettingsManager.isRaidModeActive(guildId) ? "⚠️ RAID MODE" : "✅ NORMAL").append("\n")
                        .append("- Actions anti-vandalisme : _(non implémenté)_ \n")
                        .append("- Liens phishing bloqués : _(non implémenté)_ \n")
                        .append("- Joins suspects isolés : _(non implémenté)_");
                event.reply(sb.toString()).setEphemeral(true).queue();
            }
            case "quarantine" -> {
                Role role = event.getOption("role", OptionMapping::getAsRole);
                if (role == null) {
                    protectSettingsManager.setQuarantineRoleId(guildId, null);
                    event.reply("✅ Rôle de quarantaine retiré.").setEphemeral(true).queue();
                } else {
                    protectSettingsManager.setQuarantineRoleId(guildId, role.getId());
                    event.reply("✅ Rôle de quarantaine défini : " + role.getAsMention()).setEphemeral(true).queue();
                }
            }
            case "check" -> {
                User user = event.getOption("utilisateur", OptionMapping::getAsUser);
                if (user == null) {
                    event.reply("❌ Utilisateur introuvable.").setEphemeral(true).queue();
                    return;
                }
                handleCheck(event, guildId, user);
            }
            case "status" -> {
                StringBuilder sb = new StringBuilder("**Configuration Sakura Protect**\n")
                        .append("- Anti-Bot: ").append(onOff(protectSettingsManager.isAntiBotEnabled(guildId))).append('\n')
                        .append("- Anti-Raid: ").append(onOff(protectSettingsManager.isAntiRaidEnabled(guildId))).append('\n')
                        .append("- Anti-Phishing: ").append(onOff(protectSettingsManager.isAntiPhishingEnabled(guildId))).append('\n')
                        .append("- Âge mini compte: ").append(protectSettingsManager.getMinAccountAgeHours(guildId)).append("h\n")
                        .append("- Seuil raid joins: ").append(protectSettingsManager.getRaidJoinThreshold(guildId)).append('\n')
                        .append("- Fenêtre raid: ").append(protectSettingsManager.getRaidWindowSeconds(guildId)).append("s\n")
                        .append("- Durée raid mode: ").append(protectSettingsManager.getRaidModeDurationSeconds(guildId)).append("s\n")
                        .append("- Rôle quarantaine: ").append(formatRole(protectSettingsManager.getQuarantineRoleId(guildId))).append('\n')
                        .append("- Whitelist users: ").append(protectSettingsManager.getWhitelist(guildId).size()).append('\n')
                        .append("- Staff roles trusted: ").append(protectSettingsManager.getTrustedRoleIds(guildId).size()).append('\n')
                        .append("- Allowlist phishing: ").append(protectSettingsManager.getPhishingAllowlist(guildId).size());
                event.reply(sb.toString()).setEphemeral(true).queue();
            }
            default -> event.reply("❌ Sous-commande inconnue.").setEphemeral(true).queue();
        }
    }

    private void handleCheck(SlashCommandInteractionEvent event, String guildId, User user) {
        long hoursOld = Duration.between(user.getTimeCreated(), OffsetDateTime.now()).toHours();
        int minAccountAge = protectSettingsManager.getMinAccountAgeHours(guildId);
        int raidThreshold = protectSettingsManager.getRaidJoinThreshold(guildId);
        boolean raidModeActive = protectSettingsManager.isRaidModeActive(guildId);
        boolean noAvatar = user.getAvatarId() == null;
        String username = user.getName();

        // Calcul du score (burst count = 0 car vérification manuelle a posteriori)
        int score = JoinRiskScorer.computeScore(
                hoursOld, minAccountAge, 0, raidThreshold, raidModeActive, noAvatar, username
        );

        String level;
        Color color;
        String emoji;

        if (score >= 85) {
            level = "Niveau 4 - Critique";
            color = Color.RED;
            emoji = "🔴";
        } else if (score >= 60) {
            level = "Niveau 3 - Élevé";
            color = Color.ORANGE;
            emoji = "🟠";
        } else if (score >= 40) {
            level = "Niveau 2 - Modéré";
            color = Color.YELLOW;
            emoji = "🟡";
        } else {
            level = "Niveau 1 - Faible";
            color = Color.GREEN;
            emoji = "🟢";
        }

        boolean isTrusted = isTrustedUser(event.getGuild(), user.getId());

        EmbedBuilder eb = EmbedStyle.newEmbed(color, emoji, "Analyse de Risque Protect");
        eb.setDescription("Analyse du membre " + user.getAsMention() + " (`" + user.getId() + "`)");

        eb.addField("Points de suspicion", "**" + score + "** / 100", true);
        eb.addField("Niveau de danger", "**" + level + "**", true);
        eb.addField("Statut confiance", isTrusted ? "✅ Approuvé (Ignoré)" : "⚠️ Sous surveillance", true);

        StringBuilder details = new StringBuilder();
        details.append(EmbedStyle.detailLine("Âge du compte", hoursOld + "h (Requis: " + minAccountAge + "h)")).append("\n");
        details.append(EmbedStyle.detailLine("Pas d'avatar", noAvatar ? "Oui (+15)" : "Non")).append("\n");
        details.append(EmbedStyle.detailLine("Pseudo suspect", username.matches(".*\\d{4,}+$") ? "Oui (+10)" : "Non")).append("\n");
        if (raidModeActive) {
            details.append(EmbedStyle.detailLine("Mode Raid Actif", "Oui (+20)")).append("\n");
        }

        eb.addField("Détails du calcul", details.toString(), false);

        EmbedStyle.setInfoFooterWithId(eb, user.getId());

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    private boolean isTrustedUser(Guild guild, String userId) {
        if (userId.equals(guild.getOwnerId())) return true;

        Member member = guild.getMemberById(userId);
        if (member != null) {
            if (member.getUser().isBot()) return true;
            if (member.hasPermission(Permission.ADMINISTRATOR) || member.hasPermission(Permission.MANAGE_SERVER)) return true;

            Set<String> trustedRoleIds = Set.copyOf(protectSettingsManager.getTrustedRoleIds(guild.getId()));
            for (Role role : member.getRoles()) {
                if (trustedRoleIds.contains(role.getId())) {
                    return true;
                }
            }
        }

        return protectSettingsManager.getWhitelist(guild.getId()).contains(userId);
    }

    private void handleWhitelist(SlashCommandInteractionEvent event, String guildId, String subcommand) {
        switch (subcommand) {
            case "add" -> {
                User user = event.getOption("utilisateur", OptionMapping::getAsUser);
                if (user == null) {
                    event.reply("❌ Utilisateur introuvable.").setEphemeral(true).queue();
                    return;
                }
                protectSettingsManager.addToWhitelist(guildId, user.getId());
                event.reply("✅ " + user.getAsMention() + " ajouté à la whitelist Protect.").setEphemeral(true).queue();
            }
            case "remove" -> {
                User user = event.getOption("utilisateur", OptionMapping::getAsUser);
                if (user == null) {
                    event.reply("❌ Utilisateur introuvable.").setEphemeral(true).queue();
                    return;
                }
                protectSettingsManager.removeFromWhitelist(guildId, user.getId());
                event.reply("✅ " + user.getAsMention() + " retiré de la whitelist Protect.").setEphemeral(true).queue();
            }
            case "list" -> {
                var list = protectSettingsManager.getWhitelist(guildId);
                if (list.isEmpty()) {
                    event.reply("Whitelist vide.").setEphemeral(true).queue();
                } else {
                    StringBuilder sb = new StringBuilder("**Whitelist Protect**\n");
                    for (String id : list) {
                        sb.append("- <@").append(id).append("> (`").append(id).append("`)\n");
                    }
                    event.reply(sb.toString()).setEphemeral(true).queue();
                }
            }
            default -> event.reply("❌ Sous-commande whitelist inconnue.").setEphemeral(true).queue();
        }
    }

    private void handleStaffRole(SlashCommandInteractionEvent event, String guildId, String subcommand) {
        switch (subcommand) {
            case "add" -> {
                Role role = event.getOption("role", OptionMapping::getAsRole);
                if (role == null) {
                    event.reply("❌ Rôle introuvable.").setEphemeral(true).queue();
                    return;
                }
                protectSettingsManager.addTrustedRoleId(guildId, role.getId());
                event.reply("✅ Rôle staff ajouté: " + role.getAsMention()).setEphemeral(true).queue();
            }
            case "remove" -> {
                Role role = event.getOption("role", OptionMapping::getAsRole);
                if (role == null) {
                    event.reply("❌ Rôle introuvable.").setEphemeral(true).queue();
                    return;
                }
                protectSettingsManager.removeTrustedRoleId(guildId, role.getId());
                event.reply("✅ Rôle staff retiré: " + role.getAsMention()).setEphemeral(true).queue();
            }
            case "list" -> {
                var list = protectSettingsManager.getTrustedRoleIds(guildId);
                if (list.isEmpty()) {
                    event.reply("Aucun rôle staff configuré.").setEphemeral(true).queue();
                } else {
                    StringBuilder sb = new StringBuilder("**Rôles staff de confiance**\n");
                    for (String id : list) {
                        sb.append("- <@&").append(id).append("> (`").append(id).append("`)\n");
                    }
                    event.reply(sb.toString()).setEphemeral(true).queue();
                }
            }
            default -> event.reply("❌ Sous-commande staffrole inconnue.").setEphemeral(true).queue();
        }
    }

    private void handleAllowlist(SlashCommandInteractionEvent event, String guildId, String subcommand) {
        switch (subcommand) {
            case "add" -> {
                String domain = event.getOption("domaine", "", OptionMapping::getAsString);
                if (domain.isBlank()) {
                    event.reply("❌ Domaine invalide.").setEphemeral(true).queue();
                    return;
                }
                protectSettingsManager.addPhishingAllowDomain(guildId, domain);
                event.reply("✅ Domaine autorisé ajouté: `" + domain + "`").setEphemeral(true).queue();
            }
            case "remove" -> {
                String domain = event.getOption("domaine", "", OptionMapping::getAsString);
                if (domain.isBlank()) {
                    event.reply("❌ Domaine invalide.").setEphemeral(true).queue();
                    return;
                }
                protectSettingsManager.removePhishingAllowDomain(guildId, domain);
                event.reply("✅ Domaine autorisé retiré: `" + domain + "`").setEphemeral(true).queue();
            }
            case "list" -> {
                var list = protectSettingsManager.getPhishingAllowlist(guildId);
                if (list.isEmpty()) {
                    event.reply("Allowlist phishing vide.").setEphemeral(true).queue();
                } else {
                    StringBuilder sb = new StringBuilder("**Allowlist anti-phishing**\n");
                    for (String domain : list) {
                        sb.append("- `").append(domain).append("`\n");
                    }
                    event.reply(sb.toString()).setEphemeral(true).queue();
                }
            }
            default -> event.reply("❌ Sous-commande allowlist inconnue.").setEphemeral(true).queue();
        }
    }

    private String onOff(boolean value) {
        return value ? "✅ ON" : "❌ OFF";
    }

    private String formatRole(String roleId) {
        if (roleId == null || roleId.isBlank()) return "Aucun";
        return "<@&" + roleId + "> (`" + roleId + "`)";
    }
}
