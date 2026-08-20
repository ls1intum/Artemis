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
 * Writes Redis values with Java serialization and reads both Java serialization and Kryo.
 *
 * <p>
 * Artemis switched the Redis codec from Redisson's default Kryo to Java serialization, because Hazelcast serializes
 * that way and the two disagree on values whose class defines its own {@code writeObject} - see
 * {@link de.tum.cit.aet.artemis.core.config.RedissonCodecConfiguration} for the case that forced it.
 *
 * <p>
 * Changing the codec outright would make everything an older Artemis wrote unreadable: the build job queue, the jobs
 * currently being processed, and scheduling messages all outlive a node. Reading both formats keeps an upgrade
 * non-destructive - a new node still understands what an old one left behind - while everything it writes itself is in
 * the format the whole cluster is moving to.
 *
 * <p>
 * The two formats are told apart by the value rather than by configuration: a Java serialization stream always starts
 * with {@link ObjectStreamConstants#STREAM_MAGIC}, which is not a legal start of a Kryo stream. Nothing has to be
 * remembered across restarts, and an entry rewritten by any node is simply in the new format from then on.
 *
 * <p>
 * Note for operators: the reverse direction is not possible. An <em>older</em> node cannot read what a node running
 * this codec writes. Roll a Redis-backed deployment forward, not back, and expect a mixed-version window in which the
 * old nodes ignore the new nodes' entries.
 */
public class BackwardCompatibleSerializationCodec extends BaseCodec {

    private final Codec writeFormat;

    private final Codec legacyFormat;

    private final Decoder<Object> decoder;

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

    private BackwardCompatibleSerializationCodec(Codec writeFormat, Codec legacyFormat) {
        this.writeFormat = writeFormat;
        this.legacyFormat = legacyFormat;
        this.decoder = (buf, state) -> startsWithJavaSerializationHeader(buf) ? writeFormat.getValueDecoder().decode(buf, state)
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
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

    @Override
    public Encoder getValueEncoder() {
        return writeFormat.getValueEncoder();
    }

    @Override
    public ClassLoader getClassLoader() {
        return writeFormat.getClassLoader();
    }
}
