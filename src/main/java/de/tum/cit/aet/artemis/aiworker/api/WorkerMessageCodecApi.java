package de.tum.cit.aet.artemis.aiworker.api;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.aiworker.dto.WorkerCommandDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;

/** Fixed JSON wire format, independent of either application's HTTP mapper or Java serialization. */
public final class WorkerMessageCodecApi {

    public static final int MAX_MESSAGE_CHARS = 64 * 1024 * 1024;

    private final JsonMapper mapper = JsonMapper
            .builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(48 * 1024 * 1024).maxNumberLength(32).build()).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public String encode(WorkerCommandDTO command) {
        return bounded(mapper.writeValueAsString(command));
    }

    public String encode(WorkerEventDTO event) {
        return bounded(mapper.writeValueAsString(event));
    }

    public WorkerCommandDTO decodeCommand(String body) {
        return mapper.readValue(bounded(body), WorkerCommandDTO.class);
    }

    public WorkerEventDTO decodeEvent(String body) {
        return mapper.readValue(bounded(body), WorkerEventDTO.class);
    }

    private static String bounded(String body) {
        if (body == null || body.length() > MAX_MESSAGE_CHARS) {
            throw new IllegalArgumentException("Worker message exceeds its size limit");
        }
        return body;
    }
}
