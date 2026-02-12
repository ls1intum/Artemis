package de.tum.cit.aet.artemis.globalsearch.config.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WeaviateDataTypeTest {

    @ParameterizedTest
    @EnumSource(WeaviateDataType.class)
    void getWeaviateName_returnsLowercaseString(WeaviateDataType type) {
        assertThat(type.getWeaviateName()).isNotBlank();
        assertThat(type.getWeaviateName()).isEqualTo(type.getWeaviateName().toLowerCase());
    }

    @Test
    void getWeaviateName_returnsExpectedValues() {
        assertThat(WeaviateDataType.INT.getWeaviateName()).isEqualTo("int");
        assertThat(WeaviateDataType.TEXT.getWeaviateName()).isEqualTo("text");
        assertThat(WeaviateDataType.NUMBER.getWeaviateName()).isEqualTo("number");
        assertThat(WeaviateDataType.BOOLEAN.getWeaviateName()).isEqualTo("boolean");
        assertThat(WeaviateDataType.DATE.getWeaviateName()).isEqualTo("date");
        assertThat(WeaviateDataType.UUID.getWeaviateName()).isEqualTo("uuid");
        assertThat(WeaviateDataType.BLOB.getWeaviateName()).isEqualTo("blob");
    }

    @ParameterizedTest
    @EnumSource(WeaviateDataType.class)
    void fromWeaviateName_roundTripsAllTypes(WeaviateDataType type) {
        assertThat(WeaviateDataType.fromWeaviateName(type.getWeaviateName())).isEqualTo(type);
    }

    @Test
    void fromWeaviateName_isCaseInsensitive() {
        assertThat(WeaviateDataType.fromWeaviateName("TEXT")).isEqualTo(WeaviateDataType.TEXT);
        assertThat(WeaviateDataType.fromWeaviateName("Int")).isEqualTo(WeaviateDataType.INT);
        assertThat(WeaviateDataType.fromWeaviateName("Boolean")).isEqualTo(WeaviateDataType.BOOLEAN);
    }

    @Test
    void fromWeaviateName_throwsForUnknownType() {
        assertThatThrownBy(() -> WeaviateDataType.fromWeaviateName("unknown")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown");
    }
}
