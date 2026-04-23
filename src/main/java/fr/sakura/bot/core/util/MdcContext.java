package fr.sakura.bot.core.util;

import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilitaire permettant de gÃ©rer le MDC (Mapped Diagnostic Context) avec le pattern try-with-resources.
 * Assure que les clÃ©s ajoutÃ©es sont systÃ©matiquement retirÃ©es en fin de bloc.
 */
public class MdcContext implements AutoCloseable {

    private final List<String> keys = new ArrayList<>();

    private MdcContext() {}

    /**
     * Ajoute des paires clÃ©/valeur au MDC.
     * @param pairs Paires de clÃ©s et valeurs (doit Ãªtre de longueur paire).
     * @return Une instance de MdcContext Ã  utiliser dans un try-with-resources.
     */
    public static MdcContext of(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("MdcContext.of attend un nombre pair d'arguments (clÃ©s/valeurs)");
        }

        MdcContext ctx = new MdcContext();
        for (int i = 0; i < pairs.length; i += 2) {
            String key = pairs[i];
            String value = pairs[i + 1];
            if (key != null && value != null) {
                MDC.put(key, value);
                ctx.keys.add(key);
            }
        }
        return ctx;
    }

    @Override
    public void close() {
        for (String key : keys) {
            MDC.remove(key);
        }
    }
}
