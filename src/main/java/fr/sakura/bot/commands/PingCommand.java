package fr.sakura.bot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PingCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(PingCommand.class);

    @Override
    public String getName() {
        return "ping";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Répond avec Pong ! et affiche la latence du bot");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        long time = System.currentTimeMillis();
        logger.debug("Execution /ping demandee par userId={}", event.getUser().getId());
        event.reply("Pong ! \uD83C\uDFD3").queue(response -> {
            long latency = System.currentTimeMillis() - time;
            response.editOriginalFormat("Pong ! \uD83C\uDFD3 Latence : %d ms", latency).queue();
            logger.info("/ping repondu userId={} latencyMs={}", event.getUser().getId(), latency);
        });
    }
}
