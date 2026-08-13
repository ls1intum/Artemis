package de.tum.cit.aet.artemis.globalsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.weaviate.WeaviateContainer;

import de.tum.cit.aet.artemis.shared.WeaviateTestContainerFactory;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.VectorConfig;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * Standalone Weaviate integration test that proves the {@code type}-discriminator tokenization leak and
 * that the fix used by {@code GlobalSearchResource#buildLectureDisjunct} closes it.
 * <p>
 * In the production schema {@code type} is a filterable-only TEXT property, so it uses Weaviate's default
 * {@code word} tokenization: {@code "lecture_unit"} is indexed as the tokens {@code ["lecture", "unit"]}.
 * A {@code type Equal "lecture"} filter therefore also matches {@code lecture_unit} rows, which would drag
 * them into the release-unguarded lecture branch of the global search and leak unreleased lecture units.
 * The single test below reproduces that leak ("without the fix") and then verifies that the
 * {@code type NotEqual "lecture_unit"} guard ("with the fix") removes only the unit row.
 * <p>
 * No Spring context is loaded: it drives a real Weaviate Testcontainer directly. Skipped when Docker is
 * unavailable or the container failed to start.
 */
@EnabledIf("isWeaviateAvailable")
class LectureTypeTokenizationWeaviateIntegrationTest {

    private static final WeaviateContainer weaviate = WeaviateTestContainerFactory.getContainer();

    static boolean isWeaviateAvailable() {
        return weaviate != null && weaviate.isRunning();
    }

    @Test
    void wordTokenizationLeaksLectureUnitIntoLectureFilterAndNotEqualGuardClosesIt() throws Exception {
        String host = weaviate.getHost();
        int httpPort = weaviate.getMappedPort(8080);
        int grpcPort = weaviate.getMappedPort(50051);

        try (WeaviateClient client = WeaviateClient.connectToLocal(config -> config.host(host).port(httpPort).grpcPort(grpcPort))) {
            String collectionName = "TokenizationLeakDemo";
            if (client.collections.exists(collectionName)) {
                client.collections.delete(collectionName);
            }
            // Mirror the production schema: `type` is a filterable-only TEXT property, so it gets
            // Weaviate's default WORD tokenization, exactly like SearchableEntitySchema.filterable(TYPE, TEXT).
            client.collections.create(collectionName, collection -> {
                collection.vectorConfig(VectorConfig.selfProvided());
                collection.properties(Property.text("type", property -> property.indexSearchable(false).indexFilterable(true)));
                return collection;
            });

            var collection = client.collections.use(collectionName);
            Map<String, Object> lectureRow = Map.of("type", "lecture");
            Map<String, Object> lectureUnitRow = Map.of("type", "lecture_unit");
            collection.data.insert(lectureRow);
            collection.data.insert(lectureUnitRow);

            // WITHOUT the fix: the pre-fix buildLectureDisjunct filter is `type == "lecture"`. Under WORD
            // tokenization "lecture_unit" carries the token "lecture", so this ALSO matches the unit row.
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                var leaked = collection.query.fetchObjects(query -> query.filters(Filter.property("type").eq("lecture")).limit(10));
                List<String> leakedTypes = leaked.objects().stream().map(object -> (String) object.properties().get("type")).toList();
                assertThat(leakedTypes).as("type == \"lecture\" leaks the lecture_unit row under WORD tokenization").containsExactlyInAnyOrder("lecture", "lecture_unit");
            });

            // WITH the fix: `type == "lecture" AND type != "lecture_unit"`. Equal "lecture_unit" needs BOTH
            // tokens {lecture, unit}, so it matches only the unit row; negating it drops exactly that row
            // while keeping the genuine lecture (token {lecture}).
            var guarded = collection.query
                    .fetchObjects(query -> query.filters(Filter.and(Filter.property("type").eq("lecture"), Filter.property("type").eq("lecture_unit").not())).limit(10));
            List<String> guardedTypes = guarded.objects().stream().map(object -> (String) object.properties().get("type")).toList();
            assertThat(guardedTypes).as("the NotEqual guard keeps only the genuine lecture row").containsExactly("lecture");

            client.collections.delete(collectionName);
        }
    }
}
