package de.tum.cit.aet.artemis.core.config;

import org.redisson.codec.SerializationCodec;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Makes Redis serialize values the same way Hazelcast does.
 *
 * <p>
 * Every value Artemis puts into the distributed store already has to be {@link java.io.Serializable}, because Hazelcast
 * is the default backend and serializes that way. Redisson defaults to Kryo, which does not read the object's own
 * {@code writeObject}: it walks the fields reflectively instead. For most values the two agree, which is exactly what
 * makes the difference dangerous — it only shows up on the values where they do not.
 *
 * <p>
 * The concrete case that forced this: the {@code savedPosts} cache holds {@code List<SavedPost>}, and a {@code SavedPost}
 * references a {@code User} whose {@code authorities} is a lazy Hibernate collection. Java serialization asks the
 * collection to write itself and it records that it is uninitialized; Kryo reads its size instead and Hibernate throws
 * {@code LazyInitializationException}, because {@code spring.jpa.open-in-view} is false and the session is long gone.
 * Every read of a user's saved posts answered 500 on Redis while working on Hazelcast.
 *
 * <p>
 * Aligning the codec is what makes "it works on Hazelcast" a reliable statement about Redis too, which is the property
 * the whole {@link de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider} abstraction rests on. A
 * shared contract test cannot catch this class of divergence on its own, because it necessarily uses simple values.
 *
 * <p>
 * The cost is that JDK serialization is slower and more verbose than Kryo. It falls only on deployments that select
 * Redis, and the Redis structures are dominated by round-trip and lock cost rather than by encoding (see the priority
 * queue benchmark). Correctness across backends is worth more here than the encoding difference.
 *
 * <p>
 * Note for operators: switching the codec makes values written by an earlier codec unreadable. All of this state is
 * ephemeral cluster state that a node rewrites after a restart, so no migration is needed, but do not expect a rolling
 * restart to keep reading the old entries.
 */
@Lazy
@Configuration
@Conditional(RedisCondition.class)
public class RedissonCodecConfiguration {

    /**
     * @return a customizer that switches the Redisson client to JDK serialization
     */
    @Bean
    public RedissonAutoConfigurationCustomizer artemisRedissonSerializationCodecCustomizer() {
        return config -> config.setCodec(new SerializationCodec());
    }
}
