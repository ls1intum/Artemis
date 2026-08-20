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
    void testReadsWhatAnOlderNodeWroteWithKryo() throws IOException {
        // A queued build job that an older Artemis left in Redis has to survive the upgrade, or the build never comes back.
        var kryo = new Kryo5Codec();

        assertThat(decode(codec, encode(kryo, "queued-build-job"))).isEqualTo("queued-build-job");
        assertThat(decode(codec, encode(kryo, 42L))).isEqualTo(42L);
    }

    @Test
    void testWritesJavaSerializationSoTheClusterConvergesOnOneFormat() throws IOException {
        ByteBuf encoded = encode(codec, "written-by-this-node");

        assertThat(encoded.getShort(encoded.readerIndex())).as("values this codec writes have to be Java serialization").isEqualTo(ObjectStreamConstants.STREAM_MAGIC);
        assertThat(decode(codec, encoded)).isEqualTo("written-by-this-node");
    }

    @Test
    void testRoundTripsAValueThatDefinesItsOwnSerialization() throws IOException {
        // The whole reason for leaving Kryo: it walks fields reflectively instead of asking the value to write itself,
        // so a class that rebuilds state in readObject comes back broken.
        var value = new CustomSerializedValue("authorities");

        var throughThisCodec = (CustomSerializedValue) decode(codec, encode(codec, value));
        assertThat(throughThisCodec.rebuiltOnRead()).as("Java serialization has to run readObject").isTrue();

        var throughKryo = (CustomSerializedValue) decode(new Kryo5Codec(), encode(new Kryo5Codec(), value));
        assertThat(throughKryo.rebuiltOnRead()).as("Kryo skipping readObject is the divergence this codec exists to avoid").isFalse();
    }

    private static ByteBuf encode(Codec codec, Object value) throws IOException {
        return codec.getValueEncoder().encode(value);
    }

    private static Object decode(Codec codec, ByteBuf buffer) throws IOException {
        return codec.getValueDecoder().decode(buffer, new State());
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
