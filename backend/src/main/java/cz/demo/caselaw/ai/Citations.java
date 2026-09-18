package cz.demo.caselaw.ai;

import cz.demo.caselaw.domain.Argument;
import cz.demo.caselaw.domain.Citation;
import cz.demo.caselaw.domain.Hit;
import cz.demo.caselaw.domain.Side;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps LLM citation DTOs to domain citations and merges the two sides into one globally numbered
 * list (FOR first, then AGAINST), remapping every argument's citationIndexes.
 *
 * Each side numbers its citations from 1 independently, so without re-indexing the UI would show
 * two different citations called "[1]".
 */
public final class Citations {

    private Citations() {
    }

    /** DTO -> domain. Unknown decision ids are resolved by case number when possible; the deterministic verifier has the final word. */
    public static List<Citation> fromDto(List<Dtos.CitationDto> dtos, Side side, List<Hit> hits) {
        List<Citation> result = new ArrayList<>();
        if (dtos == null) {
            return result;
        }
        int index = 0;
        for (Dtos.CitationDto dto : dtos) {
            if (dto == null || dto.quote() == null || dto.quote().isBlank()) {
                continue;
            }
            index++;
            result.add(new Citation(index, resolveId(dto, hits), dto.caseNumber(), dto.quote(), false, null, side));
        }
        return result;
    }

    private static UUID resolveId(Dtos.CitationDto dto, List<Hit> hits) {
        if (dto.decisionId() != null) {
            try {
                return UUID.fromString(dto.decisionId().trim());
            } catch (IllegalArgumentException ignored) {
                // fall through to the case-number lookup
            }
        }
        if (dto.caseNumber() != null && hits != null) {
            for (Hit hit : hits) {
                if (hit.decision() != null && dto.caseNumber().equalsIgnoreCase(hit.decision().caseNumber())) {
                    return hit.decision().id();
                }
            }
        }
        return null;
    }

    public static List<Argument> argumentsFromDto(List<Dtos.ArgumentDto> dtos) {
        List<Argument> result = new ArrayList<>();
        if (dtos == null) {
            return result;
        }
        for (Dtos.ArgumentDto dto : dtos) {
            if (dto == null || dto.claim() == null || dto.claim().isBlank()) {
                continue;
            }
            List<Integer> indexes = dto.citationIndexes() == null ? List.of() : List.copyOf(dto.citationIndexes());
            result.add(new Argument(dto.claim(), dto.reasoning(), indexes));
        }
        return result;
    }

    /** Result of merging both sides into one numbering. */
    public record Merged(List<Argument> forArguments, List<Argument> againstArguments, List<Citation> citations) {
    }

    public static Merged merge(List<Argument> forArgs, List<Citation> forCitations,
                               List<Argument> againstArgs, List<Citation> againstCitations) {
        List<Citation> merged = new ArrayList<>();
        Map<Integer, Integer> forMap = append(merged, forCitations);
        Map<Integer, Integer> againstMap = append(merged, againstCitations);
        return new Merged(remap(forArgs, forMap), remap(againstArgs, againstMap), merged);
    }

    private static Map<Integer, Integer> append(List<Citation> target, List<Citation> source) {
        Map<Integer, Integer> mapping = new HashMap<>();
        if (source == null) {
            return mapping;
        }
        for (Citation c : source) {
            int newIndex = target.size() + 1;
            mapping.put(c.index(), newIndex);
            target.add(new Citation(newIndex, c.decisionId(), c.caseNumber(), c.quote(), c.verified(), c.reason(), c.side()));
        }
        return mapping;
    }

    private static List<Argument> remap(List<Argument> arguments, Map<Integer, Integer> mapping) {
        List<Argument> result = new ArrayList<>();
        if (arguments == null) {
            return result;
        }
        for (Argument a : arguments) {
            List<Integer> indexes = new ArrayList<>();
            if (a.citationIndexes() != null) {
                for (Integer old : a.citationIndexes()) {
                    Integer mapped = mapping.get(old);
                    if (mapped != null) {
                        indexes.add(mapped);
                    }
                }
            }
            result.add(new Argument(a.claim(), a.reasoning(), indexes));
        }
        return result;
    }
}
