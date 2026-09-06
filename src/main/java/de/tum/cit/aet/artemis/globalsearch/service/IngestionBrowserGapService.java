package de.tum.cit.aet.artemis.globalsearch.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.MissingContentDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.MissingEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageSetLoader.ExpectedSets;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageSetLoader.PresentSets;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * Names what one course is missing from the index: the entities the database expects but the index does not hold, and
 * the lecture units whose slide or transcript content was never ingested.
 * <p>
 * The coverage matrix reports these as counts. This turns the same counts into names, and it does so from the same
 * {@link IngestionCoverageSetLoader} sets, so a number in the matrix and the list behind it are two views of one
 * computation rather than two computations that ought to agree.
 * <p>
 * Titles are resolved only for the ids that are actually missing. A course is not loaded to name a gap in it.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class IngestionBrowserGapService {

    /** The content kinds a lecture unit can be missing, matching the browser's content keys. */
    private static final String KIND_SLIDES = "slides";

    private static final String KIND_TRANSCRIPT = "transcript";

    private final ExerciseRepository exerciseRepository;

    private final FaqRepository faqRepository;

    private final ChannelRepository channelRepository;

    private final CourseRepository courseRepository;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final ArtemisConfigHelper artemisConfigHelper = new ArtemisConfigHelper();

    private final Environment environment;

    public IngestionBrowserGapService(ExerciseRepository exerciseRepository, FaqRepository faqRepository, ChannelRepository channelRepository, CourseRepository courseRepository,
            Optional<LectureRepositoryApi> lectureRepositoryApi, Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi, Optional<ExamRepositoryApi> examRepositoryApi,
            Environment environment) {
        this.exerciseRepository = exerciseRepository;
        this.faqRepository = faqRepository;
        this.channelRepository = channelRepository;
        this.courseRepository = courseRepository;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
        this.examRepositoryApi = examRepositoryApi;
        this.environment = environment;
    }

    /**
     * The entities the database expects to be indexed for a course that the index does not hold, resolved to their
     * titles and sorted by type then id.
     * <p>
     *
     * @param courseId the course to inspect
     * @param expected what the database expects indexed, already loaded
     * @param present  what the index holds, already loaded
     * @return the missing entities, named
     */
    public List<MissingEntityDTO> missingEntities(long courseId, ExpectedSets expected, PresentSets present) {
        Map<String, Set<Long>> presentByType = present.metadataByCourse().getOrDefault(courseId, Map.of());

        List<MissingEntityDTO> missing = new ArrayList<>();
        missing.addAll(resolve(SearchableEntitySchema.TypeValues.EXERCISE, expected.exercises().get(courseId), presentByType, this::exerciseTitles));
        missing.addAll(resolve(SearchableEntitySchema.TypeValues.LECTURE, expected.lectures().get(courseId), presentByType, this::lectureTitles));
        missing.addAll(resolve(SearchableEntitySchema.TypeValues.LECTURE_UNIT, expected.lectureUnits().get(courseId), presentByType, this::lectureUnitTitles));
        missing.addAll(resolve(SearchableEntitySchema.TypeValues.EXAM, expected.exams().get(courseId), presentByType, this::examTitles));
        missing.addAll(resolve(SearchableEntitySchema.TypeValues.FAQ, expected.faqs().get(courseId), presentByType, this::faqTitles));
        missing.addAll(resolve(SearchableEntitySchema.TypeValues.CHANNEL, expected.channels().get(courseId), presentByType, this::channelTitles));
        // The course itself is always expected to be indexed as a single object.
        missing.addAll(resolve(SearchableEntitySchema.TypeValues.COURSE, Set.of(courseId), presentByType, this::courseTitles));

        missing.sort(Comparator.comparing(MissingEntityDTO::type).thenComparingLong(MissingEntityDTO::entityId));
        return missing;
    }

    /**
     * The lecture units of a course that should have ingested content but do not: a PDF attachment with no slides
     * indexed, or a video source with no transcript indexed. Sorted by kind then unit id.
     * <p>
     * Empty when Iris is not enabled. Without Iris nothing ingests lecture content in the first place, so reporting
     * every unit with a PDF as a gap would describe a feature that is switched off rather than a problem to fix.
     *
     * @param courseId the course to inspect
     * @param expected what the database expects indexed, already loaded
     * @param present  what the index holds, already loaded
     * @return the per-unit content gaps, named
     */
    public List<MissingContentDTO> contentGaps(long courseId, ExpectedSets expected, PresentSets present) {
        if (!artemisConfigHelper.isIrisEnabled(environment)) {
            return List.of();
        }
        Set<Long> missingSlides = difference(expected.pdfUnits().get(courseId), present.slides().get(courseId));
        Set<Long> missingTranscript = difference(expected.videoUnits().get(courseId), present.transcript().get(courseId));
        if (missingSlides.isEmpty() && missingTranscript.isEmpty()) {
            return List.of();
        }

        Set<Long> allMissingUnits = new HashSet<>(missingSlides);
        allMissingUnits.addAll(missingTranscript);
        Map<Long, String> titles = lectureUnitTitles(allMissingUnits);

        List<MissingContentDTO> gaps = new ArrayList<>();
        missingSlides.forEach(unitId -> gaps.add(new MissingContentDTO(unitId, titles.get(unitId), KIND_SLIDES)));
        missingTranscript.forEach(unitId -> gaps.add(new MissingContentDTO(unitId, titles.get(unitId), KIND_TRANSCRIPT)));

        gaps.sort(Comparator.comparing(MissingContentDTO::kind).thenComparingLong(MissingContentDTO::lectureUnitId));
        return gaps;
    }

    /**
     * Diffs one type's expected ids against what the index holds and names whatever is absent. The title lookup runs only
     * when something is missing, so a fully indexed type costs no query at all.
     */
    private List<MissingEntityDTO> resolve(String type, Set<Long> expectedIds, Map<String, Set<Long>> presentByType, Function<Collection<Long>, Map<Long, String>> titleLookup) {
        Set<Long> missingIds = difference(expectedIds, presentByType.get(type));
        if (missingIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> titles = titleLookup.apply(missingIds);
        return missingIds.stream().map(id -> new MissingEntityDTO(type, id, titles.get(id))).toList();
    }

    /** Expected minus present, treating either side's absence as an empty set. */
    private static Set<Long> difference(Set<Long> expected, Set<Long> present) {
        if (expected == null || expected.isEmpty()) {
            return Set.of();
        }
        if (present == null || present.isEmpty()) {
            return Set.copyOf(expected);
        }
        return expected.stream().filter(id -> !present.contains(id)).collect(Collectors.toSet());
    }

    // ----- Title sources, one per type -----

    private Map<Long, String> exerciseTitles(Collection<Long> ids) {
        return titleMap(exerciseRepository.findAllById(ids), Exercise::getId, Exercise::getTitle);
    }

    private Map<Long, String> faqTitles(Collection<Long> ids) {
        return titleMap(faqRepository.findAllById(ids), Faq::getId, Faq::getQuestionTitle);
    }

    private Map<Long, String> channelTitles(Collection<Long> ids) {
        return titleMap(channelRepository.findAllById(ids), Channel::getId, Channel::getName);
    }

    private Map<Long, String> courseTitles(Collection<Long> ids) {
        return titleMap(courseRepository.findAllById(ids), Course::getId, Course::getTitle);
    }

    private Map<Long, String> lectureTitles(Collection<Long> ids) {
        return lectureRepositoryApi.map(api -> titleMap(api.findAllById(ids), Lecture::getId, Lecture::getTitle)).orElseGet(Map::of);
    }

    private Map<Long, String> lectureUnitTitles(Collection<Long> ids) {
        return lectureUnitRepositoryApi.map(api -> titleMap(api.findAllByIdsWithLecture(ids), LectureUnit::getId, LectureUnit::getName)).orElseGet(Map::of);
    }

    private Map<Long, String> examTitles(Collection<Long> ids) {
        return examRepositoryApi.map(api -> titleMap(api.findAllById(ids), Exam::getId, Exam::getTitle)).orElseGet(Map::of);
    }

    /**
     * Indexes entities by id to their display name. Entities without an id or without a name are dropped rather than
     * mapped to null, so a caller reading a missing key gets null once instead of having to distinguish two kinds of
     * absence.
     */
    private static <T> Map<Long, String> titleMap(Collection<T> entities, Function<T, Long> idOf, Function<T, String> titleOf) {
        Map<Long, String> titles = new HashMap<>();
        for (T entity : entities) {
            Long id = idOf.apply(entity);
            String title = titleOf.apply(entity);
            if (id != null && title != null) {
                titles.put(id, title);
            }
        }
        return titles;
    }
}
