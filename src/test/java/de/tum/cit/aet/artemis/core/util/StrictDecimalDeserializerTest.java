package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

class StrictDecimalDeserializerTest {

    private record Holder(@JsonDeserialize(using = StrictDecimalDeserializer.class) Double value) {
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserialize_plainDecimal_succeeds() throws IOException {
        Holder holder = objectMapper.readValue("{\"value\": 100.5}", Holder.class);
        assertThat(holder.value()).isEqualTo(100.5);
    }

    @Test
    void deserialize_plainInteger_succeeds() throws IOException {
        Holder holder = objectMapper.readValue("{\"value\": 100}", Holder.class);
        assertThat(holder.value()).isEqualTo(100.0);
    }

    @Test
    void deserialize_negativeDecimal_succeeds() throws IOException {
        Holder holder = objectMapper.readValue("{\"value\": -1.5}", Holder.class);
        assertThat(holder.value()).isEqualTo(-1.5);
    }

    @Test
    void deserialize_null_succeeds() throws IOException {
        Holder holder = objectMapper.readValue("{\"value\": null}", Holder.class);
        assertThat(holder.value()).isNull();
    }

    @Test
    void deserialize_scientificNotation_throws() {
        // issue #12451: a post-parse check on the Double can't tell "1e-3" apart from "0.001" - they are the
        // identical value - so rejecting it requires looking at the raw JSON token, which is what this test proves.
        assertThatThrownBy(() -> objectMapper.readValue("{\"value\": 1e-3}", Holder.class)).isInstanceOf(JsonMappingException.class);
    }

    @Test
    void deserialize_tinyScientificNotation_throws() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"value\": 1e-30}", Holder.class)).isInstanceOf(JsonMappingException.class);
    }

    @Test
    void deserialize_positiveExponentScientificNotation_throws() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"value\": 1e2}", Holder.class)).isInstanceOf(JsonMappingException.class);
    }
}
