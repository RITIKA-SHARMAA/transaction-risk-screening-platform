package com.ritikasharma.risk.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Canonical form for name matching: accents removed, lower case, every run of non-alphanumeric
 * characters collapsed to a single space, trimmed. {@code watchlist_entries.normalized_name} is stored
 * in this form, so the seed migration and this class must stay in agreement.
 */
public final class NameNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private NameNormalizer() {
    }

    public static String normalize(String name) {
        Objects.requireNonNull(name, "name");
        String withoutAccents = COMBINING_MARKS.matcher(Normalizer.normalize(name, Normalizer.Form.NFD)).replaceAll("");
        return NON_ALPHANUMERIC.matcher(withoutAccents.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }
}
