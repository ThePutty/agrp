package cz.demo.caselaw.temporal;

import cz.demo.caselaw.ai.GraphEvent;
import cz.demo.caselaw.ai.ResearchOutcome;
import cz.demo.caselaw.domain.GraphNode;
import cz.demo.caselaw.domain.JobStatus;
import cz.demo.caselaw.domain.QueryAnalysis;
import cz.demo.caselaw.domain.Research;
import cz.demo.caselaw.domain.Step;
import cz.demo.caselaw.domain.StepStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchStateMergerTest {

    private static final String QUESTION = "Lze odstoupit od smlouvy o dílo?";

    @Test
    void initialStateHasAllStepsPending() {
        Research state = ResearchStateMerger.initial("research-1", QUESTION, "graph TD");

        assertThat(state.status()).isEqualTo(JobStatus.RUNNING);
        assertThat(state.steps()).hasSize(6).allMatch(s -> s.status() == StepStatus.PENDING);
        assertThat(state.graph().mermaid()).isEqualTo("graph TD");
        assertThat(state.hits()).isEmpty();
    }

    @Test
    void runningNodeMarksItsStepRunning() {
        Research state = ResearchStateMerger.initial("r", QUESTION, "");

        var merged = ResearchStateMerger.merge(state, node("analyzeQuestion", StepStatus.RUNNING), 1_000, Map.of());

        assertThat(step(merged.state(), 0).status()).isEqualTo(StepStatus.RUNNING);
        assertThat(step(merged.state(), 0).durationMs()).isNull();
        assertThat(merged.stepStarts()).containsEntry("Analýza dotazu", 1_000L);
    }

    @Test
    void doneNodeComputesStepDuration() {
        Research state = ResearchStateMerger.initial("r", QUESTION, "");
        var running = ResearchStateMerger.merge(state, node("retrieve", StepStatus.RUNNING), 1_000, Map.of());

        var done = ResearchStateMerger.merge(running.state(), node("retrieve", StepStatus.DONE), 3_500,
                running.stepStarts());

        assertThat(step(done.state(), 1).status()).isEqualTo(StepStatus.DONE);
        assertThat(step(done.state(), 1).durationMs()).isEqualTo(2_500);
    }

    @Test
    void advocatusDiaboliStepWaitsForBothNodes() {
        Research state = ResearchStateMerger.initial("r", QUESTION, "");
        var one = ResearchStateMerger.merge(state, node("argueFor", StepStatus.DONE), 100, Map.of());
        var two = ResearchStateMerger.merge(one.state(), node("argueAgainst", StepStatus.RUNNING), 150,
                one.stepStarts());

        assertThat(step(two.state(), 2).status()).isEqualTo(StepStatus.RUNNING);

        var both = ResearchStateMerger.merge(two.state(), node("argueAgainst", StepStatus.DONE), 300,
                two.stepStarts());
        assertThat(step(both.state(), 2).status()).isEqualTo(StepStatus.DONE);
    }

    @Test
    void retriedNodeKeepsBothAttemptsAndReportsAttemptCount() {
        Research state = ResearchStateMerger.initial("r", QUESTION, "");
        var first = ResearchStateMerger.merge(state,
                GraphEvent.nodeOnly(new GraphNode("judge", StepStatus.FAILED, null, null, 1, "timeout")), 10, Map.of());
        var second = ResearchStateMerger.merge(first.state(),
                GraphEvent.nodeOnly(new GraphNode("judge", StepStatus.DONE, null, null, 2, null)), 20,
                first.stepStarts());

        assertThat(second.state().graph().nodes()).hasSize(2);
        assertThat(step(second.state(), 4).attempts()).isEqualTo(2);
        // any failed attempt still present -> the step is reported as failed
        assertThat(step(second.state(), 4).status()).isEqualTo(StepStatus.FAILED);
    }

    @Test
    void sameNodeAndAttemptIsReplacedNotAppended() {
        Research state = ResearchStateMerger.initial("r", QUESTION, "");
        var a = ResearchStateMerger.merge(state, node("retrieve", StepStatus.RUNNING), 10, Map.of());
        var b = ResearchStateMerger.merge(a.state(), node("retrieve", StepStatus.DONE), 20, a.stepStarts());

        assertThat(b.state().graph().nodes()).hasSize(1);
        assertThat(b.state().graph().nodes().get(0).status()).isEqualTo(StepStatus.DONE);
    }

    @Test
    void nonNullPayloadsOverwriteAndNullsKeepPreviousValue() {
        Research state = ResearchStateMerger.initial("r", QUESTION, "");
        QueryAnalysis analysis = new QueryAnalysis(List.of("odstoupení"), List.of("§ 2586"), List.of("dílo"));

        var withAnalysis = ResearchStateMerger.merge(state,
                new GraphEvent(new GraphNode("analyzeQuestion", StepStatus.DONE, null, null, 1, null),
                        analysis, null, null, null), 10, Map.of());
        var later = ResearchStateMerger.merge(withAnalysis.state(), node("retrieve", StepStatus.RUNNING), 20,
                withAnalysis.stepStarts());

        assertThat(later.state().analysis()).isEqualTo(analysis);
    }

    @Test
    void unknownNodeNameDoesNotTouchSteps() {
        Research state = ResearchStateMerger.initial("r", QUESTION, "");

        var merged = ResearchStateMerger.merge(state, node("__start__", StepStatus.DONE), 10, Map.of());

        assertThat(merged.state().steps()).allMatch(s -> s.status() == StepStatus.PENDING);
        assertThat(merged.state().graph().nodes()).hasSize(1);
    }

    @Test
    void completeMarksRemainingStepsDoneAndTakesOutcomePayload() {
        Research state = ResearchStateMerger.initial("r", QUESTION, "graph TD");
        QueryAnalysis analysis = new QueryAnalysis(List.of("a"), List.of(), List.of());
        ResearchOutcome outcome = new ResearchOutcome(analysis, List.of(), null, null, List.of(), List.of());

        Research done = ResearchStateMerger.complete(state, outcome);

        assertThat(done.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(done.steps()).allMatch(s -> s.status() == StepStatus.DONE);
        assertThat(done.analysis()).isEqualTo(analysis);
        assertThat(done.graph().mermaid()).isEqualTo("graph TD");
    }

    @Test
    void failKeepsPartialStateDropsAnswerAndAddsTrace() {
        Research state = ResearchStateMerger.initial("r", QUESTION, "");
        var progressed = ResearchStateMerger.merge(state, node("analyzeQuestion", StepStatus.DONE), 10, Map.of());

        Research failed = ResearchStateMerger.fail(progressed.state(), "LLM timeout");

        assertThat(failed.status()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.answer()).isNull();
        assertThat(step(failed, 0).status()).isEqualTo(StepStatus.DONE);
        assertThat(step(failed, 1).status()).isEqualTo(StepStatus.FAILED);
        assertThat(step(failed, 1).detail()).isEqualTo("LLM timeout");
        assertThat(step(failed, 5).status()).isEqualTo(StepStatus.SKIPPED);
        assertThat(failed.trace()).singleElement()
                .satisfies(t -> assertThat(t.detail()).isEqualTo("LLM timeout"));
    }

    @Test
    void nodeNamesMapToTheExpectedSteps() {
        assertThat(ResearchStateMerger.stepIndexFor("analyzeQuestion")).isZero();
        assertThat(ResearchStateMerger.stepIndexFor("retrieve")).isEqualTo(1);
        assertThat(ResearchStateMerger.stepIndexFor("argueFor")).isEqualTo(2);
        assertThat(ResearchStateMerger.stepIndexFor("argueAgainst")).isEqualTo(2);
        assertThat(ResearchStateMerger.stepIndexFor("outcomeStats")).isEqualTo(3);
        assertThat(ResearchStateMerger.stepIndexFor("judge")).isEqualTo(4);
        assertThat(ResearchStateMerger.stepIndexFor("verifyCitations")).isEqualTo(5);
        assertThat(ResearchStateMerger.stepIndexFor("nope")).isEqualTo(-1);
    }

    private static GraphEvent node(String name, StepStatus status) {
        return GraphEvent.nodeOnly(new GraphNode(name, status, null, null, 1, null));
    }

    private static Step step(Research state, int index) {
        return state.steps().get(index);
    }
}
