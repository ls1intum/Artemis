package de.tum.cit.aet.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

class OpenAPIConfigurationTest {

    record PrimitiveComponentsDTO(long count, boolean active, Long boxedCount, String title) {
    }

    @Test
    void shouldMarkOnlyPrimitiveRecordComponentsRequired() {
        Schema<?> schema = new ObjectSchema().addProperty("count", new IntegerSchema()).addProperty("active", new BooleanSchema()).addProperty("boxedCount", new IntegerSchema())
                .addProperty("title", new StringSchema());
        schema.addRequiredItem("title");
        schema.addRequiredItem("count");
        Map<String, Schema> schemas = new HashMap<>(Map.of("PrimitiveComponentsDTO", schema));

        OpenAPIConfiguration.markPrimitiveRecordComponentsRequired(schemas);

        assertThat(schema.getRequired()).containsExactlyInAnyOrder("title", "count", "active");
    }
}
