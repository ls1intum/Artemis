package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.json.JsonMapper;

class StrictIntegerDeserializerTest {

    private final JsonMapper mapper = new JsonMapper();

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
        assertThatThrownBy(() -> mapper.readValue("{\"value\": 10.5}", Wrapper.class)).isInstanceOf(JacksonException.class);
    }

    @Test
    void rejectsFractionalString() {
        assertThatThrownBy(() -> mapper.readValue("{\"value\": \"10.5\"}", Wrapper.class)).isInstanceOf(JacksonException.class);
    }
}
