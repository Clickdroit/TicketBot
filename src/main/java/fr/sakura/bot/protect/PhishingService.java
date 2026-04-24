package fr.sakura.bot.protect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PhishingService {

    private static final Logger logger = LoggerFactory.getLogger(PhishingService.class);
    private final Set<String> phishingDomains = new HashSet<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // URL vers une liste de domaines de phishing Discord (exemple public)
    private static final String PHISHING_LIST_URL = "https://raw.githubusercontent.com/nikolaisun/discord-phishing-links/main/domain-list.txt";

    public PhishingService() {
        // Mettre à jour la liste toutes les 12 heures
        scheduler.scheduleAtFixedRate(this::updateList, 0, 12, TimeUnit.HOURS);
    }

    private void updateList() {
        logger.info("Mise à jour de la liste Anti-Phishing...");
        try {
            URL url = new URL(PHISHING_LIST_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                Set<String> newList = new HashSet<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        newList.add(line.toLowerCase());
                    }
                }
                synchronized (phishingDomains) {
                    phishingDomains.clear();
                    phishingDomains.addAll(newList);
                }
                logger.info("Liste Anti-Phishing mise à jour : {} domaines chargés.", phishingDomains.size());
            }
        } catch (Exception e) {
            logger.error("Erreur lors de la mise à jour de la liste Anti-Phishing", e);
        }
    }

    public boolean isPhishing(String content) {
        String lowerContent = content.toLowerCase();
        synchronized (phishingDomains) {
            for (String domain : phishingDomains) {
                if (lowerContent.contains(domain)) {
                    return true;
                }
            }
        }
        return false;
    }
}
