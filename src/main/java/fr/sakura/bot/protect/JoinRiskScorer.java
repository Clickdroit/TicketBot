package fr.sakura.bot.protect;

public final class JoinRiskScorer {

    private JoinRiskScorer() {
    }

    public static int computeScore(long hoursOld, int minAccountAgeHours, int burstCount, int raidThreshold, boolean raidModeActive) {
        int score = 0;

        if (hoursOld < minAccountAgeHours) {
            long deficit = minAccountAgeHours - hoursOld;
            score += (int) Math.min(70, 30 + (deficit * 2));
        }

        if (burstCount >= raidThreshold) {
            score += 40;
        } else if (burstCount >= Math.max(2, (int) Math.ceil(raidThreshold * 0.7))) {
            score += 20;
        }

        if (raidModeActive) {
            score += 20;
        }

        return Math.min(score, 100);
    }
}
