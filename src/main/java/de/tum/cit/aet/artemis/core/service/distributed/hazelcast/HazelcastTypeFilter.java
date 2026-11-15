package de.tum.cit.aet.artemis.core.service.distributed.hazelcast;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.hazelcast.config.ClassFilter;
import com.hazelcast.config.JavaSerializationFilterConfig;
import com.hazelcast.config.SerializationConfig;

import de.tum.cit.aet.artemis.core.service.distributed.DistributedDataTypeFilter;

/**
 * Applies the {@link DistributedDataTypeFilter} to the Java serialization of a Hazelcast member or client.
 *
 * <p>
 * Hazelcast's own default list allows every class below {@code java} and {@code javax}. That is switched off here, so
 * that only the types of the filter, Hazelcast's own classes and the Bucket4j classes that rate limiting stores can be
 * read. Hazelcast rejects any other class with a {@link SecurityException} that names the class.
 */
public final class HazelcastTypeFilter {

    /**
     * Classes below these prefixes are serialized by Hazelcast itself or by the rate limiting library, not by Artemis.
     * The {@code [} prefix is Hazelcast's notation for arrays, whose elements are checked one by one when they are read.
     */
    private static final List<String> PROVIDER_PREFIXES = List.of("com.hazelcast.", "io.github.bucket4j.", "[");

    private HazelcastTypeFilter() {
    }

    /**
     * @param filter the types Artemis stores, including the operator configured ones
     * @return the Hazelcast filter configuration that allows exactly those types plus the ones Hazelcast needs itself
     */
    public static JavaSerializationFilterConfig toHazelcastConfig(DistributedDataTypeFilter filter) {
        var whitelist = new ClassFilter();
        whitelist.setClasses(Set.copyOf(filter.allowedClasses()));
        whitelist.setPrefixes(Set.copyOf(Stream.concat(filter.allowedPrefixes().stream(), PROVIDER_PREFIXES.stream()).toList()));
        return new JavaSerializationFilterConfig().setDefaultsDisabled(true).setWhitelist(whitelist);
    }

    /**
     * @param serializationConfig the serialization configuration of a Hazelcast member or client
     * @param filter              the types Artemis stores, including the operator configured ones
     */
    public static void apply(SerializationConfig serializationConfig, DistributedDataTypeFilter filter) {
        serializationConfig.setJavaSerializationFilterConfig(toHazelcastConfig(filter));
    }
}
