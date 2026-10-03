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

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
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
 * The recompute is expensive, so it runs only when the stored projection is stale or on an explicit refresh, and the
 * request that triggers it waits for it: answering first and recomputing afterwards served the old rows to exactly the
 * request that asked for newer ones. A cluster-wide lock ensures at most one recompute runs across all nodes at a time, so concurrent dashboard opens
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

    private static final String SUCCESS_MAP_NAME = "ingestion-coverage-successful-full-recompute";

    private static final String FAILED_MAP_NAME = "ingestion-coverage-failed-recompute";

    private static final String PROJECTION_KEY = "projection";

    /** Name assigned by the ingestion-coverage Liquibase changeset to its sole course row uniqueness constraint. */
    private static final String COURSE_ID_UNIQUE_CONSTRAINT = "ux_ingestion_coverage_course_id";

    private static final Duration FAILED_ATTEMPT_BACKOFF = Duration.ofSeconds(30);

    /**
     * How long a caller waits for a recompute that is already running. A refresh waits because that recompute may have
     * read the data before the change the admin is refreshing for; a stale read waits because giving up served it the
     * old rows while the new ones were being computed.
     */
    private static final Duration LOCK_WAIT = Duration.ofMinutes(5);

    private final IngestionCoverageSetLoader setLoader;

    private final CourseRepository courseRepository;

    private final IngestionCoverageRepository coverageRepository;

    private final DistributedDataProvider distributedDataProvider;

    private DistributedMap<String, Boolean> successfulFullRecomputes;

    private DistributedMap<String, Boolean> failedRecomputes;

    public enum RecomputeOutcome {
        FRESH, RECOMPUTED, FAILED, LOCK_TIMEOUT
    }

    public CoverageRecomputeService(IngestionCoverageSetLoader setLoader, CourseRepository courseRepository, IngestionCoverageRepository coverageRepository,
            DistributedDataProvider distributedDataProvider) {
        this.setLoader = setLoader;
        this.courseRepository = courseRepository;
        this.coverageRepository = coverageRepository;
        this.distributedDataProvider = distributedDataProvider;
    }

    /**
     * If the stored projection is older than {@link #FRESHNESS_WINDOW} (or missing), recomputes it under the cluster lock
     * before returning, so the caller reads the result; if another recompute is already running, waits for it instead.
     * Cheap to call on every dashboard open: fresh data returns at once without touching the lock.
     *
     * @return whether the projection was already fresh, was recomputed, failed, or could not acquire the lock
     */
    public RecomputeOutcome triggerRecomputeIfStale() {
        return runUnderLock(true, false, LOCK_WAIT);
    }

    /**
     * Recomputes the whole projection under the cluster lock, ignoring freshness, and returns once it is done. Backs the
     * refresh button. Waits up to {@link #LOCK_WAIT} for a recompute that is already running, then runs its own.
     *
     * @return the explicit outcome of the refresh attempt
     */
    public RecomputeOutcome forceRecompute() {
        // A requested refresh means the administrator does not accept the previously certified projection any more.
        // Remove that certificate before taking the lock so a concurrent stored read cannot call stale rows fresh.
        successfulFullRecomputes().remove(PROJECTION_KEY);
        return runUnderLock(false, true, LOCK_WAIT);
    }

    /**
     * Acquires the cluster lock and recomputes (optionally only if the projection is stale). Package-private and
     * returning whether a recompute actually ran so tests can drive it synchronously and assert the lease behavior.
     *
     * @param onlyIfStale         when {@code true}, recompute only if no successful full-recompute marker remains
     * @param bypassFailedBackoff whether an explicit refresh may retry despite a recent failed attempt
     * @param lockWait            how long to wait for a recompute already running elsewhere
     * @return the explicit outcome of the trigger
     */
    RecomputeOutcome runUnderLock(boolean onlyIfStale, boolean bypassFailedBackoff, Duration lockWait) {
        // Checked before the lock too, so reading fresh data never waits behind a recompute someone else started.
        if (onlyIfStale && hasSuccessfulFullRecompute()) {
            return RecomputeOutcome.FRESH;
        }
        if (!bypassFailedBackoff && hasFailedRecentRecompute()) {
            return RecomputeOutcome.FAILED;
        }
        DistributedLock lock = distributedDataProvider.getLock(LOCK_NAME);
        boolean locked = false;
        try {
            locked = lock.tryLock(lockWait);
            if (!locked) {
                log.warn("Coverage recompute skipped: another recompute held the lock for longer than {}", lockWait);
                return RecomputeOutcome.LOCK_TIMEOUT;
            }
            // Both distributed markers must be checked under the lock as another node could have finished or failed
            // while this caller waited. Per-course browser writes never set the success marker.
            if (onlyIfStale && hasSuccessfulFullRecompute()) {
                return RecomputeOutcome.FRESH;
            }
            if (!bypassFailedBackoff && hasFailedRecentRecompute()) {
                return RecomputeOutcome.FAILED;
            }
            recomputeAllCourses();
            successfulFullRecomputes().put(PROJECTION_KEY, Boolean.TRUE);
            failedRecomputes().remove(PROJECTION_KEY);
            return RecomputeOutcome.RECOMPUTED;
        }
        catch (Exception e) {
            log.error("Coverage recompute failed", e);
            // A failed forced refresh must never leave an old projection certified as complete.
            successfulFullRecomputes().remove(PROJECTION_KEY);
            failedRecomputes().put(PROJECTION_KEY, Boolean.TRUE);
            return RecomputeOutcome.FAILED;
        }
        finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    private DistributedMap<String, Boolean> successfulFullRecomputes() {
        if (successfulFullRecomputes == null) {
            successfulFullRecomputes = distributedDataProvider.getExpiringMap(SUCCESS_MAP_NAME, FRESHNESS_WINDOW);
        }
        return successfulFullRecomputes;
    }

    private DistributedMap<String, Boolean> failedRecomputes() {
        if (failedRecomputes == null) {
            failedRecomputes = distributedDataProvider.getExpiringMap(FAILED_MAP_NAME, FAILED_ATTEMPT_BACKOFF);
        }
        return failedRecomputes;
    }

    private boolean hasSuccessfulFullRecompute() {
        return successfulFullRecomputes().containsKey(PROJECTION_KEY);
    }

    private boolean hasFailedRecentRecompute() {
        return failedRecomputes().containsKey(PROJECTION_KEY);
    }

    /**
     * Recomputes the projection for every course in the database and removes rows for courses that no longer exist. Any
     * course that fails to map aborts the run: returning a partial projection as current would make filters and
     * cross-course sorting silently omit that course.
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
        PresentSets present = setLoader.loadPresent(courseIds);
        Instant computedAt = Instant.now();

        for (Course course : courses) {
            try {
                IngestionCoverageEntry entry = buildEntry(course, expected, present, existingByCourseId, computedAt);
                upsertResolvingConcurrentFirstInsert(course, expected, present, computedAt, entry);
            }
            catch (Exception exception) {
                log.warn("Coverage recompute failed for course {}", course.getId(), exception);
                throw exception;
            }
        }

        Set<Long> existingCourseIds = new HashSet<>(courseIds);
        List<IngestionCoverageEntry> stale = existingRows.stream().filter(entry -> !existingCourseIds.contains(entry.getCourseId())).toList();
        if (!stale.isEmpty()) {
            coverageRepository.deleteAll(stale);
        }
    }

    /**
     * Stores coverage for one course from sets the caller has already loaded, so the stored views show what the content
     * browser just showed for it rather than the last full recompute's numbers.
     *
     * @param course   the course the sets were loaded for
     * @param expected what the database expects indexed
     * @param present  what the index holds
     */
    public void storeCourseCoverage(Course course, ExpectedSets expected, PresentSets present) {
        Map<Long, IngestionCoverageEntry> existing = coverageRepository.findByCourseId(course.getId()).map(entry -> Map.of(course.getId(), entry)).orElse(Map.of());
        Instant computedAt = Instant.now();
        upsertResolvingConcurrentFirstInsert(course, expected, present, computedAt, buildEntry(course, expected, present, existing, computedAt));
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
        PresentSets present = setLoader.loadPresent(courseIds);
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
        // The repository takes the empty string, not null, for "no search"; see its javadoc for why.
        String titleSearch = search == null || search.isBlank() ? "" : search.trim();
        return coverageRepository.findFiltered(status, active, titleSearch, withTieBreaker(pageable, "courseId")).map(this::toDto);
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
        // Trimmed the same way as readStoredCoverage, so a search with incidental leading/trailing whitespace (e.g.
        // pasted from elsewhere) matches the same courses whichever view is currently active.
        Pageable stablePageable = withTieBreaker(pageable, "id");
        Page<Course> courses = search == null || search.isBlank() ? courseRepository.findAll(stablePageable)
                : courseRepository.findByTitleIgnoreCaseContaining(search.trim(), stablePageable);
        return new PageImpl<>(computeCoverageLive(courses.getContent()), pageable, courses.getTotalElements());
    }

    /**
     * Appends a unique property to the sort. Many courses share a release date or a gap score, and without it the database
     * may order tied rows differently for each page, so a course could appear on two pages and another on none.
     */
    private static Pageable withTieBreaker(Pageable pageable, String uniqueProperty) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().and(Sort.by(uniqueProperty)));
    }

    private IngestionCoverageDTO toDto(IngestionCoverageEntry entry) {
        return new IngestionCoverageDTO(entry.getCourseId(), entry.getCourseTitle(), entry.getReleaseDate(), entry.isActive(), entry.getSemester(), entry.getStatus(),
                entry.getCoverageGapScore(), entry.getComputedAt(), entry.getLastIngestedAt(), entry.getTypeCounts());
    }

    // ----- Diff + assemble -----

    /** The DB/Weaviate-derived part of one course's coverage, shared by the stored recompute and the live page view. */
    private record CoverageComputation(List<IngestionTypeCountDTO> counts, IngestionCoverageStatus status, int gapScore, ZonedDateTime lastIngestedAt) {
    }

    /**
     * The per-type expected-vs-present counts for one course.
     *
     * Shared with the content browser, which names the same gaps: a number in the matrix and the list behind it are two
     * views of one computation rather than two computations that ought to agree. Static because the browser needs the
     * counts alone, without the recompute's status, severity and persistence around them.
     *
     * @param courseId the course to diff
     * @param expected what the database expects indexed, already loaded
     * @param present  what the index holds, already loaded
     * @return one count per measured type, in a fixed order
     */
    public static List<IngestionTypeCountDTO> typeCountsForCourse(long courseId, ExpectedSets expected, PresentSets present) {
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
        counts.add(presentOnly(TYPE_SEGMENT_SUMMARY, present.segmentSummaries().get(courseId), expected.lectureUnits().get(courseId)));
        counts.add(presentOnly(TYPE_UNIT_SUMMARY, present.unitSummaries().get(courseId), expected.lectureUnits().get(courseId)));
        return counts;
    }

    private CoverageComputation computeTypeCounts(Course course, ExpectedSets expected, PresentSets present) {
        long courseId = course.getId();
        List<IngestionTypeCountDTO> counts = typeCountsForCourse(courseId, expected, present);

        long totalMissing = counts.stream().mapToLong(IngestionTypeCountDTO::missing).sum();
        long totalOrphaned = counts.stream().mapToLong(IngestionTypeCountDTO::orphaned).sum();
        long totalExpected = counts.stream().mapToLong(IngestionTypeCountDTO::expected).sum();
        // Orphaned counts towards the severity as well as the status: a course whose index still holds objects for
        // content that no longer exists answers searches with stale hits, so worst-first has to surface it rather than
        // rank it alongside a course with nothing wrong. A present-only type reports an orphan only for a unit the
        // database has lost, so an ordinary summary still cannot push a course off COMPLETE.
        long totalGap = totalMissing + totalOrphaned;
        return new CoverageComputation(counts, deriveStatus(totalExpected, totalMissing, totalOrphaned), (int) Math.min(Integer.MAX_VALUE, totalGap),
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

    /**
     * Present-only type (e.g. summaries): never reported missing, because a summary can legitimately exist without the
     * content it summarises and nothing requires one to be there.
     * <p>
     * A summary for a lecture unit the database no longer has is a different thing. Nothing will ever summarise that
     * unit again, so the object is stale rather than optional, and it is reported orphaned like any other leftover.
     * Without that, a course whose units were all deleted showed its slides and transcript red for exactly these
     * objects while its summaries read green, describing one pile of stale data two contradictory ways.
     */
    private static IngestionTypeCountDTO presentOnly(String type, Set<Long> present, Set<Long> unitsInDatabase) {
        Set<Long> presentIds = present == null ? Set.of() : present;
        Set<Long> knownUnitIds = unitsInDatabase == null ? Set.of() : unitsInDatabase;
        long orphaned = presentIds.stream().filter(unitId -> !knownUnitIds.contains(unitId)).count();
        return new IngestionTypeCountDTO(type, presentIds.size() - orphaned, presentIds.size(), 0, orphaned);
    }

    /**
     * EMPTY means there is genuinely nothing to say about this course: nothing expected and nothing stale left behind.
     * A course that expects nothing but still holds orphaned objects is not empty, it needs cleaning, so it reports
     * INCOMPLETE rather than being filtered away as uninteresting.
     */
    private static IngestionCoverageStatus deriveStatus(long totalExpected, long totalMissing, long totalOrphaned) {
        if (totalExpected == 0 && totalOrphaned == 0) {
            return IngestionCoverageStatus.EMPTY;
        }
        return totalMissing == 0 && totalOrphaned == 0 ? IngestionCoverageStatus.COMPLETE : IngestionCoverageStatus.INCOMPLETE;
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

    /**
     * A browser write and a full recompute do not share the global lock. If both insert a course's first projection row,
     * retry only the known {@code course_id} uniqueness race with the row that won the insert; all other integrity
     * failures still fail the recompute.
     */
    private void upsertResolvingConcurrentFirstInsert(Course course, ExpectedSets expected, PresentSets present, Instant computedAt, IngestionCoverageEntry entry) {
        try {
            upsert(entry);
        }
        catch (DataIntegrityViolationException exception) {
            if (!isCourseIdUniquenessViolation(exception)) {
                throw exception;
            }
            IngestionCoverageEntry concurrentEntry = coverageRepository.findByCourseId(course.getId()).orElseThrow(() -> exception);
            upsert(buildEntry(course, expected, present, Map.of(course.getId(), concurrentEntry), computedAt));
        }
    }

    private static boolean isCourseIdUniquenessViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE
                    && constraintNameMatchesCourseIdUniqueConstraint(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * PostgreSQL returns the bare constraint name while MySQL qualifies it with the table name. The database's parsed
     * constraint name is stable across driver message formats, unlike text such as "duplicate" or "course_id".
     */
    private static boolean constraintNameMatchesCourseIdUniqueConstraint(String constraintName) {
        return constraintName != null && COURSE_ID_UNIQUE_CONSTRAINT.equalsIgnoreCase(constraintName.substring(constraintName.lastIndexOf('.') + 1));
    }

    // ----- Helpers -----

    private static ZonedDateTime toZonedDateTime(Instant instant) {
        return instant == null ? null : instant.atZone(java.time.ZoneOffset.UTC);
    }
}
