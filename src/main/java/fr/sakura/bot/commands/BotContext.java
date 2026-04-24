package fr.sakura.bot.commands;

import fr.sakura.bot.core.service.*;
import fr.sakura.bot.database.ProtectSettingsManager;
import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.core.service.LevelService;
import fr.sakura.bot.core.service.RolesPanelService;
import fr.sakura.bot.core.service.TicketService;
import fr.sakura.bot.core.service.WarningService;
import fr.sakura.bot.listeners.log.ModerationLogListener;

/**
 * Regroupe les dépendances globales injectées dans les commandes.
 */
public record BotContext(
    String guildId,
    SettingsManager settings,
    LevelService levelService,
    TicketService ticketService,
    WarningService warningService,
    RolesPanelService rolesPanelService,
    ModerationLogListener moderationLog,
    ProtectSettingsManager protectSettings
) {}
