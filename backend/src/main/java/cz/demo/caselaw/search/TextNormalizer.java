package cz.demo.caselaw.search;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Canonical text form used by every deterministic comparison (citation verification,
 * provision matching). Diacritics are dropped because the LLM, the source documents and the
 * PostgreSQL fulltext index (immutable_unaccent) all disagree about them.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null || text.isEmpty()) return "";
        String s = text.replaceAll("<[^>]{1,20}>", " ");
        s = unifyPunctuation(s);
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.toLowerCase(Locale.ROOT);
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String unifyPunctuation(String s) {
        return s
                .replaceAll("[‘’‚‛′´`]", "'")
                .replaceAll("[“”„‟″«»]", "\"")
                .replaceAll("[‐‑‒–—―−]", "-")
                .replaceAll("[   ​﻿]", " ")
                .replaceAll("…", "...");
    }
}
