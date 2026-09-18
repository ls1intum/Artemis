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

    // TEMPORARY (revert before merge): the resource is relaxed to instructor and served outside the /admin/ segment.
    private static final String BASE = "/api/global-search/ingestion-dashboard/";

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
        ingestionCoverageRepository.save(storedEntry(IngestionCoverageStatus.INCOMPLETE));

        // The endpoint is paginated and defaults to 20 rows. The projection table is shared across test classes and
        // accumulates a row per course, so without an explicit size this course's row can sit on a later page and the
        // assertion below would fail purely on how many courses other tests happened to create.
        List<IngestionCoverageDTO> coverage = request.getList(BASE + "coverage?size=2000", HttpStatus.OK, IngestionCoverageDTO.class);

        assertThat(coverage).anySatisfy(dto -> {
            assertThat(dto.courseId()).isEqualTo(course.getId());
            assertThat(dto.status()).isNotNull();
            assertThat(dto.typeCounts()).isNotEmpty();
        });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void storedCoverageAppliesTheTitleSearchAlongsideTheStatusFilter() throws Exception {
        ingestionCoverageRepository.save(storedEntry(IngestionCoverageStatus.INCOMPLETE));

        // Filter and search together. The stored path took no search parameter at all, so setting any filter silently
        // widened the result back to every course while the search box still showed the typed term.
        List<IngestionCoverageDTO> filtered = request.getList(BASE + "coverage?size=2000&status=INCOMPLETE&search=" + course.getTitle(), HttpStatus.OK, IngestionCoverageDTO.class);

        assertThat(filtered).isNotEmpty().allSatisfy(dto -> {
            assertThat(dto.courseTitle()).containsIgnoringCase(course.getTitle());
            assertThat(dto.status()).isEqualTo(IngestionCoverageStatus.INCOMPLETE);
        });
        assertThat(filtered).extracting(IngestionCoverageDTO::courseId).contains(course.getId());

        // A term no course carries returns nothing rather than falling back to the unsearched page.
        assertThat(request.getList(BASE + "coverage?size=2000&status=INCOMPLETE&search=nosuchcoursetitle", HttpStatus.OK, IngestionCoverageDTO.class)).isEmpty();
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
    void liveCoveragePageTrimsTheSearchTermLikeTheStoredView() throws Exception {
        // Incidental leading/trailing whitespace (e.g. pasted from elsewhere) must match here exactly as it already does
        // on the stored-coverage endpoint, not silently return nothing because the padded term was searched verbatim.
        // %20 rather than URLEncoder: the raw path is parsed as a java.net.URI, which expects RFC 3986 percent-encoding,
        // not the '+'-for-space form-encoding URLEncoder produces (the same convention ExerciseWeaviateResourceIntegrationTest
        // already uses in this module).
        List<IngestionCoverageDTO> page = request.getList(BASE + "coverage/page?search=%20%20" + course.getTitle() + "%20%20", HttpStatus.OK, IngestionCoverageDTO.class);

        assertThat(page).extracting(IngestionCoverageDTO::courseId).contains(course.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void liveCoveragePageStillServesTheLargestPageSizeThePaginatorOffers() throws Exception {
        // The dashboard's paginator offers 10/20/50/100/200 rows and the default (unfiltered, sort-by-name) view reads
        // this endpoint, so the cap must sit at or above 200 or picking "200 rows per page" would fail the whole table.
        request.getList(BASE + "coverage/page?size=200", HttpStatus.OK, IngestionCoverageDTO.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void liveCoveragePageRejectsAPageSizeAboveTheLiveCap() throws Exception {
        // Each course with lecture units on this page costs four external Weaviate content aggregations, unlike the
        // stored-coverage endpoint above which reads one local table and is allowed the shared resolver's full range.
        request.getList(BASE + "coverage/page?size=201", HttpStatus.BAD_REQUEST, IngestionCoverageDTO.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void refreshIsAccepted() throws Exception {
        request.postWithoutResponseBody(BASE + "coverage/refresh", null, HttpStatus.OK);
    }

    /**
     * A projection row for the test course, computed now. The fresh timestamp keeps the endpoint's stale-while-revalidate
     * trigger a no-op, so the stored-coverage tests read back exactly what they wrote.
     */
    private IngestionCoverageEntry storedEntry(IngestionCoverageStatus status) {
        IngestionCoverageEntry entry = new IngestionCoverageEntry();
        entry.setCourseId(course.getId());
        entry.setCourseTitle(course.getTitle());
        entry.setTypeCounts(List.of(new IngestionTypeCountDTO("exercise", 5, 4, 1, 0)));
        entry.setCoverageGapScore(1);
        entry.setStatus(status);
        entry.setActive(true);
        entry.setComputedAt(ZonedDateTime.now());
        return entry;
    }
}
