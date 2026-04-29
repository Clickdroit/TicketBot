package fr.sakura.bot.commands.info;

import fr.sakura.bot.commands.ICommand;
import fr.sakura.bot.core.util.EmbedStyle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Random;

public class SoulmateCommand implements ICommand {

    private static final String ID_1 = "838024514369617930";
    private static final String ID_2 = "993896595554848829";

    @Override
    public String getName() {
        return "soulmate";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Calcule le taux d'âme sœur entre deux personnes")
                .addOption(OptionType.USER, "utilisateur1", "Le premier utilisateur", true)
                .addOption(OptionType.USER, "utilisateur2", "Le second utilisateur (par défaut vous)", false);
    }

    @Override
    public String getCategory() {
        return "Info";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User user1 = event.getOption("utilisateur1").getAsUser();
        User user2 = event.getOption("utilisateur2") != null 
                ? event.getOption("utilisateur2").getAsUser() 
                : event.getUser();

        int percentage = calculateSoulmate(user1.getId(), user2.getId());

        EmbedBuilder embed = EmbedStyle.newInfoEmbed("❤️", "Test d'Âme Sœur");
        embed.setDescription(String.format("Le taux d'âme sœur entre **%s** et **%s** est de :\n\n# %d%%", 
                user1.getName(), user2.getName(), percentage));

        String comment;
        if (percentage == 100) comment = "C'est le grand amour ! ✨";
        else if (percentage >= 90) comment = "Une connexion incroyable ! 🌸";
        else if (percentage >= 75) comment = "C'est très prometteur ! 😊";
        else if (percentage >= 50) comment = "Il y a un bon feeling. 👍";
        else if (percentage >= 25) comment = "C'est pas gagné, mais pourquoi pas ? 🤔";
        else comment = "Peut-être devriez-vous rester amis... 🧊";

        embed.addField("Verdict", comment, false);
        
        if (percentage >= 50) {
            embed.setThumbnail("https://cdn.discordapp.com/emojis/1126131495066042459.png"); // Un emoji coeur custom ou fixe
        }

        event.replyEmbeds(embed.build()).queue();
    }

    private int calculateSoulmate(String id1, String id2) {
        // Cas spécial
        if ((id1.equals(ID_1) && id2.equals(ID_2)) || (id1.equals(ID_2) && id2.equals(ID_1))) {
            return 98;
        }

        // Pour que ce soit statique et commutatif
        long l1 = Long.parseLong(id1);
        long l2 = Long.parseLong(id2);
        
        long min = Math.min(l1, l2);
        long max = Math.max(l1, l2);

        // On utilise les IDs pour créer un seed unique mais constant pour ce couple
        // La multiplication par des nombres premiers aide à répartir les valeurs
        long seed = min * 31 + max;
        Random random = new Random(seed);
        
        return random.nextInt(101);
    }
}
