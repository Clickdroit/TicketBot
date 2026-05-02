package fr.sakura.bot.protect;

public final class JoinRiskScorer {

    private static final double MODERATE_BURST_MULTIPLIER = 0.7d;
    private static final java.util.regex.Pattern NUMERIC_SUFFIX_PATTERN = java.util.regex.Pattern.compile(".*\\d{4,}+$");

    private JoinRiskScorer() {
    }

    public static int computeScore(long hoursOld, int minAccountAgeHours, int burstCount, int raidThreshold, boolean raidModeActive, boolean noAvatar, String username) {
        int score = 0;

        if (hoursOld < minAccountAgeHours) {
            long deficit = minAccountAgeHours - hoursOld;
            score += (int) Math.min(70, 30 + (deficit * 2));
        }

        if (burstCount >= raidThreshold) {
            score += 40;
        } else if (burstCount >= Math.max(2, (int) Math.ceil(raidThreshold * MODERATE_BURST_MULTIPLIER))) {
            score += 20;
        }

        if (raidModeActive) {
            score += 20;
        }

        if (noAvatar) {
            score += 15;
        }

        if (username != null && NUMERIC_SUFFIX_PATTERN.matcher(username).matches()) {
            score += 10;
        }

        return Math.min(score, 100);
    }
}
