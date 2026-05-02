package fr.sakura.bot.protect;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhishingServiceTest {

    @Test
    void shouldDetectBlockedDomainFromUrlHost() {
        PhishingService service = new PhishingService(false);
        service.setPhishingDomainsForTesting(Set.of("bad-site.com"));

        PhishingService.DetectionResult result = service.detect("visit https://login.bad-site.com/secure", Set.of());

        assertTrue(result.phishingFound());
        service.close();
    }

    @Test
    void shouldNotFlagPlainTextWithoutUrl() {
        PhishingService service = new PhishingService(false);
        service.setPhishingDomainsForTesting(Set.of("bad-site.com"));

        PhishingService.DetectionResult result = service.detect("this string mentions bad-site.com but no url", Set.of());

        assertFalse(result.phishingFound());
        service.close();
    }

    @Test
    void shouldHonorAllowlistDomain() {
        PhishingService service = new PhishingService(false);
        service.setPhishingDomainsForTesting(Set.of("example.com"));

        PhishingService.DetectionResult result = service.detect("https://safe.example.com/home", Set.of("example.com"));

        assertFalse(result.phishingFound());
        service.close();
    }

    @Test
    void shouldDetectTyposquatting() {
        PhishingService service = new PhishingService(false);
        
        // "d1scord.gg" is 1 distance from "discord.gg" and not in fallback
        PhishingService.DetectionResult result = service.detect("https://d1scord.gg/free-nitro", Set.of());
        
        assertTrue(result.phishingFound());
        assertTrue("typosquatting".equals(result.reason()));
        service.close();
    }
}
