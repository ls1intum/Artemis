package de.tum.cit.aet.artemis.globalsearch.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageStatus;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexOverviewDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionCoverageDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionTypeCountDTO;
import de.tum.cit.aet.artemis.globalsearch.repository.IngestionCoverageRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;

/**
 * Integration tests for {@link IngestionCoverageResource}: the admin-only ingestion-coverage endpoints. Verifies that
 * non-admins are forbidden, the overview reports reachability and per-collection counts, the stored-coverage endpoint
 * reads and maps the projection, the live-per-page endpoint computes coverage for a page of courses, and the refresh
 * endpoint is accepted.
 */
@EnabledIf("isWeaviateEnabled")
class IngestionCoverageResourceIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "ingcovres";

    private static final String BASE = "/api/global-search/admin/";

    @Autowired
    private IngestionCoverageRepository ingestionCoverageRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private CourseRepository courseRepository;

    private Course course;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        ingestionCoverageRepository.deleteAll();
        course = courseUtilService.createCourse();
        course.setTitle(TEST_PREFIX + "-course-" + course.getId());
        course = courseRepository.save(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void nonAdminIsForbiddenOnEveryEndpoint() throws Exception {
        request.get(BASE + "index/overview", HttpStatus.FORBIDDEN, IndexOverviewDTO.class);
        request.getList(BASE + "coverage", HttpStatus.FORBIDDEN, IngestionCoverageDTO.class);
        request.getList(BASE + "coverage/page", HttpStatus.FORBIDDEN, IngestionCoverageDTO.class);
        request.postWithoutResponseBody(BASE + "coverage/refresh", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void overviewReportsReachabilityAndCollectionCounts() throws Exception {
        IndexOverviewDTO overview = request.get(BASE + "index/overview", HttpStatus.OK, IndexOverviewDTO.class);

        assertThat(overview).isNotNull();
        assertThat(overview.weaviateAddress()).isNotBlank();
        // The container is running, so the SearchableEntities collection is present and counted.
        assertThat(overview.collections()).anySatisfy(collection -> assertThat(collection.collection()).isEqualTo("SearchableEntities"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void storedCoverageReadsAndMapsTheProjection() throws Exception {
        // A fresh row means the endpoint's stale-while-revalidate trigger is a no-op, so this reads exactly what is stored.
        IngestionCoverageEntry entry = new IngestionCoverageEntry();
        entry.setCourseId(course.getId());
        entry.setCourseTitle(course.getTitle());
        entry.setTypeCounts(List.of(new IngestionTypeCountDTO("exercise", 5, 4, 1, 0)));
        entry.setCoverageGapScore(1);
        entry.setStatus(IngestionCoverageStatus.INCOMPLETE);
        entry.setActive(true);
        entry.setComputedAt(ZonedDateTime.now());
        ingestionCoverageRepository.save(entry);

        List<IngestionCoverageDTO> coverage = request.getList(BASE + "coverage", HttpStatus.OK, IngestionCoverageDTO.class);

        assertThat(coverage).anySatisfy(dto -> {
            assertThat(dto.courseId()).isEqualTo(course.getId());
            assertThat(dto.status()).isNotNull();
            assertThat(dto.typeCounts()).isNotEmpty();
        });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void liveCoveragePageComputesCoverageForTheVisibleCourses() throws Exception {
        List<IngestionCoverageDTO> page = request.getList(BASE + "coverage/page?search=" + course.getTitle(), HttpStatus.OK, IngestionCoverageDTO.class);

        assertThat(page).anySatisfy(dto -> {
            assertThat(dto.courseId()).isEqualTo(course.getId());
            // Live coverage always includes the per-type breakdown, even when nothing is indexed yet.
            assertThat(dto.typeCounts()).isNotEmpty();
        });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void refreshIsAccepted() throws Exception {
        request.postWithoutResponseBody(BASE + "coverage/refresh", null, HttpStatus.OK);
    }
}
