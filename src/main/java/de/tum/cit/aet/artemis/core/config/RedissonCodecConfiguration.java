package de.tum.cit.aet.artemis.core.config;

import java.util.List;

import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import de.tum.cit.aet.artemis.core.service.distributed.DistributedDataTypeFilter;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.BackwardCompatibleSerializationCodec;

/**
 * Configures Redis map values to use Java serialization, which honors custom {@code writeObject} methods.
 * This allows cached entities to retain lazy Hibernate associations without initializing them outside a session.
 *
 * <p>
 * Only <em>map values</em> change format, and old ones stay readable. {@link BackwardCompatibleSerializationCodec}
 * tells the two apart by the value itself, so nothing has to be configured or remembered, and each entry moves to the
 * new format the first time any node rewrites it. Keys, set elements, queue entries and topic messages deliberately
 * stay on Kryo: Redis addresses those by the bytes of their encoding, so re-encoding them stops matching what is
 * already stored - measured on a deployment upgraded with an earlier version of this codec, which left five
 * undeletable jobs in {@code processingJobs} and every feature toggle stored twice.
 *
 * <p>
 * Note for operators: an older node cannot read a map value this codec has rewritten, so roll a Redis-backed
 * deployment forward rather than back. Everything else is unaffected in both directions.
 */
@Lazy
@Configuration
@Conditional(RedisCondition.class)
public class RedissonCodecConfiguration {

    @Value("${artemis.distributed-data.additional-allowed-types:}")
    private List<String> additionalAllowedTypes;

    /**
     * @return a customizer that makes the Redisson client write map values with JDK serialization while reading both
     *         formats, and leave every other encoding on Kryo. Only the types Artemis stores are created when reading.
     */
    @Bean
    public RedissonAutoConfigurationCustomizer artemisRedissonSerializationCodecCustomizer() {
        var typeFilter = DistributedDataTypeFilter.withAdditional(additionalAllowedTypes);
        return config -> config.setCodec(new BackwardCompatibleSerializationCodec(typeFilter));
    }
}
