package de.tum.cit.aet.artemis.globalsearch.web;

import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.dropIrisContentCollections;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.insertContent;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.insertMetadata;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.recreateIrisContentCollections;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.CourseBrowserDataDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedContentObjectDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedEntityRecordDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.MissingEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionBrowserService;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionBrowserWeaviateReadService;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageSetLoader.ExpectedSets;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import io.weaviate.client6.v1.api.WeaviateClient;

/**
 * Integration tests for {@link IngestionBrowserResource}: the admin-only per-course content browser endpoints. Verifies
 * that non-admins are forbidden and an unknown course is a 404 on all three endpoints, that an unknown content key is
 * rejected, and that opening a course returns what the browser needs in a single response.
 */
@EnabledIf("isWeaviateEnabled")
class IngestionBrowserResourceIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "ingbrowres";

    private static final String BASE = "/api/global-search/admin/";

    private static final long UNKNOWN_COURSE_ID = 99_999_999L;

    private static final long UNIT_ID = 4242L;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private WeaviateService weaviateService;

    @Autowired
    private IngestionBrowserWeaviateReadService browserReadService;

    @Autowired
    private WeaviateClient weaviateClient;

    private Course course;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        course = courseUtilService.createCourse();
        course.setTitle(TEST_PREFIX + "-course-" + course.getId());
        course = courseRepository.save(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void nonAdminIsForbiddenOnAllThreeEndpoints() throws Exception {
        long courseId = course.getId();
        request.get(BASE + "courses/" + courseId + "/browser", HttpStatus.FORBIDDEN, CourseBrowserDataDTO.class);
        request.getList(BASE + "courses/" + courseId + "/entities?type=lecture", HttpStatus.FORBIDDEN, IndexedEntityRecordDTO.class);
        request.getList(BASE + "courses/" + courseId + "/units/" + UNIT_ID + "/content?key=slides", HttpStatus.FORBIDDEN, IndexedContentObjectDTO.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void unknownCourseIsNotFoundOnAllThreeEndpoints() throws Exception {
        request.get(BASE + "courses/" + UNKNOWN_COURSE_ID + "/browser", HttpStatus.NOT_FOUND, CourseBrowserDataDTO.class);
        request.getList(BASE + "courses/" + UNKNOWN_COURSE_ID + "/entities?type=lecture", HttpStatus.NOT_FOUND, IndexedEntityRecordDTO.class);
        request.getList(BASE + "courses/" + UNKNOWN_COURSE_ID + "/units/" + UNIT_ID + "/content?key=slides", HttpStatus.NOT_FOUND, IndexedContentObjectDTO.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void unknownEntityTypeIsRejected() throws Exception {
        request.getList(BASE + "courses/" + course.getId() + "/entities?type=not-a-type", HttpStatus.BAD_REQUEST, IndexedEntityRecordDTO.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void returnsTheStoredRecordsOfOneType() throws Exception {
        long courseId = course.getId();
        insertMetadata(weaviateService, courseId, SearchableEntitySchema.TypeValues.COURSE, courseId, course.getTitle());

        await().atMost(TIMEOUT).until(() -> !browserReadService.listIndexedEntityRecords(courseId, SearchableEntitySchema.TypeValues.COURSE).isEmpty());

        List<IndexedEntityRecordDTO> records = request.getList(BASE + "courses/" + courseId + "/entities?type=course", HttpStatus.OK, IndexedEntityRecordDTO.class);
        assertThat(records).hasSize(1);
        // The record carries the stored property map, which the tree payload deliberately does not.
        assertThat(records.getFirst().properties()).containsEntry(SearchableEntitySchema.Properties.TITLE, course.getTitle());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void unknownContentKeyIsRejected() throws Exception {
        request.getList(BASE + "courses/" + course.getId() + "/units/" + UNIT_ID + "/content?key=not-a-key", HttpStatus.BAD_REQUEST, IndexedContentObjectDTO.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void returnsTheBrowserPayloadForACourse() throws Exception {
        long courseId = course.getId();
        insertMetadata(weaviateService, courseId, SearchableEntitySchema.TypeValues.COURSE, courseId, course.getTitle());

        // Weaviate indexes asynchronously, so wait for the row to become readable before asserting through the API. The
        // wait polls the read service rather than the endpoint because Awaitility polls on its own thread, where the mock
        // security context does not apply; the request itself has to run on the test thread or it comes back 401.
        await().atMost(TIMEOUT).until(() -> !browserReadService.listIndexedEntitiesForCourse(courseId).isEmpty());

        CourseBrowserDataDTO data = request.get(BASE + "courses/" + courseId + "/browser", HttpStatus.OK, CourseBrowserDataDTO.class);
        assertThat(data.entities()).extracting(IndexedEntityDTO::type, IndexedEntityDTO::entityId, IndexedEntityDTO::title)
                .contains(tuple(SearchableEntitySchema.TypeValues.COURSE, courseId, course.getTitle()));
        assertThat(data.entities()).filteredOn(entity -> entity.type().equals(SearchableEntitySchema.TypeValues.COURSE) && entity.entityId() == courseId)
                .allSatisfy(entity -> assertThat(entity.expected()).isTrue());
    }

    @Test
    void mapsEveryExpectedMetadataTypeIndependentlyOfIrisContentEligibility() {
        long courseId = course.getId();
        ExpectedSets expected = new ExpectedSets(Map.of(), Map.of(), Map.of(), Map.of(courseId, Set.of(10L)), Map.of(courseId, Set.of(11L)), Map.of(courseId, Set.of(12L)),
                Map.of(), Map.of(), Set.of(courseId));

        assertThat(isExpected(courseId, indexedEntity(SearchableEntitySchema.TypeValues.EXAM, 10L), expected)).isTrue();
        assertThat(isExpected(courseId, indexedEntity(SearchableEntitySchema.TypeValues.FAQ, 11L), expected)).isTrue();
        assertThat(isExpected(courseId, indexedEntity(SearchableEntitySchema.TypeValues.CHANNEL, 12L), expected)).isTrue();
        assertThat(isExpected(courseId, indexedEntity(SearchableEntitySchema.TypeValues.COURSE, courseId), expected)).isTrue();
        assertThat(isExpected(courseId, indexedEntity(SearchableEntitySchema.TypeValues.FAQ, 99L), expected)).isFalse();
        assertThat(isExpected(courseId, indexedEntity(SearchableEntitySchema.TypeValues.POST, 99L), expected)).isFalse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void namesTheCourseItselfWhenNothingIsIndexed() throws Exception {
        long courseId = course.getId();

        // Nothing was indexed for this course, so there is nothing to wait for: the course row itself is expected and absent.
        CourseBrowserDataDTO data = request.get(BASE + "courses/" + courseId + "/browser", HttpStatus.OK, CourseBrowserDataDTO.class);
        assertThat(data.missingEntities()).extracting(MissingEntityDTO::type, MissingEntityDTO::entityId).contains(tuple(SearchableEntitySchema.TypeValues.COURSE, courseId));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void omitsEmptyContentPresenceAndGapsFromTheCombinedResponse() throws Exception {
        long courseId = course.getId();

        var json = objectMapper.readTree(request.get(BASE + "courses/" + courseId + "/browser", HttpStatus.OK, String.class));
        assertThat(json.isObject()).isTrue();
        assertThat(json.has("contentPresence")).isFalse();
        assertThat(json.has("contentGaps")).isFalse();
        assertThat(json.get("missingEntities").isArray()).isTrue();
        assertThat(json.get("typeCounts").isArray()).isTrue();
    }

    @Test
    @WithAnonymousUser
    void anonymousIsRejectedOnAllThreeEndpoints() throws Exception {
        String path = BASE + "courses/" + course.getId();
        request.get(path + "/browser", HttpStatus.UNAUTHORIZED, String.class);
        request.get(path + "/entities?type=lecture", HttpStatus.UNAUTHORIZED, String.class);
        request.get(path + "/units/" + UNIT_ID + "/content?key=slides", HttpStatus.UNAUTHORIZED, String.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void requiredQueriesAreBoundAndEmptyValuesRemainInvalid() throws Exception {
        String path = BASE + "courses/" + course.getId();
        request.get(path + "/entities", HttpStatus.BAD_REQUEST, String.class);
        request.get(path + "/entities?type=", HttpStatus.BAD_REQUEST, String.class);
        request.get(path + "/units/" + UNIT_ID + "/content", HttpStatus.BAD_REQUEST, String.class);
        request.get(path + "/units/" + UNIT_ID + "/content?key=", HttpStatus.BAD_REQUEST, String.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void rawBrowserAndRecordsKeepFalseIdentityAndArrayBodies() throws Exception {
        long courseId = course.getId();
        insertMetadata(weaviateService, courseId, SearchableEntitySchema.TypeValues.LECTURE, UNKNOWN_COURSE_ID, null);
        await().atMost(TIMEOUT).until(() -> !browserReadService.listIndexedEntitiesForCourse(courseId).isEmpty());
        var browser = objectMapper.readTree(request.get(BASE + "courses/" + courseId + "/browser", HttpStatus.OK, String.class));
        assertThat(browser.isObject()).isTrue();
        assertThat(browser.get("entities")).anySatisfy(entity -> {
            assertThat(entity.get("entityId").longValue()).isEqualTo(UNKNOWN_COURSE_ID);
            assertThat(entity.get("type").stringValue()).isEqualTo("lecture");
            assertThat(entity.get("expected").isBoolean()).isTrue();
            assertThat(entity.get("expected").booleanValue()).isFalse();
            assertThat(entity.has("title")).isFalse();
            assertThat(entity.has("lectureId")).isFalse();
        });
        var records = objectMapper.readTree(request.get(BASE + "courses/" + courseId + "/entities?type=lecture", HttpStatus.OK, String.class));
        assertThat(records.isArray()).isTrue();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("properties").isObject()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void contentIsAnArrayScopedToBothCourseAndUnitAndAbsentCollectionIsEmpty() throws Exception {
        recreateIrisContentCollections(weaviateClient);
        try {
            String collection = IngestionCoverageWeaviateReadService.LECTURES_COLLECTION;
            insertContent(weaviateService, collection, course.getId(), UNIT_ID, 0);
            insertContent(weaviateService, collection, course.getId(), UNIT_ID + 1, 1);
            insertContent(weaviateService, collection, UNKNOWN_COURSE_ID, UNIT_ID, 2);
            await().atMost(TIMEOUT)
                    .until(() -> browserReadService.listContentObjectsForUnit(course.getId(), UNIT_ID, "slides").size() == 1
                            && browserReadService.listContentObjectsForUnit(course.getId(), UNIT_ID + 1, "slides").size() == 1
                            && browserReadService.listContentObjectsForUnit(UNKNOWN_COURSE_ID, UNIT_ID, "slides").size() == 1);
            String path = BASE + "courses/" + course.getId() + "/units/";
            var content = objectMapper.readTree(request.get(path + UNIT_ID + "/content?key=slides", HttpStatus.OK, String.class));
            assertThat(content.isArray()).isTrue();
            assertThat(content).hasSize(1);
            assertThat(content.get(0).get("properties").get("page_number").intValue()).isZero();
            assertThat(objectMapper.readTree(request.get(path + UNKNOWN_COURSE_ID + "/content?key=slides", HttpStatus.OK, String.class))).isEmpty();
            dropIrisContentCollections(weaviateClient);
            var absent = objectMapper.readTree(request.get(path + UNIT_ID + "/content?key=slides", HttpStatus.OK, String.class));
            assertThat(absent.isArray()).isTrue();
            assertThat(absent).isEmpty();
        }
        finally {
            dropIrisContentCollections(weaviateClient);
        }
    }

    private static IndexedEntityDTO indexedEntity(String type, long entityId) {
        return new IndexedEntityDTO(type, entityId, null, null, null, false);
    }

    private static boolean isExpected(long courseId, IndexedEntityDTO entity, ExpectedSets expected) {
        return ReflectionTestUtils.invokeMethod(IngestionBrowserService.class, "isExpectedMetadataEntity", courseId, entity, expected);
    }

}
