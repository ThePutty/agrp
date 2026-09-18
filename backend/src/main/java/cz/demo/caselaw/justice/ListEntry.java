package cz.demo.caselaw.justice;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One row of the open-data list endpoint (`/api/opendata/{y}/{m}/{d}`).
 * The finaldoc endpoint does NOT repeat the court name, the Czech keywords or the
 * human-readable case number, so these list values are cached and merged into the Decision.
 */
public record ListEntry(
        UUID id,
        String caseNumber,
        String court,
        String ecli,
        String subject,
        LocalDate decidedOn,
        LocalDate publishedOn,
        List<String> keywords,
        List<String> provisions
) {}
