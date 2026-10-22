package de.tum.cit.aet.artemis.globalsearch.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
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
 * endpoint returns only after recomputation finishes.
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

    @Autowired
    private DistributedDataProvider distributedDataProvider;

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
        // Certify the projection as freshly recomputed, so a stored-coverage read returns the rows a test seeds instead
        // of recomputing them from the database first.
        successfulFullRecomputes().put("projection", Boolean.TRUE);
    }

    @AfterEach
    void clearRecomputeMarkers() {
        // The markers live in the shared distributed store, so leaving one behind would change what other classes read.
        successfulFullRecomputes().clear();
        distributedDataProvider.<String, Boolean>getExpiringMap("ingestion-coverage-failed-recompute", Duration.ofSeconds(30)).clear();
    }

    private DistributedMap<String, Boolean> successfulFullRecomputes() {
        return distributedDataProvider.getExpiringMap("ingestion-coverage-successful-full-recompute", Duration.ofMinutes(15));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void nonAdminIsForbiddenOnEveryEndpoint() throws Exception {
        var stored = ingestionCoverageRepository.save(storedEntry(IngestionCoverageStatus.INCOMPLETE));
        request.get(BASE + "index/overview", HttpStatus.FORBIDDEN, IndexOverviewDTO.class);
        request.getList(BASE + "coverage", HttpStatus.FORBIDDEN, IngestionCoverageDTO.class);
        request.getList(BASE + "coverage/page", HttpStatus.FORBIDDEN, IngestionCoverageDTO.class);
        request.performMvcRequest(post(BASE + "coverage/refresh")).andExpect(status().isForbidden());
        assertThat(ingestionCoverageRepository.findByCourseId(course.getId()).orElseThrow().getTypeCounts()).isEqualTo(stored.getTypeCounts());
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotReadTheOverview() throws Exception {
        request.get(BASE + "index/overview", HttpStatus.UNAUTHORIZED, String.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void overviewReportsReachabilityAndCollectionCounts() throws Exception {
        IndexOverviewDTO overview = request.get(BASE + "index/overview", HttpStatus.OK, IndexOverviewDTO.class);

        assertThat(overview).isNotNull();
        assertThat(overview.weaviateAddress()).isNotBlank();
        // The container is running, so the SearchableEntities collection is present and counted.
        assertThat(overview.collections()).anySatisfy(collection -> assertThat(collection.collection()).isEqualTo("SearchableEntities"));

        var json = objectMapper.readTree(request.get(BASE + "index/overview", HttpStatus.OK, String.class));
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("weaviateReachable", "weaviateAddress", "irisEnabled", "irisReachable", "collections");
        assertThat(json.get("weaviateReachable").isBoolean()).isTrue();
        assertThat(json.get("weaviateAddress").isString()).isTrue();
        assertThat(json.get("weaviateAddress").stringValue()).isNotBlank();
        // Iris is disabled in the integration-test configuration; false must be present on the wire.
        assertThat(json.get("irisEnabled").isBoolean()).isTrue();
        assertThat(json.get("irisEnabled").booleanValue()).isFalse();
        assertThat(json.get("irisReachable").isBoolean()).isTrue();
        assertThat(json.get("irisReachable").booleanValue()).isFalse();
        var collections = json.get("collections");
        assertThat(collections.isArray()).isTrue();
        assertThat(collections).hasSize(5);
        assertThat(collections.get(0).get("collection").stringValue()).isEqualTo("SearchableEntities");
        assertThat(collections.get(1).get("collection").stringValue()).isEqualTo("Lectures");
        assertThat(collections.get(2).get("collection").stringValue()).isEqualTo("LectureTranscriptions");
        assertThat(collections.get(3).get("collection").stringValue()).isEqualTo("LectureUnitSegments");
        assertThat(collections.get(4).get("collection").stringValue()).isEqualTo("LectureUnits");
        for (var collection : collections) {
            assertThat(collection.get("collection").isString()).isTrue();
            assertThat(collection.get("readable").isBoolean()).isTrue();
            if (collection.get("readable").booleanValue()) {
                assertThat(collection.propertyNames()).containsExactlyInAnyOrder("collection", "count", "readable");
                assertThat(collection.get("count").isIntegralNumber()).isTrue();
                assertThat(collection.get("count").longValue()).isNotNegative();
            }
            else {
                assertThat(collection.propertyNames()).containsExactlyInAnyOrder("collection", "readable");
            }
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void storedCoverageReadsAndMapsTheProjection() throws Exception {
        ingestionCoverageRepository.save(storedEntry(IngestionCoverageStatus.INCOMPLETE));

        // The endpoint is paginated and defaults to 20 rows. The projection table is shared across test classes and
        // accumulates a row per course, so without an explicit size this course's row can sit on a later page and the
        // assertion below would fail purely on how many courses other tests happened to create.
        List<IngestionCoverageDTO> coverage = request.getList(BASE + "coverage?size=2000", HttpStatus.OK, IngestionCoverageDTO.class);

        // The seeded counts come back unchanged: the course has no exercises, so a recompute would have replaced them.
        assertThat(coverage).filteredOn(dto -> dto.courseId() == course.getId()).singleElement().satisfies(dto -> {
            assertThat(dto.status()).isEqualTo(IngestionCoverageStatus.INCOMPLETE);
            assertThat(dto.typeCounts()).containsExactly(new IngestionTypeCountDTO("exercise", 5, 4, 1, 0));
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
        assertThat(filtered).filteredOn(dto -> dto.courseId() == course.getId()).singleElement()
                .satisfies(dto -> assertThat(dto.typeCounts()).containsExactly(new IngestionTypeCountDTO("exercise", 5, 4, 1, 0)));

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
    void pagesOfTiedRowsShowEveryCourseExactlyOnce() throws Exception {
        // Three courses tie on both sort keys used here: the same start date live, the same gap score stored.
        List<Long> courseIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Course tied = courseUtilService.createCourse();
            tied.setTitle(TEST_PREFIX + "-tied-" + tied.getId());
            tied.setStartDate(course.getStartDate());
            courseIds.add(courseRepository.save(tied).getId());
            IngestionCoverageEntry entry = storedEntry(IngestionCoverageStatus.INCOMPLETE);
            entry.setCourseId(tied.getId());
            entry.setCourseTitle(tied.getTitle());
            ingestionCoverageRepository.save(entry);
        }
        List<Long> live = new ArrayList<>();
        List<Long> stored = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            String query = "size=1&page=" + page + "&search=" + TEST_PREFIX + "-tied-";
            request.getList(BASE + "coverage/page?sort=startDate,desc&" + query, HttpStatus.OK, IngestionCoverageDTO.class).forEach(dto -> live.add(dto.courseId()));
            request.getList(BASE + "coverage?sort=coverageGapScore,desc&" + query, HttpStatus.OK, IngestionCoverageDTO.class).forEach(dto -> stored.add(dto.courseId()));
        }

        assertThat(live).containsExactlyInAnyOrderElementsOf(courseIds);
        assertThat(stored).containsExactlyInAnyOrderElementsOf(courseIds);
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
    void refreshAnswersOnceTheRecomputeHasFinished() throws Exception {
        ingestionCoverageRepository.save(storedEntry(IngestionCoverageStatus.INCOMPLETE));

        var response = request.performMvcRequest(post(BASE + "coverage/refresh")).andExpect(status().isOk()).andReturn().getResponse();

        assertThat(response.getContentAsByteArray()).isEmpty();

        // The seeded row claims one missing exercise the course does not have, so a finished recompute has replaced it.
        assertThat(ingestionCoverageRepository.findByCourseId(course.getId()).orElseThrow().getTypeCounts())
                .noneMatch(count -> count.type().equals("exercise") && count.missing() > 0);
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotRefreshOrChangeTheProjection() throws Exception {
        var stored = ingestionCoverageRepository.save(storedEntry(IngestionCoverageStatus.INCOMPLETE));

        request.performMvcRequest(post(BASE + "coverage/refresh")).andExpect(status().isUnauthorized());

        assertThat(ingestionCoverageRepository.findByCourseId(course.getId()).orElseThrow().getTypeCounts()).isEqualTo(stored.getTypeCounts());
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotReadEitherCoverageEndpoint() throws Exception {
        request.get(BASE + "coverage", HttpStatus.UNAUTHORIZED, String.class);
        request.get(BASE + "coverage/page", HttpStatus.UNAUTHORIZED, String.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void storedCoverageBindsFalseAndLiteralSearchAndOmitsEmptyMetadata() throws Exception {
        var entry = storedEntry(IngestionCoverageStatus.EMPTY);
        entry.setCourseTitle("C++ & 50%_\\ Überprüfung 東京");
        entry.setActive(false);
        entry.setCoverageGapScore(0);
        entry.setTypeCounts(List.of());
        ingestionCoverageRepository.save(entry);
        var decoy = storedEntry(IngestionCoverageStatus.EMPTY);
        decoy.setCourseId(course.getId() + 1_000_000L);
        decoy.setCourseTitle("C++ & 50XX\\ Überprüfung 東京");
        decoy.setActive(false);
        ingestionCoverageRepository.save(decoy);

        var response = request.performMvcRequest(get(BASE + "coverage").param("search", "  " + entry.getCourseTitle() + "  ").param("active", "false").param("status", "EMPTY")
                .param("page", "0").param("size", "1").param("sort", "courseTitle,asc")).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeader("X-Total-Count")).isEqualTo("1");
        var rows = objectMapper.readTree(response.getContentAsString());
        assertThat(rows.isArray()).isTrue();
        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.get("courseId").longValue()).isEqualTo(course.getId());
        assertThat(row.get("courseTitle").stringValue()).isEqualTo(entry.getCourseTitle());
        assertThat(row.get("active").booleanValue()).isFalse();
        assertThat(row.get("coverageGapScore").intValue()).isZero();
        assertThat(row.get("status").stringValue()).isEqualTo("EMPTY");
        assertThat(row.get("computedAt").isString()).isTrue();
        assertThat(row.has("typeCounts")).isFalse();
        assertThat(row.has("releaseDate")).isFalse();
        assertThat(row.has("semester")).isFalse();
        assertThat(row.has("lastIngestedAt")).isFalse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void bothCoverageEndpointsExposeFullTotalsAndRespectOmittedPaginationDefaults() throws Exception {
        for (int i = 0; i < 2; i++) {
            var matching = courseUtilService.createCourse();
            matching.setTitle(TEST_PREFIX + "-headers-" + matching.getId());
            matching = courseRepository.save(matching);
            var entry = storedEntry(IngestionCoverageStatus.COMPLETE);
            entry.setCourseId(matching.getId());
            entry.setCourseTitle(matching.getTitle());
            ingestionCoverageRepository.save(entry);
        }
        for (String endpoint : List.of("coverage", "coverage/page")) {
            var response = request.performMvcRequest(get(BASE + endpoint).param("search", TEST_PREFIX + "-headers-").param("page", "0").param("size", "1"))
                    .andExpect(status().isOk()).andReturn().getResponse();
            assertThat(response.getHeader("X-Total-Count")).isEqualTo("2");
            assertThat(response.getHeader("Link")).contains("page=1", "rel=\"next\"");
            var page = objectMapper.readTree(response.getContentAsString());
            assertThat(page.isArray()).isTrue();
            assertThat(page).hasSize(1);
            assertThat(page.get(0).get("computedAt").isString()).isTrue();
            assertThat(page.get(0).get("status").isString()).isTrue();
            var defaults = request.performMvcRequest(get(BASE + endpoint).param("search", TEST_PREFIX + "-headers-")).andExpect(status().isOk()).andReturn().getResponse();
            assertThat(defaults.getHeader("X-Total-Count")).isEqualTo("2");
            assertThat(objectMapper.readTree(defaults.getContentAsString())).hasSize(2);
            var empty = request.performMvcRequest(get(BASE + endpoint).param("search", TEST_PREFIX + "-missing-")).andExpect(status().isOk()).andReturn().getResponse();
            assertThat(empty.getHeader("X-Total-Count")).isEqualTo("0");
            assertThat(objectMapper.readTree(empty.getContentAsString())).isEmpty();
        }
    }

    /**
     * A projection row for the test course. The success marker set in {@link #setUp()} keeps the endpoint's
     * stale-while-revalidate trigger a no-op, so the stored-coverage tests read back exactly what they wrote.
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
