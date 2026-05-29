package fr.sakura.bot.commands;

import fr.sakura.bot.core.service.TicketService;
import fr.sakura.bot.database.SettingsManager;
import fr.sakura.bot.listeners.log.TicketLogListener;

/**
 * Regroupe les dépendances globales injectées dans les commandes de TicketBot.
 */
public record BotContext(
    SettingsManager settings,
    TicketService ticketService,
    TicketLogListener ticketLogListener
) {}
