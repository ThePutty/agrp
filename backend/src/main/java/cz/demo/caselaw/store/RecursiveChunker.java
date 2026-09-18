package cz.demo.caselaw.store;

import cz.demo.caselaw.domain.Chunk;
import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.justice.IngestProperties;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a decision into embeddable chunks.
 *
 * <p>The verdict is always kept whole as chunk #0 - it is short, it decides the case and the
 * demo shows it verbatim. The justification is split recursively (paragraph → sentence → word)
 * into ~1500-character windows with a 200-character overlap, so a passage that straddles a
 * boundary still appears intact in one of the chunks. The total is capped, keeping the first N:
 * Czech judgments state the operative reasoning early and embeddings are not free.
 */
@Component
public class RecursiveChunker implements Chunker {

    static final String SECTION_VERDICT = "verdict";
    static final String SECTION_JUSTIFICATION = "justification";
    private static final int CHUNK_SIZE = 1500;
    private static final int CHUNK_OVERLAP = 200;

    private final int maxChunks;

    public RecursiveChunker(IngestProperties props) {
        this.maxChunks = props.maxChunksPerDecision();
    }

    @Override
    public List<Chunk> chunk(Decision decision) {
        List<Chunk> chunks = new ArrayList<>();
        int seq = 0;

        String verdict = clean(decision.verdictText());
        if (!verdict.isBlank()) {
            chunks.add(new Chunk(null, decision.id(), seq++, SECTION_VERDICT, verdict));
        }

        String justification = clean(decision.justificationText());
        if (!justification.isBlank()) {
            List<TextSegment> segments = DocumentSplitters.recursive(CHUNK_SIZE, CHUNK_OVERLAP)
                    .split(Document.from(justification));
            for (TextSegment segment : segments) {
                if (chunks.size() >= maxChunks) break;
                String text = segment.text().trim();
                if (text.isBlank()) continue;
                chunks.add(new Chunk(null, decision.id(), seq++, SECTION_JUSTIFICATION, text));
            }
        }
        return List.copyOf(chunks);
    }

    /** justice.cz texts carry occasional <b>/<i> markup and stray carriage returns. */
    private static String clean(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]{1,20}>", "")
                .replace('\r', '\n')
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
