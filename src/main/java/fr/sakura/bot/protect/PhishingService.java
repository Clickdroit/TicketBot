package fr.sakura.bot.protect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.IDN;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PhishingService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(PhishingService.class);

    private static final String PHISHING_LIST_URL = "https://raw.githubusercontent.com/BuildTools/discord-antiscam/main/list.txt";
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\b((?:https?://|www\\.)[^\\s<>()]+)");
    private static final int PHISHING_LIST_CONNECT_TIMEOUT_MS = 4000;
    private static final int PHISHING_LIST_READ_TIMEOUT_MS = 4000;
    private static final int REDIRECT_CONNECT_TIMEOUT_MS = 1500;
    private static final int REDIRECT_READ_TIMEOUT_MS = 1500;
    private static final int MAX_REDIRECT_DEPTH = 3;

    private static final Set<String> SHORTENER_DOMAINS = Set.of(
            "bit.ly", "tinyurl.com", "t.co", "rb.gy", "cutt.ly", "is.gd", "tiny.one", "goo.gl", "ow.ly"
    );

    private static final Set<String> FALLBACK_PHISHING_DOMAINS = Set.of(
            "dlscord.gg", "disc0rd.com", "discord-app.com", "discord-nitro.com", "free-nitro.com",
            "steam-community.com", "steam-nitro.com", "discord.gift", "discord.co", "discorcl.com"
    );

    private static final List<String> PROTECTED_DOMAINS = List.of("discord.com", "discord.gg", "steamcommunity.com", "steampowered.com");

    private volatile Set<String> phishingDomains = FALLBACK_PHISHING_DOMAINS;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public record DetectionResult(boolean phishingFound, String matchedUrl, String matchedDomain, String reason) {
        public static DetectionResult none() {
            return new DetectionResult(false, null, null, null);
        }
    }

    public PhishingService() {
        this(true);
    }

    PhishingService(boolean startScheduler) {
        if (startScheduler) {
            scheduler.scheduleAtFixedRate(this::updateList, 0, 12, TimeUnit.HOURS);
        }
    }

    private void updateList() {
        logger.info("Mise à jour de la liste Anti-Phishing...");
        try {
            URL url = new URL(PHISHING_LIST_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(PHISHING_LIST_CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(PHISHING_LIST_READ_TIMEOUT_MS);

            Set<String> newList = ConcurrentHashMap.newKeySet();
            newList.addAll(FALLBACK_PHISHING_DOMAINS);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String normalized = normalizeDomain(line);
                    if (!normalized.isEmpty()) {
                        newList.add(normalized);
                    }
                }
            }

            phishingDomains = Set.copyOf(newList);
            logger.info("Liste Anti-Phishing mise à jour : {} domaines chargés.", phishingDomains.size());
        } catch (Exception e) {
            logger.error("Erreur lors de la mise à jour de la liste Anti-Phishing (utilisation du fallback)", e);
            if (phishingDomains.isEmpty()) {
                phishingDomains = FALLBACK_PHISHING_DOMAINS;
            }
        }
    }

    public CompletableFuture<DetectionResult> detectAsync(String content, Collection<String> allowlist) {
        if (content == null || content.isBlank()) {
            return CompletableFuture.completedFuture(DetectionResult.none());
        }

        Set<String> allowlistSet = normalizeDomains(allowlist);
        List<String> urls = extractUrls(content);

        List<CompletableFuture<DetectionResult>> futures = new ArrayList<>();
        for (String rawUrl : urls) {
            futures.add(checkUrlAsync(rawUrl, allowlistSet));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    for (CompletableFuture<DetectionResult> future : futures) {
                        DetectionResult result = future.join();
                        if (result.phishingFound()) return result;
                    }
                    return DetectionResult.none();
                });
    }

    private CompletableFuture<DetectionResult> checkUrlAsync(String rawUrl, Set<String> allowlistSet) {
        String normalizedUrl = ensureUrlScheme(rawUrl);
        String host = extractNormalizedHost(normalizedUrl);
        if (host == null || host.isBlank()) return CompletableFuture.completedFuture(DetectionResult.none());

        if (isDomainAllowed(host, allowlistSet)) {
            return CompletableFuture.completedFuture(DetectionResult.none());
        }

        if (isBlockedDomain(host, allowlistSet)) {
            return CompletableFuture.completedFuture(new DetectionResult(true, rawUrl, host, "domain_match"));
        }

        if (checkTyposquatting(host)) {
            return CompletableFuture.completedFuture(new DetectionResult(true, rawUrl, host, "typosquatting"));
        }

        if (isShortenerDomain(host)) {
            return CompletableFuture.supplyAsync(() -> {
                String redirectedHost = resolveRedirectedHost(normalizedUrl);
                if (redirectedHost != null && !isDomainAllowed(redirectedHost, allowlistSet)) {
                    if (isBlockedDomain(redirectedHost, allowlistSet)) {
                        return new DetectionResult(true, rawUrl, redirectedHost, "shortener_redirect");
                    }
                    if (checkTyposquatting(redirectedHost)) {
                        return new DetectionResult(true, rawUrl, redirectedHost, "shortener_typosquatting");
                    }
                }
                return DetectionResult.none();
            }, scheduler);
        }

        return CompletableFuture.completedFuture(DetectionResult.none());
    }

    private boolean checkTyposquatting(String host) {
        for (String protectedDomain : PROTECTED_DOMAINS) {
            if (host.equals(protectedDomain)) return false;
            int distance = calculateLevenshteinDistance(host, protectedDomain);
            if (distance > 0 && distance <= 2) {
                return true;
            }
        }
        return false;
    }

    private int calculateLevenshteinDistance(String x, String y) {
        int[][] dp = new int[x.length() + 1][y.length() + 1];
        for (int i = 0; i <= x.length(); i++) {
            for (int j = 0; j <= y.length(); j++) {
                if (i == 0) dp[i][j] = j;
                else if (j == 0) dp[i][j] = i;
                else {
                    dp[i][j] = Math.min(Math.min(
                        dp[i - 1][j - 1] + (x.charAt(i - 1) == y.charAt(j - 1) ? 0 : 1),
                        dp[i - 1][j] + 1),
                        dp[i][j - 1] + 1);
                }
            }
        }
        return dp[x.length()][y.length()];
    }

    public DetectionResult detect(String content, Collection<String> allowlist) {
        return detectAsync(content, allowlist).join();
    }

    public String normalizeDomain(String domain) {
        if (domain == null) return "";
        String normalized = domain.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return "";
        if (normalized.startsWith("http://")) normalized = normalized.substring(7);
        if (normalized.startsWith("https://")) normalized = normalized.substring(8);
        int slashIdx = normalized.indexOf('/');
        if (slashIdx >= 0) normalized = normalized.substring(0, slashIdx);
        if (normalized.startsWith("www.")) normalized = normalized.substring(4);
        if (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);

        try {
            normalized = IDN.toASCII(normalized);
        } catch (IllegalArgumentException e) {
            logger.debug("Domaine invalide ignoré pendant normalisation IDN: {}", domain, e);
            return "";
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private Set<String> normalizeDomains(Collection<String> domains) {
        if (domains == null || domains.isEmpty()) return Set.of();
        Set<String> normalized = new HashSet<>();
        for (String domain : domains) {
            String v = normalizeDomain(domain);
            if (!v.isBlank()) normalized.add(v);
        }
        return normalized;
    }

    private List<String> extractUrls(String content) {
        Matcher matcher = URL_PATTERN.matcher(content);
        List<String> urls = new ArrayList<>();
        while (matcher.find()) {
            urls.add(trimTrailingPunctuation(matcher.group(1)));
        }
        return urls;
    }

    private String trimTrailingPunctuation(String url) {
        String value = url;
        while (!value.isEmpty() && ".,!?;:)".indexOf(value.charAt(value.length() - 1)) >= 0) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String ensureUrlScheme(String rawUrl) {
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            return rawUrl;
        }
        return "https://" + rawUrl;
    }

    private String extractNormalizedHost(String url) {
        try {
            URI uri = URI.create(url);
            return normalizeDomain(uri.getHost());
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isBlockedDomain(String host, Set<String> allowlistSet) {
        if (host == null || host.isBlank()) return false;
        Set<String> localSnapshot = phishingDomains;
        for (String blocked : localSnapshot) {
            if (domainMatches(host, blocked) && !isDomainAllowed(host, allowlistSet)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDomainAllowed(String host, Set<String> allowlistSet) {
        if (allowlistSet.isEmpty()) return false;
        for (String allowed : allowlistSet) {
            if (domainMatches(host, allowed)) return true;
        }
        return false;
    }

    private boolean isShortenerDomain(String host) {
        for (String shortener : SHORTENER_DOMAINS) {
            if (domainMatches(host, shortener)) return true;
        }
        return false;
    }

    private boolean domainMatches(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private String resolveRedirectedHost(String url) {
        try {
            String current = url;
            for (int i = 0; i < MAX_REDIRECT_DEPTH; i++) {
                HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
                conn.setConnectTimeout(REDIRECT_CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(REDIRECT_READ_TIMEOUT_MS);
                conn.setRequestMethod("HEAD");
                conn.setInstanceFollowRedirects(false);
                int status = conn.getResponseCode();
                if (status / 100 != 3) {
                    return extractNormalizedHost(current);
                }
                String location = conn.getHeaderField("Location");
                if (location == null || location.isBlank()) {
                    return extractNormalizedHost(current);
                }
                URI next = URI.create(current).resolve(location);
                if (!"http".equalsIgnoreCase(next.getScheme()) && !"https".equalsIgnoreCase(next.getScheme())) {
                    return null;
                }
                current = next.toString();
            }
            return extractNormalizedHost(current);
        } catch (Exception e) {
            return null;
        }
    }

    void setPhishingDomainsForTesting(Set<String> domains) {
        phishingDomains = normalizeDomains(domains);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
