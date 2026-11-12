package de.tum.cit.aet.artemis.core.service.distributed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.core.service.distributed.DistributedDataTypeFilter.FilteringClassLoader;

class DistributedDataTypeFilterTest {

    private static final Path RECORDED_SURFACE = Path.of("src", "test", "resources", "config", "distributed-data-surface.txt");

    private static final Pattern QUALIFIED_TYPE = Pattern.compile("\\b[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+\\.[A-Z][A-Za-z0-9_$]*(?:\\.[A-Z][A-Za-z0-9_$]*)*");

    private final DistributedDataTypeFilter filter = DistributedDataTypeFilter.withAdditional(List.of());

    /**
     * Every type named by the recorded surface, as a stored type or as the type of one of its components, has to be
     * readable. A type missing here would be refused on the first read of a stored value.
     */
    @Test
    void testEveryTypeOfTheRecordedSurfaceIsAllowed() throws IOException {
        var names = new TreeSet<String>();
        for (String line : Files.readAllLines(RECORDED_SURFACE, StandardCharsets.UTF_8)) {
            if (line.startsWith("#") || line.startsWith("schema-version")) {
                continue;
            }
            Matcher matcher = QUALIFIED_TYPE.matcher(line);
            while (matcher.find()) {
                names.add(matcher.group());
            }
        }

        assertThat(names).as("the surface file lists types").hasSizeGreaterThan(50);
        assertThat(names).filteredOn(name -> !filter.isAllowed(name)).as("types of the recorded surface that the filter rejects").isEmpty();
    }

    @Test
    void testCollectionAndValueTypesStoredByRecordsAreAllowed() {
        assertThat(List.of("java.lang.String", "java.lang.Long", "java.util.ArrayList", "java.util.HashMap", "java.util.ImmutableCollections$ListN", "java.util.CollSer",
                "java.util.concurrent.ConcurrentHashMap", "java.time.Instant", "java.time.ZonedDateTime", "java.time.Ser", "java.math.BigDecimal", "[B", "[Ljava.lang.String;",
                "[[Ljava.lang.Long;", "de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem")).allMatch(filter::isAllowed);
    }

    @ParameterizedTest
    @ValueSource(strings = { "java.net.URL", "java.net.URI", "java.net.InetSocketAddress", "java.lang.Runtime", "java.io.File", "[Ljava.net.URL;",
            "org.hibernate.collection.spi.PersistentBag", "de.tum.cit.aet.artemisfake.Type", "java.utility.Fake", "[", "[L;", "" })
    void testRejectsTypesArtemisDoesNotStore(String className) {
        assertThat(filter.isAllowed(className)).as(className).isFalse();
    }

    @Test
    void testRejectionIsReportedAsException() {
        assertThatThrownBy(() -> filter.assertAllowed("java.net.URL")).isInstanceOf(SecurityException.class).hasMessageContaining("java.net.URL");
        filter.assertAllowed("java.util.ArrayList");
    }

    @Test
    void testOperatorCanAddPackagesAndClasses() {
        var extended = DistributedDataTypeFilter.withAdditional(List.of(" org.example.extension. ", "java.net.URI", "", " "));

        assertThat(extended.isAllowed("org.example.extension.Value")).isTrue();
        assertThat(extended.isAllowed("org.example.extension.nested.Value")).isTrue();
        assertThat(extended.isAllowed("java.net.URI")).isTrue();
        assertThat(extended.isAllowed("[Ljava.net.URI;")).isTrue();
        assertThat(extended.isAllowed("java.net.URL")).as("an exact entry does not allow its neighbours").isFalse();
        assertThat(extended.isAllowed("org.example.other.Value")).isFalse();
        assertThat(filter.isAllowed("java.net.URI")).as("the default list is not changed by a configured copy").isFalse();
        assertThat(DistributedDataTypeFilter.withAdditional(null).isAllowed("java.util.List")).isTrue();
    }

    @Test
    void testClassLoaderResolvesAllowedAndRefusesOtherTypes() throws ClassNotFoundException {
        var loader = new FilteringClassLoader(getClass().getClassLoader(), filter);

        assertThat(Class.forName("java.util.ArrayList", false, loader)).isEqualTo(ArrayList.class);
        assertThat(Class.forName("de.tum.cit.aet.artemis.core.service.distributed.ClusterNodeInfo", false, loader)).isEqualTo(ClusterNodeInfo.class);
        assertThatThrownBy(() -> Class.forName("java.net.URL", false, loader)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> Class.forName("[Ljava.net.URL;", false, loader)).isInstanceOf(SecurityException.class);
    }
}
