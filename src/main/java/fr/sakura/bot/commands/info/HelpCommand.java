package fr.sakura.bot.commands.info;



import fr.sakura.bot.commands.ICommand;

import fr.sakura.bot.core.util.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class HelpCommand implements ICommand {

    private static final Logger logger = LoggerFactory.getLogger(HelpCommand.class);
    private final Map<String, ICommand> commandMap;

    public HelpCommand(Map<String, ICommand> commandMap) {
        this.commandMap = commandMap;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Affiche la liste des commandes disponibles");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        logger.debug("Execution /help par userId={}", event.getUser().getId());

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("ÃƒÆ’Ã‚Â°Ãƒâ€¦Ã‚Â¸Ãƒâ€¦Ã¢â‚¬â„¢Ãƒâ€šÃ‚Â¸", "Guide des commandes Sakura");
        embed.setDescription("Voici les commandes disponibles sur ce serveur, classÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©es par catÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©gorie.");
        
        // Group commands by category
        Map<String, List<ICommand>> categories = new TreeMap<>();
        for (ICommand cmd : commandMap.values()) {
            categories.computeIfAbsent(cmd.getCategory(), k -> new ArrayList<>()).add(cmd);
        }

        // Add fields for each category
        for (Map.Entry<String, List<ICommand>> entry : categories.entrySet()) {
            String categoryName = entry.getKey();
            String commandList = entry.getValue().stream()
                    .sorted(Comparator.comparing(ICommand::getName))
                    .map(cmd -> "`/`" + cmd.getName())
                    .collect(Collectors.joining(", "));
            
            embed.addField(categoryName, commandList, false);
        }

        if (event.getJDA().getSelfUser().getEffectiveAvatarUrl() != null) {
            embed.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl());
        }
        EmbedStyle.setFooter(embed, "DemandÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© par " + event.getUser().getName());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        logger.info("/help envoye userId={}", event.getUser().getId());
    }
}
