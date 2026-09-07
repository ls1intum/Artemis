package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamConstants;
import java.io.Serial;
import java.io.Serializable;

import org.junit.jupiter.api.Test;
import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.codec.Kryo5Codec;

import io.netty.buffer.ByteBuf;

/**
 * The codec has to keep a Redis-backed deployment readable across the release that changes the wire format, so these
 * tests pin both directions: what an older Artemis wrote with Kryo, and what this one writes itself.
 */
class BackwardCompatibleSerializationCodecTest {

    private final BackwardCompatibleSerializationCodec codec = new BackwardCompatibleSerializationCodec();

    @Test
    void testReadsMapValuesAnOlderNodeWroteWithKryo() throws IOException {
        // A cache entry an older Artemis left in Redis has to survive the upgrade rather than come back as garbage.
        var kryo = new Kryo5Codec();

        assertThat(decodeMapValue(codec, encode(kryo, "cached-by-an-older-node"))).isEqualTo("cached-by-an-older-node");
        assertThat(decodeMapValue(codec, encode(kryo, 42L))).isEqualTo(42L);
    }

    @Test
    void testWritesMapValuesWithJavaSerialization() throws IOException {
        ByteBuf encoded = codec.getMapValueEncoder().encode("written-by-this-node");

        assertThat(encoded.getShort(encoded.readerIndex())).as("map values have to be written with Java serialization").isEqualTo(ObjectStreamConstants.STREAM_MAGIC);
        assertThat(decodeMapValue(codec, encoded)).isEqualTo("written-by-this-node");
    }

    @Test
    void testLeavesEverythingAddressedByItsBytesOnKryo() throws IOException {
        // Redis finds a hash field, a set element and a queue entry by the bytes of its encoding. Re-encoding any of
        // them would stop matching what is already stored: lookups miss, writes duplicate, removes do nothing. This was
        // measured on a real deployment, so it is pinned rather than argued about.
        var kryo = new Kryo5Codec();
        String key = "121775952000135";

        assertThat(bytesOf(codec.getMapKeyEncoder().encode(key))).as("map keys must stay byte-identical to Kryo").isEqualTo(bytesOf(encode(kryo, key)));
        assertThat(bytesOf(codec.getValueEncoder().encode(key))).as("set elements and queue entries must stay byte-identical to Kryo").isEqualTo(bytesOf(encode(kryo, key)));

        assertThat(codec.getMapKeyDecoder().decode(encode(kryo, key), new State())).isEqualTo(key);
        assertThat(codec.getValueDecoder().decode(encode(kryo, key), new State())).isEqualTo(key);
    }

    @Test
    void testRoundTripsAMapValueThatDefinesItsOwnSerialization() throws IOException {
        // The whole reason for leaving Kryo on map values: it walks fields reflectively instead of asking the value to
        // write itself, so a class that rebuilds state in readObject comes back broken.
        var value = new CustomSerializedValue("authorities");

        var throughThisCodec = (CustomSerializedValue) decodeMapValue(codec, codec.getMapValueEncoder().encode(value));
        assertThat(throughThisCodec.rebuiltOnRead()).as("Java serialization has to run readObject").isTrue();
        assertThat(throughThisCodec.name()).isEqualTo("authorities");

        var throughKryo = (CustomSerializedValue) decode(new Kryo5Codec(), encode(new Kryo5Codec(), value));
        assertThat(throughKryo.rebuiltOnRead()).as("Kryo skipping readObject is the divergence this codec exists to avoid").isFalse();
    }

    private static ByteBuf encode(Codec codec, Object value) throws IOException {
        return codec.getValueEncoder().encode(value);
    }

    private static Object decode(Codec codec, ByteBuf buffer) throws IOException {
        return codec.getValueDecoder().decode(buffer, new State());
    }

    private static Object decodeMapValue(Codec codec, ByteBuf buffer) throws IOException {
        return codec.getMapValueDecoder().decode(buffer, new State());
    }

    private static byte[] bytesOf(ByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return bytes;
    }

    /**
     * Stands in for the values that made the codec matter: state that only {@code readObject} restores, the way a
     * Hibernate collection restores itself. Java serialization runs {@code readObject}; Kryo walks the fields instead
     * and leaves the transient one at its default.
     */
    private static final class CustomSerializedValue implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String name;

        private transient boolean rebuiltOnRead;

        CustomSerializedValue(String name) {
            this.name = name;
        }

        boolean rebuiltOnRead() {
            return rebuiltOnRead;
        }

        String name() {
            return name;
        }

        @Serial
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            rebuiltOnRead = true;
        }
    }
}
