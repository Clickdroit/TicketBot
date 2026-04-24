package fr.sakura.bot.core.service;

import fr.sakura.bot.core.store.LevelStore;
import fr.sakura.bot.database.SettingsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class LevelServiceTest {

    private LevelStore levelStore;
    private SettingsManager settingsManager;
    private LevelService levelService;

    @BeforeEach
    void setUp() {
        levelStore = Mockito.mock(LevelStore.class);
        settingsManager = Mockito.mock(SettingsManager.class);
        levelService = new LevelService(levelStore, settingsManager);
        
        // Configuration des réglages par défaut
        when(settingsManager.getXpMinMessageLength(anyString())).thenReturn(5);
        when(settingsManager.getXpMinAlnumCount(anyString())).thenReturn(3);
    }

    @Test
    void shouldAwardXp_ValidContent() {
        when(settingsManager.isLevelsEnabled(anyString())).thenReturn(true);
        assertTrue(levelService.shouldAwardXp("guild-1", "Bonjour tout le monde, ceci est un message valide."));
    }

    @Test
    void shouldNotAwardXp_WhenDisabled() {
        when(settingsManager.isLevelsEnabled(anyString())).thenReturn(false);
        assertFalse(levelService.shouldAwardXp("guild-1", "Ceci est un message valide mais le système est coupé."));
    }

    @Test
    void shouldNotAwardXp_TooShort() {
        assertFalse(levelService.shouldAwardXp("guild-1", "Slt"));
    }

    @Test
    void shouldNotAwardXp_Command() {
        assertFalse(levelService.shouldAwardXp("guild-1", "/help rank"));
    }

    @Test
    void shouldNotAwardXp_OnlyUrl() {
        assertFalse(levelService.shouldAwardXp("guild-1", "https://discord.gg/invite"));
    }

    @Test
    void shouldNotAwardXp_Repetitive() {
        assertFalse(levelService.shouldAwardXp("guild-1", "aaaaaaaaaa"));
    }

    @Test
    void computeLevelFromTotalXp_CorrectCalculations() {
        assertEquals(0, levelService.computeLevelFromTotalXp(0));
        assertEquals(0, levelService.computeLevelFromTotalXp(99));
        assertEquals(1, levelService.computeLevelFromTotalXp(100));
        assertEquals(1, levelService.computeLevelFromTotalXp(399));
        assertEquals(2, levelService.computeLevelFromTotalXp(400));
        assertEquals(10, levelService.computeLevelFromTotalXp(10000));
    }
}
