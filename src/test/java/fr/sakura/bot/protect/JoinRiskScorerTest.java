package fr.sakura.bot.protect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinRiskScorerTest {

    @Test
    void shouldGiveLowScoreForNormalJoin() {
        int score = JoinRiskScorer.computeScore(720, 24, 2, 10, false, false, "NormalUser");
        assertTrue(score < 30);
    }

    @Test
    void shouldGiveHighScoreForFreshAccountAndRaidBurst() {
        int score = JoinRiskScorer.computeScore(1, 24, 15, 10, true, false, "NormalUser");
        assertTrue(score >= 85);
    }

    @Test
    void shouldIncreaseScoreForNoAvatar() {
        int scoreNormal = JoinRiskScorer.computeScore(100, 24, 0, 10, false, false, "NormalUser");
        int scoreNoAvatar = JoinRiskScorer.computeScore(100, 24, 0, 10, false, true, "NormalUser");
        assertTrue(scoreNoAvatar > scoreNormal);
    }

    @Test
    void shouldIncreaseScoreForNumericPattern() {
        int scoreNormal = JoinRiskScorer.computeScore(100, 24, 0, 10, false, false, "NormalUser");
        int scorePattern = JoinRiskScorer.computeScore(100, 24, 0, 10, false, false, "User12345");
        assertTrue(scorePattern > scoreNormal);
    }
}
