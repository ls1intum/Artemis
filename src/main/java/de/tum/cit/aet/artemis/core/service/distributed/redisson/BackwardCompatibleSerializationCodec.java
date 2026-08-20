package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import java.io.ObjectStreamConstants;

import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.redisson.codec.Kryo5Codec;
import org.redisson.codec.SerializationCodec;

import io.netty.buffer.ByteBuf;

/**
 * Serializes Redis <em>map values</em> the way Hazelcast does, and leaves every other encoding untouched.
 *
 * <p>
 * Redisson defaults to Kryo, which does not run a value's own {@code writeObject}: it walks the fields reflectively
 * instead. That is what made {@code savedPosts} answer 500 under Redis while working under Hazelcast - the cached
 * {@code List<SavedPost>} reaches a {@code User} whose {@code authorities} is a lazy Hibernate collection, and Kryo
 * reads its size rather than letting it record that it is uninitialized. Map values are where that class of value
 * lives, because that is what backs the Spring caches.
 *
 * <p>
 * <b>Keys stay on Kryo, and so do plain values.</b> Redis addresses a hash field by the <em>bytes</em> of its encoded
 * key, so re-encoding a key does not find the entry that is already there: the lookup misses, a write inserts a second
 * field for the same logical key, and a remove silently does nothing. That is not theory - it was measured on a
 * deployment upgraded with an earlier version of this codec, where {@code processingJobs} kept five jobs no node could
 * delete (a warn loop every five seconds) and {@code features} ended up holding all thirteen toggles twice. Sets,
 * queues and topics address their elements by encoded bytes for the same reason, so they keep Kryo too.
 *
 * <p>
 * Map values are the one place where nothing is addressed by content, so they can change format safely. Old entries
 * stay readable because the decoder tells the formats apart by the value itself: a Java serialization stream starts
 * with {@link ObjectStreamConstants#STREAM_MAGIC}, which cannot start a Kryo stream. Nothing has to be configured or
 * remembered, and each value moves to the new format the first time any node rewrites it.
 *
 * <p>
 * Note for operators: an older node cannot read a map value this codec has rewritten, so roll a Redis-backed
 * deployment forward rather than back. Keys, sets, queues and topics are unaffected in both directions.
 */
public class BackwardCompatibleSerializationCodec extends BaseCodec {

    private final Codec mapValueFormat;

    private final Codec legacyFormat;

    private final Decoder<Object> mapValueDecoder;

    public BackwardCompatibleSerializationCodec() {
        this(new SerializationCodec(), new Kryo5Codec());
    }

    /**
     * Constructor used by Redisson's {@link BaseCodec#copy(ClassLoader, Object)} to rebind a codec to the class loader
     * that has to resolve the deserialized classes.
     *
     * @param classLoader the class loader to resolve value classes with
     */
    public BackwardCompatibleSerializationCodec(ClassLoader classLoader) {
        this(new SerializationCodec(classLoader), new Kryo5Codec(classLoader));
    }

    /**
     * Constructor used by Redisson's {@link BaseCodec#copy(ClassLoader, Object)} to rebind an existing instance.
     *
     * @param classLoader the class loader to resolve value classes with
     * @param codec       the instance being copied, whose configuration is not needed because this codec carries none
     */
    public BackwardCompatibleSerializationCodec(ClassLoader classLoader, BackwardCompatibleSerializationCodec codec) {
        this(classLoader);
    }

    private BackwardCompatibleSerializationCodec(Codec mapValueFormat, Codec legacyFormat) {
        this.mapValueFormat = mapValueFormat;
        this.legacyFormat = legacyFormat;
        this.mapValueDecoder = (buf, state) -> startsWithJavaSerializationHeader(buf) ? mapValueFormat.getValueDecoder().decode(buf, state)
                : legacyFormat.getValueDecoder().decode(buf, state);
    }

    /**
     * @param buf a value as it is stored in Redis, positioned at its first byte
     * @return whether it was written by Java serialization rather than by Kryo
     */
    private static boolean startsWithJavaSerializationHeader(ByteBuf buf) {
        return buf.readableBytes() >= Short.BYTES && buf.getShort(buf.readerIndex()) == ObjectStreamConstants.STREAM_MAGIC;
    }

    @Override
    public Decoder<Object> getMapValueDecoder() {
        return mapValueDecoder;
    }

    @Override
    public Encoder getMapValueEncoder() {
        return mapValueFormat.getValueEncoder();
    }

    @Override
    public Decoder<Object> getMapKeyDecoder() {
        return legacyFormat.getValueDecoder();
    }

    @Override
    public Encoder getMapKeyEncoder() {
        return legacyFormat.getValueEncoder();
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return legacyFormat.getValueDecoder();
    }

    @Override
    public Encoder getValueEncoder() {
        return legacyFormat.getValueEncoder();
    }

    @Override
    public ClassLoader getClassLoader() {
        return mapValueFormat.getClassLoader();
    }
}
