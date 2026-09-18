package cz.demo.caselaw.ai;

import cz.demo.caselaw.domain.Argument;
import cz.demo.caselaw.domain.Citation;
import cz.demo.caselaw.domain.Decision;
import cz.demo.caselaw.domain.GraphNode;
import cz.demo.caselaw.domain.Hit;
import cz.demo.caselaw.domain.OutcomeStats;
import cz.demo.caselaw.domain.QueryAnalysis;
import cz.demo.caselaw.domain.Side;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LangGraph4j state for one research run. Domain records are kept in the map as-is: the run lives
 * inside a single Temporal activity, so nothing is ever serialized or checkpointed.
 *
 * Only `nodeTrace` is an appender channel - the three parallel branches must not write the same
 * key, which is why FOR/AGAINST/statistics have separate keys.
 */
public class ResearchState extends AgentState {

    public static final String QUESTION = "question";
    public static final String ANALYSIS = "analysis";
    public static final String HITS = "hits";
    public static final String SIMILAR = "similar";
    public static final String FOR_ARGS = "forArgs";
    public static final String AGAINST_ARGS = "againstArgs";
    public static final String FOR_CITATIONS = "forCitations";
    public static final String AGAINST_CITATIONS = "againstCitations";
    public static final String OUTCOME = "outcome";
    public static final String VERDICT = "verdict";
    public static final String STRONGER_SIDE = "strongerSide";
    public static final String VERIFICATION = "verification";
    public static final String ATTEMPTS = "attempts";
    public static final String FEEDBACK_FOR = "feedbackFor";
    public static final String FEEDBACK_AGAINST = "feedbackAgainst";
    public static final String NODE_TRACE = "nodeTrace";

    public static final Map<String, Channel<?>> SCHEMA =
            Map.of(NODE_TRACE, Channels.appender(ArrayList::new));

    public ResearchState(Map<String, Object> initData) {
        super(initData);
    }

    public String question() {
        return this.<String>value(QUESTION).orElse("");
    }

    public QueryAnalysis analysis() {
        return this.<QueryAnalysis>value(ANALYSIS).orElse(null);
    }

    public List<Hit> hits() {
        return this.<List<Hit>>value(HITS).orElseGet(List::of);
    }

    public List<Decision> similar() {
        return this.<List<Decision>>value(SIMILAR).orElseGet(List::of);
    }

    public List<Argument> forArgs() {
        return this.<List<Argument>>value(FOR_ARGS).orElseGet(List::of);
    }

    public List<Argument> againstArgs() {
        return this.<List<Argument>>value(AGAINST_ARGS).orElseGet(List::of);
    }

    public List<Citation> forCitations() {
        return this.<List<Citation>>value(FOR_CITATIONS).orElseGet(List::of);
    }

    public List<Citation> againstCitations() {
        return this.<List<Citation>>value(AGAINST_CITATIONS).orElseGet(List::of);
    }

    public OutcomeStats outcome() {
        return this.<OutcomeStats>value(OUTCOME).orElse(null);
    }

    public String verdict() {
        return this.<String>value(VERDICT).orElse(null);
    }

    public Side strongerSide() {
        return this.<Side>value(STRONGER_SIDE).orElse(Side.BALANCED);
    }

    public List<Citation> verification() {
        return this.<List<Citation>>value(VERIFICATION).orElseGet(List::of);
    }

    public int attempts() {
        return this.<Integer>value(ATTEMPTS).orElse(1);
    }

    public String feedbackFor() {
        return this.<String>value(FEEDBACK_FOR).orElse(null);
    }

    public String feedbackAgainst() {
        return this.<String>value(FEEDBACK_AGAINST).orElse(null);
    }

    public List<GraphNode> nodeTrace() {
        return this.<List<GraphNode>>value(NODE_TRACE).orElseGet(List::of);
    }
}
