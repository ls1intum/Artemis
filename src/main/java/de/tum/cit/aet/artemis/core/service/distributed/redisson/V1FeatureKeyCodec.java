package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import java.util.List;
import java.util.Optional;

import org.redisson.codec.Kryo5Codec;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import de.tum.cit.aet.artemis.core.service.feature.Feature;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

/**
 * Re-encodes a key of the distributed feature map from schema version 1 to the current {@link Feature} enum.
 *
 * <p>
 * Kryo writes an enum as its class name followed by its position, so the bytes of a key say "the ninth constant", not
 * "AtlasAgent". Version 2 removed {@code AtlasML}, which moved every later constant one position up. Reading a v1 key
 * with the current enum would therefore turn a stored AtlasAgent toggle into Memiris. This codec reuses Redisson's own
 * Kryo setup, so the stream is read exactly as it was written, and only swaps the {@link Feature} serializer for one that
 * resolves the position against the frozen v1 order.
 */
final class V1FeatureKeyCodec extends Kryo5Codec {

    /**
     * The {@link Feature} constants in the order schema version 1 encoded them. Frozen: never edit this list.
     */
    static final List<String> V1_FEATURE_ORDER = List.of("ProgrammingExercises", "PlagiarismChecks", "Exports", "LearningPaths", "Science", "StandardizedCompetencies",
            "TutorSuggestions", "AtlasML", "AtlasAgent", "Memiris", "LectureContentProcessing", "RateLimit", "GlobalSearch", "AutonomousTutor", "Deimos", "IrisProactiveStruggle",
            "GlobalSearchReconcile", "GlobalSearchReconcileOrphan");

    /** The map-key encoding the provider writes with, see {@link BackwardCompatibleSerializationCodec#getMapKeyEncoder()}. */
    private final BackwardCompatibleSerializationCodec currentCodec = new BackwardCompatibleSerializationCodec();

    @Override
    protected Kryo createKryo(ClassLoader classLoader, boolean useReferences) throws ClassNotFoundException {
        Kryo kryo = super.createKryo(classLoader, useReferences);
        kryo.addDefaultSerializer(Feature.class, new V1FeatureSerializer());
        return kryo;
    }

    /**
     * @param v1Key a key of the v1 feature map, as stored
     * @return the same feature encoded for the current schema, or empty if the feature no longer exists
     * @throws IllegalArgumentException if the bytes are not a v1 feature key
     */
    Optional<byte[]> reencode(byte[] v1Key) {
        Object decoded;
        ByteBuf in = Unpooled.wrappedBuffer(v1Key);
        try {
            decoded = getValueDecoder().decode(in, null);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Not a v1 feature key", e);
        }
        finally {
            in.release();
        }
        if (decoded == null) {
            return Optional.empty();
        }
        if (!(decoded instanceof Feature feature)) {
            throw new IllegalArgumentException("Not a v1 feature key: decoded to " + decoded.getClass().getName());
        }
        ByteBuf out;
        try {
            out = currentCodec.getMapKeyEncoder().encode(feature);
        }
        catch (Exception e) {
            throw new IllegalStateException("Could not encode feature " + feature, e);
        }
        try {
            return Optional.of(ByteBufUtil.getBytes(out));
        }
        finally {
            out.release();
        }
    }

    /**
     * Reads a position the way Kryo's enum serializer wrote it ({@code ordinal + 1}, {@code 0} for null) and maps it
     * through {@link #V1_FEATURE_ORDER}. A feature removed since version 1 reads as {@code null}.
     */
    private static final class V1FeatureSerializer extends Serializer<Feature> {

        @Override
        public void write(Kryo kryo, Output output, Feature feature) {
            throw new UnsupportedOperationException("Only reads version 1 keys");
        }

        @Override
        public Feature read(Kryo kryo, Input input, Class<? extends Feature> type) {
            int position = input.readVarInt(true) - 1;
            if (position < 0) {
                return null;
            }
            if (position >= V1_FEATURE_ORDER.size()) {
                throw new IllegalArgumentException("Position " + position + " is outside the v1 feature order");
            }
            String name = V1_FEATURE_ORDER.get(position);
            for (Feature feature : Feature.values()) {
                if (feature.name().equals(name)) {
                    return feature;
                }
            }
            return null;
        }
    }
}
