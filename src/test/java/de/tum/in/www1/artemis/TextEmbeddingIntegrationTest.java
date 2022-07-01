package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.TextEmbedding;

class TextEmbeddingIntegrationTest {

    @Test
    void testTextEmbedding() {
        var vector = new float[] { 1.5f, 2.5f };

        TextEmbedding textEmbedding = new TextEmbedding();
        textEmbedding.setId("id");
        textEmbedding.setVector(vector);

        assertThat(textEmbedding.getId()).isEqualTo("id");
        assertThat(textEmbedding.getVector()).isEqualTo(vector);
    }

}
