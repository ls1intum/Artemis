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
 * the indexer actually writes a row. That equivalence is the correctness property; the query shapes are not.
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
     * @param pdfUnits     ids of attachment/video units whose attachment is a PDF, expected to have slide content
     * @param videoUnits   ids of attachment/video units with a video source, expected to have transcript content
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

    private final IngestionCoverageWeaviateReadService weaviateReadService;

    public IngestionCoverageSetLoader(IngestionCoverageExpectedIdsRepository expectedIdsRepository, Optional<LectureRepositoryApi> lectureRepositoryApi,
            Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi, Optional<ExamRepositoryApi> examRepositoryApi, IngestionCoverageWeaviateReadService weaviateReadService) {
        this.expectedIdsRepository = expectedIdsRepository;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
        this.examRepositoryApi = examRepositoryApi;
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
        Map<Long, Set<Long>> pdfUnits = bucket(lectureUnitRepositoryApi.map(api -> api.findUnitIdCourseIdPairsWithPdfAttachmentForCourses(courseIds)).orElse(List.of()));
        Map<Long, Set<Long>> videoUnits = bucket(lectureUnitRepositoryApi.map(api -> api.findUnitIdCourseIdPairsWithVideoForCourses(courseIds)).orElse(List.of()));
        return new ExpectedSets(exercises, lectures, lectureUnits, exams, faqs, channels, pdfUnits, videoUnits);
    }

    /**
     * Reads what the index actually holds for the given courses.
     *
     * @param courseIds        the courses to read metadata for
     * @param contentCourseIds the courses to read Iris content for, normally only those that have lecture units at all,
     *                             since the content aggregations are the heavier read and a course without units cannot
     *                             have content
     * @return the present id-sets, bucketed per course
     */
    public PresentSets loadPresent(Collection<Long> courseIds, Collection<Long> contentCourseIds) {
        PresentMetadata metadata = weaviateReadService.readPresentMetadata(courseIds);
        Map<Long, Set<Long>> slides = weaviateReadService.readPresentContentUnitIds(LECTURES_COLLECTION, contentCourseIds);
        Map<Long, Set<Long>> transcript = weaviateReadService.readPresentContentUnitIds(LECTURE_TRANSCRIPTIONS_COLLECTION, contentCourseIds);
        Map<Long, Set<Long>> segmentSummaries = weaviateReadService.readPresentContentUnitIds(LECTURE_UNIT_SEGMENTS_COLLECTION, contentCourseIds);
        Map<Long, Set<Long>> unitSummaries = weaviateReadService.readPresentContentUnitIds(LECTURE_UNITS_COLLECTION, contentCourseIds);
        return new PresentSets(metadata.presentIdsByCourseAndType(), metadata.lastIngestedAtByCourse(), slides, transcript, segmentSummaries, unitSummaries);
    }

    /** Buckets flat (courseId, entityId) pairs into a per-course id-set. */
    private static Map<Long, Set<Long>> bucket(List<CourseEntityIdDTO> pairs) {
        return pairs.stream().collect(Collectors.groupingBy(CourseEntityIdDTO::courseId, Collectors.mapping(CourseEntityIdDTO::entityId, Collectors.toSet())));
    }
}
