package cz.demo.caselaw.temporal;

import cz.demo.caselaw.ai.GraphEvent;
import cz.demo.caselaw.ai.ResearchOutcome;
import cz.demo.caselaw.domain.GraphNode;
import cz.demo.caselaw.domain.GraphView;
import cz.demo.caselaw.domain.JobStatus;
import cz.demo.caselaw.domain.Research;
import cz.demo.caselaw.domain.Step;
import cz.demo.caselaw.domain.StepStatus;
import cz.demo.caselaw.domain.TraceEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure state machine of a research job. Extracted out of the workflow so it can be unit tested
 * without a Temporal test environment: the workflow only supplies "now" and keeps the returned state.
 */
public final class ResearchStateMerger {

    /** Coarse UI steps, in display order. Graph node names are mapped onto them. */
    public static final List<String> STEP_NAMES = List.of(
            "Analýza dotazu",
            "Hybridní hledání",
            "Advocatus Diaboli",
            "Statistika výsledků",
            "Verdikt",
            "Ověření citací");

    private static final Map<String, Integer> NODE_TO_STEP = Map.of(
            "analyzeQuestion", 0,
            "retrieve", 1,
            "argueFor", 2,
            "argueAgainst", 2,
            "outcomeStats", 3,
            "judge", 4,
            "verifyCitations", 5);

    /** @param stepStarts step name to Workflow.currentTimeMillis() when the step first became active */
    public record MergeResult(Research state, Map<String, Long> stepStarts) {}

    public static int stepIndexFor(String nodeName) {
        return NODE_TO_STEP.getOrDefault(nodeName, -1);
    }

    public static List<Step> initialSteps() {
        List<Step> steps = new ArrayList<>(STEP_NAMES.size());
        for (String name : STEP_NAMES) {
            steps.add(new Step(name, StepStatus.PENDING, null, null, null));
        }
        return List.copyOf(steps);
    }

    public static Research initial(String researchId, String question, String mermaid) {
        return new Research(
                researchId,
                question,
                JobStatus.RUNNING,
                initialSteps(),
                null,
                List.of(),
                null,
                null,
                new GraphView(mermaid == null ? "" : mermaid, List.of()),
                List.of());
    }

    /** Applies one partial graph update: node states, then the derived step statuses and payloads. */
    public static MergeResult merge(Research state, GraphEvent event, long nowMillis, Map<String, Long> stepStarts) {
        if (event == null) {
            return new MergeResult(state, stepStarts);
        }
        List<GraphNode> nodes = mergeNode(state.graph().nodes(), event.node());
        Map<String, Long> starts = new LinkedHashMap<>(stepStarts);
        List<Step> steps = recomputeSteps(state.steps(), nodes, event, nowMillis, starts);

        Research merged = new Research(
                state.id(),
                state.question(),
                state.status(),
                steps,
                event.analysis() != null ? event.analysis() : state.analysis(),
                event.hits() != null ? event.hits() : state.hits(),
                event.answer() != null ? event.answer() : state.answer(),
                event.outcome() != null ? event.outcome() : state.outcome(),
                new GraphView(state.graph().mermaid(), nodes),
                state.trace());
        return new MergeResult(merged, Map.copyOf(starts));
    }

    /** Final state once the graph activity returned everything it produced. */
    public static Research complete(Research state, ResearchOutcome outcome) {
        List<Step> steps = new ArrayList<>();
        for (Step s : state.steps()) {
            boolean terminal = s.status() == StepStatus.DONE || s.status() == StepStatus.FAILED
                    || s.status() == StepStatus.SKIPPED;
            steps.add(terminal ? s : new Step(s.name(), StepStatus.DONE, s.durationMs(), s.attempts(), s.detail()));
        }
        List<GraphNode> nodes = outcome.nodes() != null && !outcome.nodes().isEmpty()
                ? List.copyOf(outcome.nodes())
                : state.graph().nodes();
        return new Research(
                state.id(),
                state.question(),
                JobStatus.COMPLETED,
                List.copyOf(steps),
                outcome.analysis() != null ? outcome.analysis() : state.analysis(),
                outcome.hits() != null ? List.copyOf(outcome.hits()) : state.hits(),
                outcome.answer() != null ? outcome.answer() : state.answer(),
                outcome.outcome() != null ? outcome.outcome() : state.outcome(),
                new GraphView(state.graph().mermaid(), nodes),
                outcome.trace() != null ? List.copyOf(outcome.trace()) : state.trace());
    }

    /** The graph activity gave up after its retries: keep what was collected, drop the answer. */
    public static Research fail(Research state, String errorMessage) {
        List<Step> steps = new ArrayList<>();
        boolean markedOne = false;
        for (Step s : state.steps()) {
            if (!markedOne && (s.status() == StepStatus.RUNNING || s.status() == StepStatus.PENDING)) {
                steps.add(new Step(s.name(), StepStatus.FAILED, s.durationMs(), s.attempts(), errorMessage));
                markedOne = true;
            } else if (s.status() == StepStatus.PENDING) {
                steps.add(new Step(s.name(), StepStatus.SKIPPED, null, null, null));
            } else {
                steps.add(s);
            }
        }
        List<TraceEntry> trace = new ArrayList<>(state.trace());
        trace.add(new TraceEntry("Temporal", "runResearchGraph", StepStatus.FAILED, null, errorMessage));
        return new Research(
                state.id(),
                state.question(),
                JobStatus.FAILED,
                List.copyOf(steps),
                state.analysis(),
                state.hits(),
                null,
                state.outcome(),
                state.graph(),
                List.copyOf(trace));
    }

    /** Latest state per (name, attempt): a retried node appears as its own row in the graph view. */
    private static List<GraphNode> mergeNode(List<GraphNode> existing, GraphNode incoming) {
        if (incoming == null) {
            return existing;
        }
        List<GraphNode> nodes = new ArrayList<>(existing);
        for (int i = 0; i < nodes.size(); i++) {
            if (sameNode(nodes.get(i), incoming)) {
                nodes.set(i, incoming);
                return List.copyOf(nodes);
            }
        }
        nodes.add(incoming);
        return List.copyOf(nodes);
    }

    private static boolean sameNode(GraphNode a, GraphNode b) {
        return a.name().equals(b.name()) && attempt(a) == attempt(b);
    }

    private static int attempt(GraphNode n) {
        return n.attempt() == null ? 1 : n.attempt();
    }

    private static List<Step> recomputeSteps(List<Step> current, List<GraphNode> nodes, GraphEvent event,
                                             long nowMillis, Map<String, Long> starts) {
        List<Step> steps = new ArrayList<>(current.size());
        for (int i = 0; i < current.size(); i++) {
            Step step = current.get(i);
            List<GraphNode> own = nodesOfStep(nodes, i);
            if (own.isEmpty()) {
                steps.add(step);
                continue;
            }
            starts.putIfAbsent(step.name(), nowMillis);
            StepStatus status = statusOf(own);
            Integer duration = step.durationMs();
            if (status != StepStatus.RUNNING) {
                duration = (int) (nowMillis - starts.get(step.name()));
            }
            Integer attempts = own.stream().map(ResearchStateMerger::attempt).max(Integer::compareTo).orElse(1);
            steps.add(new Step(step.name(), status, duration, attempts, detailFor(event, i, step.detail())));
        }
        return List.copyOf(steps);
    }

    private static List<GraphNode> nodesOfStep(List<GraphNode> nodes, int stepIndex) {
        List<GraphNode> own = new ArrayList<>();
        for (GraphNode n : nodes) {
            if (stepIndexFor(n.name()) == stepIndex) {
                own.add(n);
            }
        }
        return own;
    }

    private static StepStatus statusOf(List<GraphNode> own) {
        if (own.stream().anyMatch(n -> n.status() == StepStatus.FAILED)) {
            return StepStatus.FAILED;
        }
        boolean allDone = own.stream().allMatch(n -> n.status() == StepStatus.DONE || n.status() == StepStatus.SKIPPED);
        return allDone ? StepStatus.DONE : StepStatus.RUNNING;
    }

    private static String detailFor(GraphEvent event, int stepIndex, String fallback) {
        GraphNode node = event.node();
        if (node != null && stepIndexFor(node.name()) == stepIndex && node.detail() != null) {
            return node.detail();
        }
        return fallback;
    }

    private ResearchStateMerger() {}
}
