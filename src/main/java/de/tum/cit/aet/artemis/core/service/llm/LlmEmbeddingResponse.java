package de.tum.cit.aet.artemis.core.service.llm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.core.config.StrictLongDeserializer;

/** Vectors and input positions returned by an OpenAI-compatible embeddings endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmEmbeddingResponse(@Nullable List<Entry> data) {

    /** Each index identifies an input in the original batch. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(@Nullable @JsonDeserialize(using = StrictLongDeserializer.class) Long index,
            @JsonDeserialize(using = VectorDeserializer.class) float @Nullable [] embedding) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Entry that && Objects.equals(index, that.index) && Arrays.equals(embedding, that.embedding);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hashCode(index) + Arrays.hashCode(embedding);
        }

        @Override
        public String toString() {
            return "Entry[index=" + index + ", dimensions=" + (embedding == null ? 0 : embedding.length) + "]";
        }
    }

    // Providers may send little-endian IEEE 754 floats as base64, even when the request asks for a JSON array.
    static final class VectorDeserializer extends ValueDeserializer<float[]> {

        @Override
        public float[] deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.hasToken(JsonToken.VALUE_STRING)) {
                byte[] bytes;
                try {
                    bytes = Base64.getDecoder().decode(parser.getString());
                }
                catch (IllegalArgumentException ex) {
                    return context.reportInputMismatch(float[].class, "Invalid base64 embedding");
                }
                if (bytes.length % Float.BYTES != 0) {
                    return context.reportInputMismatch(float[].class, "Base64 embedding byte length %d is not a multiple of %d", bytes.length, Float.BYTES);
                }
                float[] vector = new float[bytes.length / Float.BYTES];
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(vector);
                return vector;
            }
            if (!parser.hasToken(JsonToken.START_ARRAY)) {
                return context.reportInputMismatch(float[].class, "Expected an embedding array or base64 string");
            }
            var builder = context.getArrayBuilders().getFloatBuilder();
            float[] chunk = builder.resetAndStart();
            int size = 0;
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (!parser.currentToken().isNumeric()) {
                    return context.reportInputMismatch(float[].class, "Expected a number in the embedding array");
                }
                if (size == chunk.length) {
                    chunk = builder.appendCompletedChunk(chunk, size);
                    size = 0;
                }
                chunk[size++] = parser.getFloatValue();
            }
            return builder.completeAndClearBuffer(chunk, size);
        }
    }
}
