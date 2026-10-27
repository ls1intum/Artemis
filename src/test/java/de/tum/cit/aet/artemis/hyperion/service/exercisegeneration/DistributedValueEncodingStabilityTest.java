package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.redisson.client.handler.State;

import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.BackwardCompatibleSerializationCodec;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationLiveSaveService.LiveSaveSlot;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.ExerciseGenerationBaseline;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationGrading;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Redis implements {@code replace(key, expected, replacement)} and {@code remove(key, value)} by comparing the
 * <em>encoded bytes</em> of the expected value with the stored bytes, not by {@code equals}. A value that is read back
 * from the store therefore only works as the {@code expected} argument if encoding it again yields exactly the bytes it
 * was read from.
 *
 * <p>
 * {@code Map.copyOf} and {@code Map.of} break that: their iteration order depends on a per-JVM random salt and on the
 * insertion order of colliding keys, so a map with two or more entries is serialized in an order that changes after a
 * round trip. The compare-and-set then fails forever, and the retry loops in the live-save coordination spin on it.
 * That is how {@code GenerationJobServiceRedisTest} hung a whole CI shard for an hour.
 *
 * <p>
 * Each case repeats with varied keys from a fixed seed because failing layouts depend on the key hashes. The samples
 * are reproducible, while a few hundred repetitions expose the unstable maps regardless of the JVM salt.
 */
class DistributedValueEncodingStabilityTest {

    private static final int REPETITIONS = 400;

    private final BackwardCompatibleSerializationCodec codec = new BackwardCompatibleSerializationCodec();

    @Test
    void liveSaveSlotEncodesToTheSameBytesAfterARoundTrip() throws IOException {
        Random random = new Random(0x5eedL);
        for (int attempt = 0; attempt < REPETITIONS; attempt++) {
            var readers = new HashMap<String, String>();
            for (int reader = 0; reader <= attempt % 7; reader++) {
                readers.put("live-reader:" + new UUID(random.nextLong(), random.nextLong()), "node-" + reader % 2);
            }
            assertEncodingIsStable(new LiveSaveSlot("live-copy:" + new UUID(random.nextLong(), random.nextLong()), "node-0", Instant.parse("2026-01-01T00:00:00Z"), readers));
        }
    }

    @Test
    void generationBaselineEncodesToTheSameBytesAfterARoundTrip() throws IOException {
        Random random = new Random(0x5eedL);
        for (int attempt = 0; attempt < REPETITIONS; attempt++) {
            var heads = new EnumMap<RepositoryType, String>(RepositoryType.class);
            heads.put(RepositoryType.TEMPLATE, new UUID(random.nextLong(), random.nextLong()).toString());
            heads.put(RepositoryType.SOLUTION, new UUID(random.nextLong(), random.nextLong()).toString());
            heads.put(RepositoryType.TESTS, new UUID(random.nextLong(), random.nextLong()).toString());
            var tests = new LinkedHashMap<String, GenerationGrading.Grading>();
            for (int test = 0; test <= attempt % 12; test++) {
                tests.put("test" + new UUID(random.nextLong(), random.nextLong()), new GenerationGrading.Grading(1.0 + test, Visibility.ALWAYS, null));
            }
            var grading = new GenerationGrading.Snapshot(tests);
            assertEncodingIsStable(
                    new ExerciseGenerationBaseline("job", GenerationMode.GENERATE, heads, heads, "statement", "title", "statement", "title", "main", grading, grading));
        }
    }

    private void assertEncodingIsStable(Object value) throws IOException {
        byte[] stored = encode(value);
        Object readBack = decode(stored);
        assertThat(readBack).isEqualTo(value);
        assertThat(encode(readBack)).as("re-encoding what was read back must reproduce the stored bytes, or Redis compare-and-set never matches it").isEqualTo(stored);
    }

    private byte[] encode(Object value) throws IOException {
        ByteBuf buffer = codec.getMapValueEncoder().encode(value);
        try {
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            return bytes;
        }
        finally {
            buffer.release();
        }
    }

    private Object decode(byte[] bytes) throws IOException {
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        try {
            return codec.getMapValueDecoder().decode(buffer, new State());
        }
        finally {
            buffer.release();
        }
    }
}
