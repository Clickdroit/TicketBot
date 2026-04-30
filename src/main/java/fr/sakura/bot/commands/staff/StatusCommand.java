package fr.sakura.bot.commands.staff;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.database.DatabaseManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.awt.Color;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class StatusCommand implements ICommand {

    @Override
    public String getName() {
        return "status";
    }

    @Override
    public String getCategory() {
        return "Staff";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche l'état de santé technique du bot")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        String uptimeStr = formatUptime(uptime);

        long gatewayPing = event.getJDA().getGatewayPing();
        
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("📡 État du Système Sakura")
                .setColor(gatewayPing < 150 ? Color.GREEN : Color.YELLOW)
                .setTimestamp(Instant.now());

        eb.addField("⏱️ Uptime", "```" + uptimeStr + "```", true);
        eb.addField("📶 Latence Gateway", "```" + gatewayPing + "ms```", true);
        eb.addField("💾 Base de données", "```" + (DatabaseManager.isReady() ? "Connecté (" + (DatabaseManager.isPostgres() ? "PostgreSQL" : "SQLite") + ")" : "Déconnecté ❌") + "```", false);

        Runtime runtime = Runtime.getRuntime();
        long usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long totalMem = runtime.totalMemory() / 1024 / 1024;
        eb.addField("🧠 Mémoire", "```" + usedMem + "MB / " + totalMem + "MB```", true);
        eb.addField("🧵 Threads", "```" + Thread.activeCount() + "```", true);

        eb.setFooter("Sakura Bot v1.0 • ID: " + event.getJDA().getSelfUser().getId());

        event.replyEmbeds(eb.build()).queue();
    }

    private String formatUptime(long ms) {
        long days = TimeUnit.MILLISECONDS.toDays(ms);
        long hours = TimeUnit.MILLISECONDS.toHours(ms) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;
        return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
    }
}
