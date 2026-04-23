package fr.sakura.bot.core.service;

import fr.sakura.bot.core.model.WarningEntry;
import fr.sakura.bot.core.store.WarningStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WarningServiceTest {

    private WarningStore warningStore;
    private WarningService warningService;

    @BeforeEach
    void setUp() {
        warningStore = Mockito.mock(WarningStore.class);
        warningService = new WarningService(warningStore);
    }

    @Test
    void addWarning_CallsStoreAndReturnsCount() {
        when(warningStore.addWarning(eq("guild-1"), eq("user-1"), any(WarningEntry.class))).thenReturn(3);

        int count = warningService.addWarning("guild-1", "user-1", "mod-1", "Spam");

        assertEquals(3, count);
        verify(warningStore).addWarning(eq("guild-1"), eq("user-1"), any(WarningEntry.class));
    }

    @Test
    void getWarnings_ReturnsStoreList() {
        List<WarningEntry> mockList = List.of(new WarningEntry("mod-1", "Reason", "2024-01-01T00:00:00Z"));
        when(warningStore.getWarnings("guild-1", "user-1")).thenReturn(mockList);

        List<WarningEntry> result = warningService.getWarnings("guild-1", "user-1");

        assertEquals(mockList, result);
    }
}
