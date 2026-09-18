package cz.demo.caselaw.temporal;

import java.util.UUID;

/**
 * Workflow ids are also the public job ids returned by GraphQL, so they must be short,
 * readable and stable: the UI polls research(id) / ingest(id) with exactly this string.
 */
public final class TemporalIds {
    public static String research() {
        return "research-" + shortRandom();
    }

    public static String ingest() {
        return "ingest-" + shortRandom();
    }

    private static String shortRandom() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private TemporalIds() {}
}
