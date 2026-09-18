package cz.demo.caselaw.ai;

import org.bsc.langgraph4j.serializer.StateSerializer;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LangGraph4j clones the state before running parallel branches, and its default serializer uses
 * Java object streams - which would force every domain record into Serializable.
 *
 * One research run lives entirely inside one Temporal activity and is never checkpointed, so the
 * state never has to leave the JVM. This serializer therefore hands the state over through an
 * in-process stash and writes only a token into the stream: a shallow copy, no Serializable
 * requirement, no reflection.
 */
public class InMemoryStateSerializer extends StateSerializer<ResearchState> {

    private final Map<Long, Map<String, Object>> stash = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public InMemoryStateSerializer() {
        super(ResearchState::new);
    }

    @Override
    public void writeData(Map<String, Object> data, ObjectOutput out) throws java.io.IOException {
        long token = sequence.incrementAndGet();
        stash.put(token, new HashMap<>(data));
        out.writeLong(token);
    }

    @Override
    public Map<String, Object> readData(ObjectInput in) throws java.io.IOException {
        Map<String, Object> data = stash.remove(in.readLong());
        return data == null ? new HashMap<>() : data;
    }
}
