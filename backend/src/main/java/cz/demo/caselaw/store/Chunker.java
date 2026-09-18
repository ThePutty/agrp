package cz.demo.caselaw.store;

import cz.demo.caselaw.domain.Chunk;
import cz.demo.caselaw.domain.Decision;

import java.util.List;

/** Splits a decision into chunks: verdict = 1 chunk, justification = recursive ~1500 chars, max N chunks. */
public interface Chunker {
    List<Chunk> chunk(Decision decision);
}
