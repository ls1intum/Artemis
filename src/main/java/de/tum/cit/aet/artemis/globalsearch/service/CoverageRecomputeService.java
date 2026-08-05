package de.tum.cit.aet.artemis.globalsearch.service;

import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURES_COLLECTION;
import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURE_TRANSCRIPTIONS_COLLECTION;
import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURE_UNITS_COLLECTION;
import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURE_UNIT_SEGMENTS_COLLECTION;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.dto.CourseEntityIdDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageStatus;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionTypeCountDTO;
import de.tum.cit.aet.artemis.globalsearch.repository.IngestionCoverageRepository;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.PresentMetadata;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;

/**
 * Recomputes the exact per-course {@link IngestionCoverageEntry} projection by reading the database (the expected id-sets)
 * and Weaviate (the present id-sets), diffing them per type, and upserting the result. It never trusts the write path -
 * coverage is derived from what Weaviate actually holds versus what the database expects.
 * <p>
 * The recompute is expensive and runs OFF the request path: the dashboard serves the last stored projection instantly and
 * triggers a background recompute only when the data is stale (stale-while-revalidate) or on an explicit refresh. A
 * cluster-wide Hazelcast lock ensures at most one recompute runs across all nodes at a time, so concurrent dashboard opens
 * (which can land on any node behind the load balancer) never fan out into duplicate recomputes.
 * <p>
 * Content coverage: slides and transcript are diffed at lecture-unit granularity (expected units from the DB vs the
 * distinct present units in Weaviate). Segment and unit summaries are PRESENT-ONLY - their present counts are stored but
 * never diffed, because a summary can legitimately exist without full content and the plan decoupled summaries from
 * content-correctness. Lecture and exam data is reached through the module {@code api} packages, so when either module is
 * disabled those types are simply omitted rather than breaking the recompute.
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

    private static final String LOCK_MAP = "ingestion-coverage-recompute";

    private static final String LOCK_KEY = "recompute";

    private static final int LOCK_WAIT_SECONDS = 1;

    private final ExerciseRepository exerciseRepository;

    private final FaqRepository faqRepository;

    private final ChannelRepository channelRepository;

    private final CourseRepository courseRepository;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final IngestionCoverageWeaviateReadService weaviateReadService;

    private final IngestionCoverageRepository coverageRepository;

    private final HazelcastInstance hazelcastInstance;

    public CoverageRecomputeService(ExerciseRepository exerciseRepository, FaqRepository faqRepository, ChannelRepository channelRepository, CourseRepository courseRepository,
            Optional<LectureRepositoryApi> lectureRepositoryApi, Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi, Optional<ExamRepositoryApi> examRepositoryApi,
            IngestionCoverageWeaviateReadService weaviateReadService, IngestionCoverageRepository coverageRepository,
            @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.exerciseRepository = exerciseRepository;
        this.faqRepository = faqRepository;
        this.channelRepository = channelRepository;
        this.courseRepository = courseRepository;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
        this.examRepositoryApi = examRepositoryApi;
        this.weaviateReadService = weaviateReadService;
        this.coverageRepository = coverageRepository;
        this.hazelcastInstance = hazelcastInstance;
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
        IMap<String, Instant> lockMap = hazelcastInstance.getMap(LOCK_MAP);
        boolean locked = false;
        try {
            locked = lockMap.tryLock(LOCK_KEY, LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
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
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        catch (Exception e) {
            log.error("Coverage recompute failed", e);
            return false;
        }
        finally {
            if (locked) {
                lockMap.unlock(LOCK_KEY);
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

        ExpectedSets expected = loadExpectedSets(courseIds);
        // Content only exists for courses that have lecture units, so the (heavier) per-course content aggregations are
        // read only for those courses; the rest cannot have slides, transcript, or summaries.
        Set<Long> contentCourseIds = expected.lectureUnits().keySet();
        PresentSets present = loadPresentSets(courseIds, contentCourseIds);
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

    // ----- Expected (database) -----

    private record ExpectedSets(Map<Long, Set<Long>> exercises, Map<Long, Set<Long>> lectures, Map<Long, Set<Long>> lectureUnits, Map<Long, Set<Long>> exams,
            Map<Long, Set<Long>> faqs, Map<Long, Set<Long>> channels, Map<Long, Set<Long>> pdfUnits, Map<Long, Set<Long>> videoUnits) {
    }

    private ExpectedSets loadExpectedSets(Collection<Long> courseIds) {
        Map<Long, Set<Long>> exercises = bucket(exerciseRepository.findExerciseIdCourseIdPairsForCourses(courseIds));
        Map<Long, Set<Long>> faqs = bucket(faqRepository.findFaqIdCourseIdPairsForCourses(courseIds));
        Map<Long, Set<Long>> channels = bucket(channelRepository.findIndexableChannelIdCourseIdPairsForCourses(courseIds));
        Map<Long, Set<Long>> lectures = bucket(lectureRepositoryApi.map(api -> api.findLectureIdCourseIdPairsForCourses(courseIds)).orElse(List.of()));
        Map<Long, Set<Long>> exams = bucket(examRepositoryApi.map(api -> api.findExamIdCourseIdPairsForCourses(courseIds)).orElse(List.of()));
        Map<Long, Set<Long>> lectureUnits = bucket(lectureUnitRepositoryApi.map(api -> api.findIndexableUnitIdCourseIdPairsForCourses(courseIds)).orElse(List.of()));
        Map<Long, Set<Long>> pdfUnits = bucket(lectureUnitRepositoryApi.map(api -> api.findUnitIdCourseIdPairsWithPdfAttachmentForCourses(courseIds)).orElse(List.of()));
        Map<Long, Set<Long>> videoUnits = bucket(lectureUnitRepositoryApi.map(api -> api.findUnitIdCourseIdPairsWithVideoForCourses(courseIds)).orElse(List.of()));
        return new ExpectedSets(exercises, lectures, lectureUnits, exams, faqs, channels, pdfUnits, videoUnits);
    }

    // ----- Present (Weaviate) -----

    private record PresentSets(Map<Long, Map<String, Set<Long>>> metadataByCourse, Map<Long, Instant> lastIngestedAt, Map<Long, Set<Long>> slides, Map<Long, Set<Long>> transcript,
            Map<Long, Set<Long>> segmentSummaries, Map<Long, Set<Long>> unitSummaries) {
    }

    private PresentSets loadPresentSets(Collection<Long> courseIds, Collection<Long> contentCourseIds) {
        PresentMetadata metadata = weaviateReadService.readPresentMetadata(courseIds);
        Map<Long, Set<Long>> slides = weaviateReadService.readPresentContentUnitIds(LECTURES_COLLECTION, contentCourseIds);
        Map<Long, Set<Long>> transcript = weaviateReadService.readPresentContentUnitIds(LECTURE_TRANSCRIPTIONS_COLLECTION, contentCourseIds);
        Map<Long, Set<Long>> segmentSummaries = weaviateReadService.readPresentContentUnitIds(LECTURE_UNIT_SEGMENTS_COLLECTION, contentCourseIds);
        Map<Long, Set<Long>> unitSummaries = weaviateReadService.readPresentContentUnitIds(LECTURE_UNITS_COLLECTION, contentCourseIds);
        return new PresentSets(metadata.presentIdsByCourseAndType(), metadata.lastIngestedAtByCourse(), slides, transcript, segmentSummaries, unitSummaries);
    }

    // ----- Diff + assemble -----

    private IngestionCoverageEntry buildEntry(Course course, ExpectedSets expected, PresentSets present, Map<Long, IngestionCoverageEntry> existingByCourseId, Instant computedAt) {
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

        IngestionCoverageEntry entry = existingByCourseId.get(courseId);
        if (entry == null) {
            entry = new IngestionCoverageEntry();
        }
        entry.setCourseId(courseId);
        entry.setTypeCounts(counts);
        entry.setCoverageGapScore((int) Math.min(Integer.MAX_VALUE, totalMissing));
        entry.setStatus(deriveStatus(totalExpected, totalMissing));
        entry.setCourseTitle(course.getTitle());
        entry.setReleaseDate(course.getStartDate());
        entry.setActive(isActive(course));
        entry.setSemester(course.getSemester());
        entry.setComputedAt(computedAt.atZone(java.time.ZoneOffset.UTC));
        entry.setLastIngestedAt(toZonedDateTime(present.lastIngestedAt().get(courseId)));
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

    /** Buckets flat (courseId, entityId) pairs into a per-course id-set. */
    private static Map<Long, Set<Long>> bucket(List<CourseEntityIdDTO> pairs) {
        return pairs.stream().collect(Collectors.groupingBy(CourseEntityIdDTO::courseId, Collectors.mapping(CourseEntityIdDTO::entityId, Collectors.toSet())));
    }

    private static ZonedDateTime toZonedDateTime(Instant instant) {
        return instant == null ? null : instant.atZone(java.time.ZoneOffset.UTC);
    }
}
