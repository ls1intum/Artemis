package de.tum.cit.aet.artemis.globalsearch.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.CourseBrowserDataDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedContentObjectDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.MissingEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionBrowserWeaviateReadService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;

/**
 * Integration tests for {@link IngestionBrowserResource}: the admin-only per-course content browser endpoints. Verifies
 * that non-admins are forbidden and an unknown course is a 404 on both endpoints, that an unknown content key is
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
    void nonAdminIsForbiddenOnBothEndpoints() throws Exception {
        long courseId = course.getId();
        request.get(BASE + "courses/" + courseId + "/browser", HttpStatus.FORBIDDEN, CourseBrowserDataDTO.class);
        request.getList(BASE + "courses/" + courseId + "/units/" + UNIT_ID + "/content?key=slides", HttpStatus.FORBIDDEN, IndexedContentObjectDTO.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void unknownCourseIsNotFoundOnBothEndpoints() throws Exception {
        request.get(BASE + "courses/" + UNKNOWN_COURSE_ID + "/browser", HttpStatus.NOT_FOUND, CourseBrowserDataDTO.class);
        request.getList(BASE + "courses/" + UNKNOWN_COURSE_ID + "/units/" + UNIT_ID + "/content?key=slides", HttpStatus.NOT_FOUND, IndexedContentObjectDTO.class);
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
        insertMetadata(courseId, SearchableEntitySchema.TypeValues.COURSE, courseId, course.getTitle());

        // Weaviate indexes asynchronously, so wait for the row to become readable before asserting through the API. The
        // wait polls the read service rather than the endpoint because Awaitility polls on its own thread, where the mock
        // security context does not apply; the request itself has to run on the test thread or it comes back 401.
        await().atMost(TIMEOUT).until(() -> !browserReadService.listIndexedEntitiesForCourse(courseId).isEmpty());

        CourseBrowserDataDTO data = request.get(BASE + "courses/" + courseId + "/browser", HttpStatus.OK, CourseBrowserDataDTO.class);
        assertThat(data.entities()).extracting(IndexedEntityDTO::type, IndexedEntityDTO::entityId, IndexedEntityDTO::title)
                .contains(tuple(SearchableEntitySchema.TypeValues.COURSE, courseId, course.getTitle()));
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
    void servesContentPresenceAndGapsInTheSameResponse() throws Exception {
        long courseId = course.getId();

        CourseBrowserDataDTO data = request.get(BASE + "courses/" + courseId + "/browser", HttpStatus.OK, CourseBrowserDataDTO.class);
        assertThat(data.contentPresence()).isNotNull();
        assertThat(data.contentGaps()).isNotNull();
    }

    private void insertMetadata(long courseId, String type, long entityId, String title) throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.COURSE_ID, courseId);
        properties.put(SearchableEntitySchema.Properties.TYPE, type);
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, entityId);
        properties.put(SearchableEntitySchema.Properties.TITLE, title);
        weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME).data.insert(properties);
    }
}
