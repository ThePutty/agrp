package cz.demo.caselaw.justice;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.demo.caselaw.domain.Decision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnapshotDecisionSourceTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static Decision decision(UUID id, LocalDate publishedOn) {
        return new Decision(id, "ECLI", "1 C 1/2023", "Okresní soud ve Znojmě", "OSZN",
                LocalDate.of(2023, 11, 30), publishedOn, "předmět",
                List.of("smlouva o úvěru"), List.of("§ 2991 z. č. 89/2012 Sb."), List.of("VYHOVENI"),
                "I. Žaloba se zamítá.", "1. Odůvodnění.", "https://x");
    }

    private static Path writeSnapshot(Path dir, Decision... decisions) throws IOException {
        ObjectMapper mapper = SnapshotDecisionSource.mapper();
        Path file = dir.resolve("decisions.jsonl.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            for (Decision d : decisions) {
                out.write(mapper.writeValueAsString(d).getBytes(StandardCharsets.UTF_8));
                out.write('\n');
            }
            out.write('\n');   // trailing blank line must be tolerated
        }
        return file;
    }

    private static SnapshotDecisionSource sourceFor(Path file) {
        SnapshotDecisionSource source = new SnapshotDecisionSource(
                new IngestProperties("snapshot", file.toString(), 300, 8));
        source.load();
        return source;
    }

    @Test
    void readsGzipJsonlAndFiltersByPublicationDay(@TempDir Path dir) throws IOException {
        SnapshotDecisionSource source = sourceFor(writeSnapshot(dir,
                decision(A, LocalDate.of(2024, 3, 1)), decision(B, LocalDate.of(2024, 3, 4))));

        assertThat(source.listDecisionIds(LocalDate.of(2024, 3, 1))).containsExactly(A);
        assertThat(source.listDecisionIds(LocalDate.of(2024, 3, 4))).containsExactly(B);
        assertThat(source.listDecisionIds(LocalDate.of(2024, 3, 9))).isEmpty();
        assertThat(source.all()).hasSize(2);

        Decision loaded = source.fetch(A);
        assertThat(loaded).isEqualTo(decision(A, LocalDate.of(2024, 3, 1)));
    }

    @Test
    void unknownIdIsReportedAsNotFound(@TempDir Path dir) throws IOException {
        SnapshotDecisionSource source = sourceFor(writeSnapshot(dir, decision(A, LocalDate.of(2024, 3, 1))));

        assertThatThrownBy(() -> source.fetch(B)).isInstanceOf(DecisionNotFoundException.class);
    }

    @Test
    void missingFileLeavesAnEmptySource(@TempDir Path dir) {
        SnapshotDecisionSource source = sourceFor(dir.resolve("nope.jsonl.gz"));

        assertThat(source.all()).isEmpty();
        assertThat(source.listDecisionIds(LocalDate.of(2024, 3, 1))).isEmpty();
    }
}
