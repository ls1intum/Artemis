package de.tum.cit.aet.artemis.hyperion.protocol;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Fixed JSON wire format, independent of either application's HTTP mapper or Java serialization. */
public final class WorkerMessageCodec {

    public static final int MAX_MESSAGE_CHARS = 64 * 1024 * 1024;

    private final JsonMapper mapper = JsonMapper
            .builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(12 * 1024 * 1024).maxNumberLength(32).build()).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public String encode(WorkerCommand command) {
        return bounded(mapper.writeValueAsString(command));
    }

    public String encode(WorkerEvent event) {
        return bounded(mapper.writeValueAsString(event));
    }

    public WorkerCommand decodeCommand(String body) {
        return mapper.readValue(bounded(body), WorkerCommand.class);
    }

    public WorkerEvent decodeEvent(String body) {
        return mapper.readValue(bounded(body), WorkerEvent.class);
    }

    private static String bounded(String body) {
        if (body == null || body.length() > MAX_MESSAGE_CHARS) {
            throw new IllegalArgumentException("Worker message exceeds its size limit");
        }
        return body;
    }
}
