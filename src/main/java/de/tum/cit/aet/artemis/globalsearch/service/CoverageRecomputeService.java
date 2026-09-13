package de.tum.cit.aet.artemis.globalsearch.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageStatus;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionCoverageDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionTypeCountDTO;
import de.tum.cit.aet.artemis.globalsearch.repository.IngestionCoverageRepository;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageSetLoader.ExpectedSets;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageSetLoader.PresentSets;

/**
 * Recomputes the exact per-course {@link IngestionCoverageEntry} projection by reading the database (the expected id-sets)
 * and Weaviate (the present id-sets), diffing them per type, and upserting the result. It never trusts the write path -
 * coverage is derived from what Weaviate actually holds versus what the database expects.
 * <p>
 * The recompute is expensive and runs OFF the request path: the dashboard serves the last stored projection instantly and
 * triggers a background recompute only when the data is stale (stale-while-revalidate) or on an explicit refresh. A
 * cluster-wide lock ensures at most one recompute runs across all nodes at a time, so concurrent dashboard opens
 * (which can land on any node behind the load balancer) never fan out into duplicate recomputes.
 * <p>
 * Content coverage: slides and transcript are diffed at lecture-unit granularity (expected units from the DB vs the
 * distinct present units in Weaviate). Segment and unit summaries are PRESENT-ONLY - their present counts are stored but
 * never diffed, because a summary can legitimately exist without full content and the plan decoupled summaries from
 * content-correctness. The expected and present id-sets themselves come from {@link IngestionCoverageSetLoader}, which the
 * content browser reads too, so the counts reported here and the entities the browser names as missing are derived from
 * one set of rules rather than two.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class CoverageRecomputeService {

    private static final Logger log = LoggerFactory.getLogger(CoverageRecomputeService.class);

    /** Content coverage type labels (stored on the projection alongside the metadata {@code SearchableEntitySchema} types). */
    public static final String TYPE_SLIDES = "slides";

    public static final String TYPE_TRANSCRIPT = "transcript";

    public static final String TYPE_SEGMENT_SUMMARY = "segment_summary";

    public static final String TYPE_UNIT_SUMMARY = "unit_summary";

    /** How old the stored projection may be before a dashboard open triggers a background recompute. */
    private static final Duration FRESHNESS_WINDOW = Duration.ofMinutes(15);

    private static final String LOCK_NAME = "ingestion-coverage-recompute";

    private static final Duration LOCK_WAIT = Duration.ofSeconds(1);

    private final IngestionCoverageSetLoader setLoader;

    private final CourseRepository courseRepository;

    private final IngestionCoverageRepository coverageRepository;

    private final DistributedDataProvider distributedDataProvider;

    public CoverageRecomputeService(IngestionCoverageSetLoader setLoader, CourseRepository courseRepository, IngestionCoverageRepository coverageRepository,
            DistributedDataProvider distributedDataProvider) {
        this.setLoader = setLoader;
        this.courseRepository = courseRepository;
        this.coverageRepository = coverageRepository;
        this.distributedDataProvider = distributedDataProvider;
    }

    /**
     * Stale-while-revalidate trigger: if the stored projection is older than {@link #FRESHNESS_WINDOW} (or missing),
     * recompute in the background under the cluster lock. Cheap and safe to call on every dashboard open - it returns
     * immediately if the data is fresh or another node already holds the lock.
     */
    @Async
    public void triggerRecomputeIfStale() {
        runUnderLock(true);
    }

    /**
     * Forces a full recompute in the background under the cluster lock, ignoring freshness. Backs the "Refresh data"
     * button. A no-op if another recompute is already running.
     */
    @Async
    public void forceRecompute() {
        runUnderLock(false);
    }

    /**
     * Acquires the cluster lock and recomputes (optionally only if the projection is stale). Package-private and
     * returning whether a recompute actually ran so tests can drive it synchronously and assert the lease behavior.
     *
     * @param onlyIfStale when {@code true}, recompute only if the stored projection is older than the freshness window
     * @return {@code true} if a recompute ran, {@code false} if it was skipped (lock held by another node, or fresh)
     */
    boolean runUnderLock(boolean onlyIfStale) {
        DistributedLock lock = distributedDataProvider.getLock(LOCK_NAME);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT);
            if (!locked) {
                log.debug("Coverage recompute skipped: another node is already recomputing");
                return false;
            }
            // Re-check freshness inside the lock so a queued trigger does not redo work a just-finished recompute did.
            if (onlyIfStale && !isStale()) {
                return false;
            }
            recomputeAllCourses();
            return true;
        }
        catch (Exception e) {
            log.error("Coverage recompute failed", e);
            return false;
        }
        finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    private boolean isStale() {
        Instant threshold = Instant.now().minus(FRESHNESS_WINDOW);
        return coverageRepository.findTopByOrderByComputedAtAsc().map(oldest -> oldest.getComputedAt().toInstant().isBefore(threshold)).orElse(true);
    }

    /**
     * Recomputes the projection for every course in the database and removes rows for courses that no longer exist. A
     * single course that fails to map does not abort the run - its row is left untouched and the rest proceed.
     * Package-private so tests can drive one recompute synchronously without the lock or the async boundary.
     */
    void recomputeAllCourses() {
        List<Course> courses = courseRepository.findAll();
        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        // Load the existing rows once, up front, for BOTH the per-course upsert lookup (avoiding a findByCourseId query
        // per course) and the stale-row deletion below (avoiding a second full-table read).
        List<IngestionCoverageEntry> existingRows = coverageRepository.findAll();
        if (courseIds.isEmpty()) {
            if (!existingRows.isEmpty()) {
                coverageRepository.deleteAll();
            }
            return;
        }
        Map<Long, IngestionCoverageEntry> existingByCourseId = existingRows.stream().collect(Collectors.toMap(IngestionCoverageEntry::getCourseId, entry -> entry));

        ExpectedSets expected = setLoader.loadExpected(courseIds);
        // Content only exists for courses that have lecture units, so the (heavier) per-course content aggregations are
        // read only for those courses; the rest cannot have slides, transcript, or summaries.
        Set<Long> contentCourseIds = expected.lectureUnits().keySet();
        PresentSets present = setLoader.loadPresent(courseIds, contentCourseIds);
        Instant computedAt = Instant.now();

        for (Course course : courses) {
            try {
                IngestionCoverageEntry entry = buildEntry(course, expected, present, existingByCourseId, computedAt);
                upsert(entry);
            }
            catch (Exception e) {
                log.warn("Skipping coverage for course {} that could not be computed: {}", course.getId(), e.getMessage());
            }
        }

        Set<Long> existingCourseIds = new HashSet<>(courseIds);
        List<IngestionCoverageEntry> stale = existingRows.stream().filter(entry -> !existingCourseIds.contains(entry.getCourseId())).toList();
        if (!stale.isEmpty()) {
            coverageRepository.deleteAll(stale);
        }
    }

    /**
     * Computes coverage for a set of courses live (reading the DB + Weaviate and diffing) and returns it as DTOs WITHOUT
     * persisting, for the default matrix page view. The visible page selects its courses by DB-native sort/search, so this
     * is called with only the ~25 courses on screen; the (heavier) content aggregations are read only for the ones that
     * have lecture units.
     *
     * @param courses the courses to compute coverage for (typically one page)
     * @return one coverage DTO per course, in the given order, computed at the request time
     */
    public List<IngestionCoverageDTO> computeCoverageLive(List<Course> courses) {
        if (courses.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        ExpectedSets expected = setLoader.loadExpected(courseIds);
        Set<Long> contentCourseIds = expected.lectureUnits().keySet();
        PresentSets present = setLoader.loadPresent(courseIds, contentCourseIds);
        ZonedDateTime computedAt = ZonedDateTime.now();

        List<IngestionCoverageDTO> result = new ArrayList<>();
        for (Course course : courses) {
            CoverageComputation computation = computeTypeCounts(course, expected, present);
            result.add(new IngestionCoverageDTO(course.getId(), course.getTitle(), course.getStartDate(), isActive(course), course.getSemester(), computation.status(),
                    computation.gapScore(), computedAt, computation.lastIngestedAt(), computation.counts()));
        }
        return result;
    }

    /**
     * Reads the stored coverage projection for the cross-course matrix views (worst-first, release-date,
     * most-recent-ingestion, status/active filters, title search), paginated and sorted on the projection's indexed
     * columns. Pure table read, no Weaviate access. The caller triggers the stale-while-revalidate recompute separately
     * (a sibling {@code @Async} call would not cross the proxy).
     *
     * @param status   an optional status to filter by, or {@code null} for all statuses
     * @param active   an optional active/inactive filter, or {@code null} for either
     * @param search   an optional case-insensitive course-title search, or {@code null}/blank for all titles
     * @param pageable the page and sort (on the projection columns)
     * @return the requested page of stored coverage rows as DTOs
     */
    public Page<IngestionCoverageDTO> readStoredCoverage(IngestionCoverageStatus status, Boolean active, String search, Pageable pageable) {
        String titleSearch = search == null || search.isBlank() ? null : search.trim();
        return coverageRepository.findFiltered(status, active, titleSearch, pageable).map(this::toDto);
    }

    /**
     * Selects a page of courses by DB-native sort/search and computes their coverage LIVE, for the default matrix view.
     * Always fresh; never reads the stored projection.
     *
     * @param search   an optional case-insensitive course-title search, or {@code null}/blank for all courses
     * @param pageable the page and sort (on {@code Course} columns, e.g. title / startDate)
     * @return the requested page of live-computed coverage DTOs
     */
    public Page<IngestionCoverageDTO> computeLiveCoveragePage(String search, Pageable pageable) {
        Page<Course> courses = search == null || search.isBlank() ? courseRepository.findAll(pageable) : courseRepository.findByTitleIgnoreCaseContaining(search, pageable);
        return new PageImpl<>(computeCoverageLive(courses.getContent()), pageable, courses.getTotalElements());
    }

    private IngestionCoverageDTO toDto(IngestionCoverageEntry entry) {
        return new IngestionCoverageDTO(entry.getCourseId(), entry.getCourseTitle(), entry.getReleaseDate(), entry.isActive(), entry.getSemester(), entry.getStatus(),
                entry.getCoverageGapScore(), entry.getComputedAt(), entry.getLastIngestedAt(), entry.getTypeCounts());
    }

    // ----- Diff + assemble -----

    /** The DB/Weaviate-derived part of one course's coverage, shared by the stored recompute and the live page view. */
    private record CoverageComputation(List<IngestionTypeCountDTO> counts, IngestionCoverageStatus status, int gapScore, ZonedDateTime lastIngestedAt) {
    }

    private CoverageComputation computeTypeCounts(Course course, ExpectedSets expected, PresentSets present) {
        long courseId = course.getId();
        Map<String, Set<Long>> presentMetadata = present.metadataByCourse().getOrDefault(courseId, Map.of());

        List<IngestionTypeCountDTO> counts = new ArrayList<>();
        counts.add(diff(SearchableEntitySchema.TypeValues.EXERCISE, expected.exercises().get(courseId), presentMetadata.get(SearchableEntitySchema.TypeValues.EXERCISE)));
        counts.add(diff(SearchableEntitySchema.TypeValues.LECTURE, expected.lectures().get(courseId), presentMetadata.get(SearchableEntitySchema.TypeValues.LECTURE)));
        counts.add(
                diff(SearchableEntitySchema.TypeValues.LECTURE_UNIT, expected.lectureUnits().get(courseId), presentMetadata.get(SearchableEntitySchema.TypeValues.LECTURE_UNIT)));
        counts.add(diff(SearchableEntitySchema.TypeValues.EXAM, expected.exams().get(courseId), presentMetadata.get(SearchableEntitySchema.TypeValues.EXAM)));
        counts.add(diff(SearchableEntitySchema.TypeValues.FAQ, expected.faqs().get(courseId), presentMetadata.get(SearchableEntitySchema.TypeValues.FAQ)));
        counts.add(diff(SearchableEntitySchema.TypeValues.CHANNEL, expected.channels().get(courseId), presentMetadata.get(SearchableEntitySchema.TypeValues.CHANNEL)));
        // The course itself is always expected to be indexed as a single object.
        counts.add(diff(SearchableEntitySchema.TypeValues.COURSE, Set.of(courseId), presentMetadata.get(SearchableEntitySchema.TypeValues.COURSE)));
        // Content: slides and transcript are diffed; summaries are present-only.
        counts.add(diff(TYPE_SLIDES, expected.pdfUnits().get(courseId), present.slides().get(courseId)));
        counts.add(diff(TYPE_TRANSCRIPT, expected.videoUnits().get(courseId), present.transcript().get(courseId)));
        counts.add(presentOnly(TYPE_SEGMENT_SUMMARY, present.segmentSummaries().get(courseId)));
        counts.add(presentOnly(TYPE_UNIT_SUMMARY, present.unitSummaries().get(courseId)));

        long totalMissing = counts.stream().mapToLong(IngestionTypeCountDTO::missing).sum();
        long totalExpected = counts.stream().mapToLong(IngestionTypeCountDTO::expected).sum();
        return new CoverageComputation(counts, deriveStatus(totalExpected, totalMissing), (int) Math.min(Integer.MAX_VALUE, totalMissing),
                toZonedDateTime(present.lastIngestedAt().get(courseId)));
    }

    private IngestionCoverageEntry buildEntry(Course course, ExpectedSets expected, PresentSets present, Map<Long, IngestionCoverageEntry> existingByCourseId, Instant computedAt) {
        long courseId = course.getId();
        CoverageComputation computation = computeTypeCounts(course, expected, present);

        IngestionCoverageEntry entry = existingByCourseId.get(courseId);
        if (entry == null) {
            entry = new IngestionCoverageEntry();
        }
        entry.setCourseId(courseId);
        entry.setTypeCounts(computation.counts());
        entry.setCoverageGapScore(computation.gapScore());
        entry.setStatus(computation.status());
        entry.setCourseTitle(course.getTitle());
        entry.setReleaseDate(course.getStartDate());
        entry.setActive(isActive(course));
        entry.setSemester(course.getSemester());
        entry.setComputedAt(computedAt.atZone(java.time.ZoneOffset.UTC));
        entry.setLastIngestedAt(computation.lastIngestedAt());
        return entry;
    }

    /** Exact expected-vs-present diff for one type. Null sets are treated as empty. */
    private static IngestionTypeCountDTO diff(String type, Set<Long> expected, Set<Long> present) {
        Set<Long> expectedIds = expected == null ? Set.of() : expected;
        Set<Long> presentIds = present == null ? Set.of() : present;
        long missing = expectedIds.stream().filter(id -> !presentIds.contains(id)).count();
        long orphaned = presentIds.stream().filter(id -> !expectedIds.contains(id)).count();
        return new IngestionTypeCountDTO(type, expectedIds.size(), presentIds.size(), missing, orphaned);
    }

    /** Present-only type (e.g. summaries): the present count is reported, but nothing is ever flagged missing or orphaned. */
    private static IngestionTypeCountDTO presentOnly(String type, Set<Long> present) {
        long count = present == null ? 0 : present.size();
        return new IngestionTypeCountDTO(type, count, count, 0, 0);
    }

    private static IngestionCoverageStatus deriveStatus(long totalExpected, long totalMissing) {
        if (totalExpected == 0) {
            return IngestionCoverageStatus.EMPTY;
        }
        return totalMissing == 0 ? IngestionCoverageStatus.COMPLETE : IngestionCoverageStatus.INCOMPLETE;
    }

    private static boolean isActive(Course course) {
        ZonedDateTime now = ZonedDateTime.now();
        boolean started = course.getStartDate() == null || !course.getStartDate().isAfter(now);
        boolean notEnded = course.getEndDate() == null || !course.getEndDate().isBefore(now);
        return started && notEnded;
    }

    private void upsert(IngestionCoverageEntry entry) {
        coverageRepository.save(entry);
    }

    // ----- Helpers -----

    private static ZonedDateTime toZonedDateTime(Instant instant) {
        return instant == null ? null : instant.atZone(java.time.ZoneOffset.UTC);
    }
}
