package cz.demo.caselaw.store;

import cz.demo.caselaw.domain.Chunk;
import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.justice.IngestProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecursiveChunkerTest {

    private static final UUID ID = UUID.randomUUID();

    private static Decision decision(String verdict, String justification) {
        return new Decision(ID, null, "1 C 1/2023", "Okresní soud", null, null, null, null,
                List.of(), List.of(), List.of(), verdict, justification, "https://x");
    }

    private static String longText(int paragraphs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= paragraphs; i++) {
            sb.append(i).append(". Soud zjistil ze spisu následující skutečnosti týkající se uzavřené smlouvy o úvěru ")
                    .append("a následného postoupení pohledávky na žalobkyni, přičemž žalovaný tyto skutečnosti nerozporoval.\n\n");
        }
        return sb.toString();
    }

    @Test
    void verdictBecomesTheFirstChunkWithMarkupRemoved() {
        List<Chunk> chunks = new RecursiveChunker(props(8)).chunk(decision("<b>I. Žaloba se zamítá.</b>", "1. Odůvodnění."));

        assertThat(chunks.get(0).seq()).isZero();
        assertThat(chunks.get(0).section()).isEqualTo("verdict");
        assertThat(chunks.get(0).content()).isEqualTo("I. Žaloba se zamítá.");
        assertThat(chunks.get(0).decisionId()).isEqualTo(ID);
        assertThat(chunks.get(1).section()).isEqualTo("justification");
    }

    @Test
    void blankSectionsAreSkipped() {
        assertThat(new RecursiveChunker(props(8)).chunk(decision("  ", null))).isEmpty();
        assertThat(new RecursiveChunker(props(8)).chunk(decision(null, "1. Text odůvodnění."))).hasSize(1);
    }

    @Test
    void justificationIsSplitIntoSeveralChunksWithSequentialSeq() {
        List<Chunk> chunks = new RecursiveChunker(props(8)).chunk(decision("I. Žaloba se zamítá.", longText(30)));

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks).allSatisfy(c -> assertThat(c.content().length()).isLessThanOrEqualTo(1600));
        assertThat(chunks).extracting(Chunk::seq).containsExactlyElementsOf(
                java.util.stream.IntStream.range(0, chunks.size()).boxed().toList());
    }

    @Test
    void totalIsCappedByConfiguration() {
        List<Chunk> chunks = new RecursiveChunker(props(3)).chunk(decision("I. Žaloba se zamítá.", longText(100)));

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).section()).isEqualTo("verdict");
    }

    private static IngestProperties props(int maxChunks) {
        return new IngestProperties("snapshot", "/tmp/none.gz", 300, maxChunks);
    }
}
