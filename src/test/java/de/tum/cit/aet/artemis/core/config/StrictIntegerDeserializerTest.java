package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

class StrictIntegerDeserializerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private record Wrapper(@JsonDeserialize(using = StrictIntegerDeserializer.class) Integer value) {
    }

    @Test
    void acceptsWholeNumber() throws Exception {
        assertThat(mapper.readValue("{\"value\": 100}", Wrapper.class).value()).isEqualTo(100);
    }

    @Test
    void preservesNull() throws Exception {
        assertThat(mapper.readValue("{\"value\": null}", Wrapper.class).value()).isNull();
    }

    @Test
    void preservesAbsentFieldAsNull() throws Exception {
        assertThat(mapper.readValue("{}", Wrapper.class).value()).isNull();
    }

    @Test
    void rejectsFractionalNumber() {
        assertThatThrownBy(() -> mapper.readValue("{\"value\": 10.5}", Wrapper.class)).isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void rejectsFractionalString() {
        assertThatThrownBy(() -> mapper.readValue("{\"value\": \"10.5\"}", Wrapper.class)).isInstanceOf(JsonProcessingException.class);
    }
}
