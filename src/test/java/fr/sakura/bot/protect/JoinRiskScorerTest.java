package fr.sakura.bot.protect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinRiskScorerTest {

    @Test
    void shouldGiveLowScoreForNormalJoin() {
        int score = JoinRiskScorer.computeScore(720, 24, 2, 10, false);
        assertTrue(score < 30);
    }

    @Test
    void shouldGiveHighScoreForFreshAccountAndRaidBurst() {
        int score = JoinRiskScorer.computeScore(1, 24, 15, 10, true);
        assertTrue(score >= 85);
    }
}
