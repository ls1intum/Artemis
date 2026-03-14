package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.buildagent.dto.BuildResultQueueException;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;

class RedissonCodecConfigurationTest {

    @Test
    void roundTripWithQueueSafeException() throws Exception {
        ObjectMapper objectMapper = configuredMapper();
        ResultQueueItem resultQueueItem = new ResultQueueItem(null, null, List.of(),
                BuildResultQueueException.from(new CompletionException("build failed", new ExecutionException(new TimeoutException("timed out")))));

        String json = objectMapper.writeValueAsString(resultQueueItem);
        ResultQueueItem deserialized = objectMapper.readValue(json, ResultQueueItem.class);

        assertThat(deserialized.exception()).isInstanceOf(BuildResultQueueException.class);
        BuildResultQueueException exception = (BuildResultQueueException) deserialized.exception();
        assertThat(exception.getOriginalClassName()).isEqualTo(CompletionException.class.getName());
        assertThat(exception.getCause()).isInstanceOf(BuildResultQueueException.class);
        assertThat(((BuildResultQueueException) exception.getCause()).getOriginalClassName()).isEqualTo(ExecutionException.class.getName());
    }

    private static ObjectMapper configuredMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        RedissonCodecConfiguration.configureObjectMapper(objectMapper);
        RedissonCodecConfiguration.configureTypeInclusion(objectMapper);
        return objectMapper;
    }
}
