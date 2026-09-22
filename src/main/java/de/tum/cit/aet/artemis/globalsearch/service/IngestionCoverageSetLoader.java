package de.tum.cit.aet.artemis.globalsearch.service;

import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURES_COLLECTION;
import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURE_TRANSCRIPTIONS_COLLECTION;
import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURE_UNITS_COLLECTION;
import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURE_UNIT_SEGMENTS_COLLECTION;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.dto.CourseEntityIdDTO;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.repository.IngestionCoverageExpectedIdsRepository;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.PresentMetadata;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;

/**
 * Loads the two id-sets every ingestion-coverage answer is derived from: what the database expects to be indexed, and
 * what the index actually holds, both bucketed per course.
 * <p>
 * This exists as one class because two features consume the same sets and must never disagree. The coverage matrix
 * reports how many entities are missing; the content browser names which ones. If each computed its own expected set,
 * the matrix could report three missing while the browser named two, and a reader would have no way to tell which was
 * right. Sharing the loader makes that disagreement impossible rather than merely unlikely.
 * <p>
 * "Expected" is a claim about the indexing rules, so each query here has to stay equivalent to the condition under which
 * the indexer actually writes a row. That equivalence is the correctness property; the query shapes are not. A gap
 * between the two cannot be cleared by re-ingesting, so it is reported on every recompute for as long as the content
 * exists: the metadata sets follow the global-search indexer, while the content sets (slides, transcript) follow the
 * lecture ingestion path, which requires Iris to be enabled for the course and skips tutorial lectures, non-file
 * attachments, and anything that is not a PDF or a video.
 * <p>
 * Entities from optional modules (lecture, lecture unit, exam) are reached through their module {@code api} packages, so
 * a disabled module yields an empty set rather than breaking the load.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class IngestionCoverageSetLoader {

    /**
     * What the database expects to be indexed, per course. Every map is keyed by course id; a course with nothing of a
     * given type is simply absent from that map rather than mapped to an empty set.
     *
     * @param exercises    indexable exercise ids, including exam exercises
     * @param lectures     lecture ids
     * @param lectureUnits indexable lecture unit ids (text, online, attachment/video)
     * @param exams        exam ids
     * @param faqs         FAQ ids
     * @param channels     indexable channel ids (not archived, and course-wide or public)
     * @param pdfUnits     ids of attachment/video units whose attachment is a PDF, expected to have slide content;
     *                         empty for a course whose content the ingestion path does not process
     * @param videoUnits   ids of attachment/video units with a video source, expected to have transcript content;
     *                         empty for a course whose content the ingestion path does not process
     */
    public record ExpectedSets(Map<Long, Set<Long>> exercises, Map<Long, Set<Long>> lectures, Map<Long, Set<Long>> lectureUnits, Map<Long, Set<Long>> exams,
            Map<Long, Set<Long>> faqs, Map<Long, Set<Long>> channels, Map<Long, Set<Long>> pdfUnits, Map<Long, Set<Long>> videoUnits) {
    }

    /**
     * What the index actually holds, per course.
     *
     * @param metadataByCourse course id to entity type to the entity ids present in {@code SearchableEntities}
     * @param lastIngestedAt   course id to the most recent index-write time across its objects
     * @param slides           course id to the lecture unit ids holding slide content
     * @param transcript       course id to the lecture unit ids holding transcript content
     * @param segmentSummaries course id to the lecture unit ids holding aligned segment summaries
     * @param unitSummaries    course id to the lecture unit ids holding a unit summary
     */
    public record PresentSets(Map<Long, Map<String, Set<Long>>> metadataByCourse, Map<Long, Instant> lastIngestedAt, Map<Long, Set<Long>> slides, Map<Long, Set<Long>> transcript,
            Map<Long, Set<Long>> segmentSummaries, Map<Long, Set<Long>> unitSummaries) {
    }

    private final IngestionCoverageExpectedIdsRepository expectedIdsRepository;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final Optional<IrisSettingsApi> irisSettingsApi;

    private final IngestionCoverageWeaviateReadService weaviateReadService;

    public IngestionCoverageSetLoader(IngestionCoverageExpectedIdsRepository expectedIdsRepository, Optional<LectureRepositoryApi> lectureRepositoryApi,
            Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi, Optional<ExamRepositoryApi> examRepositoryApi, Optional<IrisSettingsApi> irisSettingsApi,
            IngestionCoverageWeaviateReadService weaviateReadService) {
        this.expectedIdsRepository = expectedIdsRepository;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
        this.examRepositoryApi = examRepositoryApi;
        this.irisSettingsApi = irisSettingsApi;
        this.weaviateReadService = weaviateReadService;
    }

    /**
     * Reads what the database expects to be indexed for the given courses. All courses are resolved per type in one
     * query, so this never degrades into a query per course.
     *
     * @param courseIds the courses to load
     * @return the expected id-sets, bucketed per course
     */
    public ExpectedSets loadExpected(Collection<Long> courseIds) {
        Map<Long, Set<Long>> exercises = bucket(expectedIdsRepository.findExerciseIdCourseIdPairsForCourses(courseIds));
        Map<Long, Set<Long>> faqs = bucket(expectedIdsRepository.findFaqIdCourseIdPairsForCourses(courseIds));
        Map<Long, Set<Long>> channels = bucket(expectedIdsRepository.findIndexableChannelIdCourseIdPairsForCourses(courseIds));
        Map<Long, Set<Long>> lectures = bucket(lectureRepositoryApi.map(api -> api.findLectureIdCourseIdPairsForCourses(courseIds)).orElse(List.of()));
        Map<Long, Set<Long>> exams = bucket(examRepositoryApi.map(api -> api.findExamIdCourseIdPairsForCourses(courseIds)).orElse(List.of()));
        Map<Long, Set<Long>> lectureUnits = bucket(lectureUnitRepositoryApi.map(api -> api.findIndexableUnitIdCourseIdPairsForCourses(courseIds)).orElse(List.of()));
        // Content lives in the Iris collections, which are only written for a course that has Iris enabled. Asking for
        // the other courses would report every one of their PDFs and videos missing for as long as Iris stays off.
        Collection<Long> irisEnabledCourseIds = courseIdsWithIrisEnabled(courseIds);
        Map<Long, Set<Long>> pdfUnits = irisEnabledCourseIds.isEmpty() ? Map.of()
                : bucket(lectureUnitRepositoryApi.map(api -> api.findUnitIdCourseIdPairsWithPdfAttachmentForCourses(irisEnabledCourseIds)).orElse(List.of()));
        Map<Long, Set<Long>> videoUnits = irisEnabledCourseIds.isEmpty() ? Map.of()
                : bucket(lectureUnitRepositoryApi.map(api -> api.findUnitIdCourseIdPairsWithVideoForCourses(irisEnabledCourseIds)).orElse(List.of()));
        return new ExpectedSets(exercises, lectures, lectureUnits, exams, faqs, channels, pdfUnits, videoUnits);
    }

    /**
     * The given courses that have Iris enabled, which are the only ones whose lecture content the ingestion path
     * processes. An absent {@link IrisSettingsApi} means the module is switched off for this instance, so nothing
     * writes the content collections at all and no course expects content.
     */
    private Collection<Long> courseIdsWithIrisEnabled(Collection<Long> courseIds) {
        return irisSettingsApi.map(api -> api.filterCourseIdsWithIrisEnabled(courseIds)).orElse(List.of());
    }

    /**
     * Reads what the index actually holds for the given courses.
     * <p>
     * Content is read for every course asked about, not only for those that still have lecture units. A course whose
     * last unit was deleted expects no content and would be skipped by that narrowing, yet the objects those units left
     * behind are precisely the orphans this data is diffed to find. The read service discovers which courses hold
     * content in one grouped aggregation per collection, so covering them all is not the per-course cost it looks like.
     *
     * @param courseIds the courses to read
     * @return the present id-sets, bucketed per course
     */
    public PresentSets loadPresent(Collection<Long> courseIds) {
        PresentMetadata metadata = weaviateReadService.readPresentMetadata(courseIds);
        Map<Long, Set<Long>> slides = weaviateReadService.readPresentContentUnitIds(LECTURES_COLLECTION, courseIds);
        Map<Long, Set<Long>> transcript = weaviateReadService.readPresentContentUnitIds(LECTURE_TRANSCRIPTIONS_COLLECTION, courseIds);
        Map<Long, Set<Long>> segmentSummaries = weaviateReadService.readPresentContentUnitIds(LECTURE_UNIT_SEGMENTS_COLLECTION, courseIds);
        Map<Long, Set<Long>> unitSummaries = weaviateReadService.readPresentContentUnitIds(LECTURE_UNITS_COLLECTION, courseIds);
        return new PresentSets(metadata.presentIdsByCourseAndType(), metadata.lastIngestedAtByCourse(), slides, transcript, segmentSummaries, unitSummaries);
    }

    /** Buckets flat (courseId, entityId) pairs into a per-course id-set. */
    private static Map<Long, Set<Long>> bucket(List<CourseEntityIdDTO> pairs) {
        return pairs.stream().collect(Collectors.groupingBy(CourseEntityIdDTO::courseId, Collectors.mapping(CourseEntityIdDTO::entityId, Collectors.toSet())));
    }
}
