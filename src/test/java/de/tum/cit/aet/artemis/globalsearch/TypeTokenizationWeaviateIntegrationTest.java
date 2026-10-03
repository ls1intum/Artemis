package de.tum.cit.aet.artemis.globalsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.weaviate.WeaviateContainer;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityAccessFilterService;
import de.tum.cit.aet.artemis.shared.WeaviateTestContainerFactory;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.CollectionHandle;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.VectorConfig;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * Standalone Weaviate integration test that proves the {@code type}-discriminator tokenization leak and
 * that the {@code NotEqual} guard used across {@code GlobalSearchResource} closes it.
 * <p>
 * In the production schema {@code type} is a filterable-only TEXT property, so it uses Weaviate's default
 * {@code word} tokenization: a value is indexed as its underscore-separated tokens. Any type value that is
 * a token subset of another therefore leaks into the narrower one's filter. Two pairs collide today:
 * <ul>
 * <li>{@code "lecture"} vs {@code "lecture_unit"} - the unit rows would be dragged into the
 * release-unguarded lecture branch and leak unreleased lecture units. An access problem.</li>
 * <li>{@code "post"} vs {@code "answer_post"} - replies would be returned to a caller asking for posts.
 * Both are gated by the same course membership, so this one is a correctness problem.</li>
 * </ul>
 * Each test reproduces the leak ("without the fix") and then verifies the {@code type NotEqual <narrower>}
 * guard ("with the fix") removes only the narrower rows, because the narrower value needs ALL its tokens.
 * <p>
 * No Spring context is loaded: it drives a real Weaviate Testcontainer directly. Skipped when Docker is
 * unavailable or the container failed to start.
 */
@EnabledIf("isWeaviateAvailable")
class TypeTokenizationWeaviateIntegrationTest {

    private static final WeaviateContainer weaviate = WeaviateTestContainerFactory.getContainer();

    static boolean isWeaviateAvailable() {
        return weaviate != null && weaviate.isRunning();
    }

    @Test
    void wordTokenizationLeaksLectureUnitIntoLectureFilterAndNotEqualGuardClosesIt() throws Exception {
        assertLeakAndGuard("lecture", "lecture_unit", "TokenizationLeakDemoLecture");
    }

    @Test
    void wordTokenizationLeaksAnswerPostIntoPostFilterAndNotEqualGuardClosesIt() throws Exception {
        assertLeakAndGuard("post", "answer_post", "TokenizationLeakDemoPost");
    }

    /**
     * The guard has to reach every path that filters by type, not only the per-role disjuncts: an admin without a
     * course scope gets a bare type filter, with or without excluded courses, and asking for lectures there must not
     * return lecture units.
     */
    @Test
    void adminTypeFiltersReturnOnlyTheRequestedLectureType() throws Exception {
        AuthorizationCheckService authCheckService = mock(AuthorizationCheckService.class);
        CourseRepository courseRepository = mock(CourseRepository.class);
        when(authCheckService.isCurrentUserAdminAccessEnabled()).thenReturn(true);
        Course firstCourse = new Course();
        firstCourse.setId(1L);
        Course secondCourse = new Course();
        secondCourse.setId(2L);
        when(courseRepository.findAll()).thenReturn(List.of(firstCourse, secondCourse));
        var filterService = new SearchableEntityAccessFilterService(authCheckService, courseRepository, Optional.empty());
        User admin = new User();
        admin.setId(1L);
        Set<String> lectureOnly = Set.of(SearchableEntitySchema.TypeValues.LECTURE);

        Filter unscoped = filterService.buildSearchableItemFilter(admin, null, List.of(), lectureOnly, Set.of(), false).filter();
        Filter withExclusion = filterService.buildSearchableItemFilter(admin, null, List.of(2L), lectureOnly, Set.of(), false).filter();

        String collectionName = "TokenizationAdminTypeFilter";
        String host = weaviate.getHost();
        try (WeaviateClient client = WeaviateClient.connectToLocal(config -> config.host(host).port(weaviate.getMappedPort(8080)).grpcPort(weaviate.getMappedPort(50051)))) {
            if (client.collections.exists(collectionName)) {
                client.collections.delete(collectionName);
            }
            client.collections.create(collectionName, collection -> {
                collection.vectorConfig(VectorConfig.selfProvided());
                collection.properties(Property.text(SearchableEntitySchema.Properties.TYPE, property -> property.indexSearchable(false).indexFilterable(true)),
                        Property.integer(SearchableEntitySchema.Properties.COURSE_ID, property -> property.indexFilterable(true)));
                return collection;
            });
            var collection = client.collections.use(collectionName);
            collection.data.insert(Map.of(SearchableEntitySchema.Properties.TYPE, "lecture", SearchableEntitySchema.Properties.COURSE_ID, 1L));
            collection.data.insert(Map.of(SearchableEntitySchema.Properties.TYPE, "lecture_unit", SearchableEntitySchema.Properties.COURSE_ID, 1L));
            collection.data.insert(Map.of(SearchableEntitySchema.Properties.TYPE, "lecture", SearchableEntitySchema.Properties.COURSE_ID, 2L));

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(rows(collection, unscoped)).containsExactlyInAnyOrder("lecture@1", "lecture@2"));
            assertThat(rows(collection, withExclusion)).containsExactly("lecture@1");

            client.collections.delete(collectionName);
        }
    }

    private static List<String> rows(CollectionHandle<Map<String, Object>> collection, Filter filter) {
        return collection.query.fetchObjects(query -> query.filters(filter).limit(10)).objects().stream()
                .map(object -> object.properties().get(SearchableEntitySchema.Properties.TYPE) + "@"
                        + ((Number) object.properties().get(SearchableEntitySchema.Properties.COURSE_ID)).longValue())
                .toList();
    }

    /**
     * Inserts one row of each type into a throwaway collection that mirrors the production {@code type}
     * property, asserts the bare {@code Equal} filter leaks the narrower row, then asserts the guarded
     * filter keeps only the broader one.
     *
     * @param broadType      the type whose token set is a subset of the other (e.g. {@code "lecture"})
     * @param narrowType     the type carrying the extra token (e.g. {@code "lecture_unit"})
     * @param collectionName a collection name unique to this case so parallel tests cannot collide
     */
    private void assertLeakAndGuard(String broadType, String narrowType, String collectionName) throws Exception {
        String host = weaviate.getHost();
        int httpPort = weaviate.getMappedPort(8080);
        int grpcPort = weaviate.getMappedPort(50051);

        try (WeaviateClient client = WeaviateClient.connectToLocal(config -> config.host(host).port(httpPort).grpcPort(grpcPort))) {
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
            collection.data.insert(Map.of("type", broadType));
            collection.data.insert(Map.of("type", narrowType));

            // WITHOUT the fix: a bare `type == broadType` filter. Under WORD tokenization the narrower value
            // carries every token of the broader one, so this ALSO matches the narrower row.
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                var leaked = collection.query.fetchObjects(query -> query.filters(Filter.property("type").eq(broadType)).limit(10));
                List<String> leakedTypes = leaked.objects().stream().map(object -> (String) object.properties().get("type")).toList();
                assertThat(leakedTypes).as("type == \"%s\" leaks the %s row under WORD tokenization", broadType, narrowType).containsExactlyInAnyOrder(broadType, narrowType);
            });

            // WITH the fix: `type == broadType AND type != narrowType`. Equal on the narrower value needs ALL
            // of its tokens, so it matches only the narrower row; negating it drops exactly that row while
            // keeping the genuine broader row.
            var guarded = collection.query
                    .fetchObjects(query -> query.filters(Filter.and(Filter.property("type").eq(broadType), Filter.property("type").eq(narrowType).not())).limit(10));
            List<String> guardedTypes = guarded.objects().stream().map(object -> (String) object.properties().get("type")).toList();
            assertThat(guardedTypes).as("the NotEqual guard keeps only the genuine %s row", broadType).containsExactly(broadType);

            client.collections.delete(collectionName);
        }
    }
}
