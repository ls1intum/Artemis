package de.tum.cit.aet.artemis.globalsearch.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.MissingContentDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.MissingEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService.IndexedKey;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

/**
 * Shared, read-only index-drift computation: for a course, it compares the entity keys the database expects to be
 * indexed against the keys actually present in the shared {@code SearchableEntities} Weaviate collection, and reports
 * per type how many are expected, present, missing (expected but not indexed), and orphaned (indexed but not in the
 * database). The comparison is by {@code (type, entity_id)} set difference, so it detects wrong-membership drift that a
 * bare count would hide (a course can hold the right number of rows but the wrong ones).
 * <p>
 * This service is not profile-gated, so both the scheduling-node census ({@link SearchableEntityCensusService}) and
 * the admin drift endpoint call it. It writes nothing.
 * <p>
 * Coverage: {@code expected} is enumerated for the types with an unambiguous per-course source (lecture, exam, faq, and
 * the course row). Other types are reported present-only until their database loaders exist (Ingestion Backfill and
 * Reconcile). {@code drifted} (indexed content differs from the database) is not computed here: it needs the achieved
 * content hash stamped onto the Weaviate row, which the Ingestion Durable Sync project adds.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class CourseIndexCensusService {

    private static final Logger log = LoggerFactory.getLogger(CourseIndexCensusService.class);

    /** The Iris collection (unprefixed) that holds ingested slide content, keyed by owning lecture unit id. */
    private static final String SLIDES_COLLECTION = "Lectures";

    /** The Iris collection (unprefixed) that holds ingested transcript content, keyed by owning lecture unit id. */
    private static final String TRANSCRIPTS_COLLECTION = "LectureTranscriptions";

    private final SearchableEntityWeaviateService searchableEntityWeaviateService;

    private final CourseRepository courseRepository;

    private final LectureRepository lectureRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final ExamRepository examRepository;

    private final FaqRepository faqRepository;

    private final ExerciseRepository exerciseRepository;

    private final ChannelRepository channelRepository;

    private final boolean irisEnabled;

    public CourseIndexCensusService(SearchableEntityWeaviateService searchableEntityWeaviateService, CourseRepository courseRepository, LectureRepository lectureRepository,
            LectureUnitRepository lectureUnitRepository, ExamRepository examRepository, FaqRepository faqRepository, ExerciseRepository exerciseRepository,
            ChannelRepository channelRepository, Environment environment) {
        this.searchableEntityWeaviateService = searchableEntityWeaviateService;
        this.courseRepository = courseRepository;
        this.lectureRepository = lectureRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.examRepository = examRepository;
        this.faqRepository = faqRepository;
        this.exerciseRepository = exerciseRepository;
        this.channelRepository = channelRepository;
        // Content collections are written by Iris only; when Iris is not enabled there is nothing to count and we skip
        // the extra Weaviate aggregates entirely.
        this.irisEnabled = new ArtemisConfigHelper().isIrisEnabled(environment);
    }

    /**
     * Computes the per-type drift for every course. Each course is measured independently, so a failure on one course
     * is logged and yields an empty entry rather than failing the whole census. Read-only.
     * <p>
     * This recomputes live from Weaviate on every call, which is acceptable for an admin view but scales with the
     * number of courses; persisting the latest census as a snapshot and serving that is the planned optimization.
     *
     * @return the per-course census, one entry per course
     */
    public List<CourseCensus> censusAllCourses() {
        List<CourseCensus> results = new ArrayList<>();
        for (Course course : courseRepository.findAll()) {
            boolean active = isActive(course);
            String startDate = course.getStartDate() != null ? course.getStartDate().toInstant().toString() : null;
            try {
                results.add(censusCourse(course.getId(), course.getTitle(), course.getSemester(), startDate, active));
            }
            catch (Exception e) {
                log.warn("Index census failed for course {}: {}", course.getId(), e.getMessage(), e);
                results.add(new CourseCensus(course.getId(), course.getTitle(), course.getSemester(), startDate, active, List.of(), List.of()));
            }
        }
        return results;
    }

    /**
     * Enumerates, by name, the lecture units that are expected to have ingested content but are missing it: a unit that
     * has a PDF but no slide chunks ({@code slides}), or a video but no transcript segments ({@code transcript}). This
     * is the per-unit breakdown behind the course-level PDF/video content counts, so an admin can see exactly which
     * units still need ingestion instead of only a count. Returns an empty list when Iris is not enabled. Read-only.
     *
     * @param courseId the course id
     * @return the per-unit content gaps, sorted by kind then unit id
     */
    public List<MissingContentDTO> contentGapsForCourse(long courseId) {
        if (!irisEnabled) {
            return List.of();
        }
        Set<Long> unitsWithPdf = lectureUnitRepository.findUnitIdsWithAttachmentByCourseId(courseId);
        Set<Long> unitsWithSlides = searchableEntityWeaviateService.listExternalUnitIdsForCourse(SLIDES_COLLECTION, courseId);
        Set<Long> unitsWithVideo = lectureUnitRepository.findUnitIdsWithVideoByCourseId(courseId);
        Set<Long> unitsWithTranscript = searchableEntityWeaviateService.listExternalUnitIdsForCourse(TRANSCRIPTS_COLLECTION, courseId);

        Set<Long> missingSlides = unitsWithPdf.stream().filter(id -> !unitsWithSlides.contains(id)).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> missingTranscript = unitsWithVideo.stream().filter(id -> !unitsWithTranscript.contains(id)).collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> allMissing = new LinkedHashSet<>(missingSlides);
        allMissing.addAll(missingTranscript);
        Map<Long, String> titles = resolveTitles(SearchableEntitySchema.TypeValues.LECTURE_UNIT, courseId, allMissing);

        List<MissingContentDTO> gaps = new ArrayList<>();
        for (Long id : missingSlides) {
            gaps.add(new MissingContentDTO(id, titles.get(id), "slides"));
        }
        for (Long id : missingTranscript) {
            gaps.add(new MissingContentDTO(id, titles.get(id), "transcript"));
        }
        gaps.sort(Comparator.comparing(MissingContentDTO::kind).thenComparingLong(MissingContentDTO::lectureUnitId));
        return gaps;
    }

    /** A course counts as active when it has no end date or its end date is still in the future. */
    private static boolean isActive(Course course) {
        return course.getEndDate() == null || course.getEndDate().isAfter(ZonedDateTime.now());
    }

    /**
     * Computes the per-type drift for a single course.
     *
     * @param courseId the course id
     * @return the per-course census result (title and semester are null on this path)
     */
    public CourseCensus censusCourse(long courseId) {
        return censusCourse(courseId, null, null, null, true);
    }

    /**
     * Enumerates, by name, the entities the database expects to be indexed for a course but that are absent from the
     * {@code SearchableEntities} Weaviate collection: the missing side of the census, resolved to titles. It is computed
     * as the per-type set difference {@code expected - present} (the same rule the census counts use), and only the
     * missing ids are resolved to titles, so no full course load happens for a course that is already complete. Types
     * without a database source (posts, answer posts) are reported present-only by the census and so never appear here.
     * Read-only.
     *
     * @param courseId the course id
     * @return the missing entities, sorted by type then id
     */
    public List<MissingEntityDTO> missingEntitiesForCourse(long courseId) {
        Map<String, Set<Long>> presentByType = new HashMap<>();
        for (IndexedKey key : searchableEntityWeaviateService.listIndexedKeysForCourse(courseId)) {
            presentByType.computeIfAbsent(key.type(), type -> new HashSet<>()).add(key.entityId());
        }
        Map<String, Set<Long>> expectedByType = expectedKeysByType(courseId);

        List<MissingEntityDTO> missing = new ArrayList<>();
        for (Map.Entry<String, Set<Long>> entry : expectedByType.entrySet()) {
            String type = entry.getKey();
            Set<Long> present = presentByType.getOrDefault(type, Set.of());
            Set<Long> missingIds = entry.getValue().stream().filter(id -> !present.contains(id)).collect(Collectors.toCollection(LinkedHashSet::new));
            if (missingIds.isEmpty()) {
                continue;
            }
            Map<Long, String> titles = resolveTitles(type, courseId, missingIds);
            for (Long id : missingIds) {
                missing.add(new MissingEntityDTO(type, id, titles.get(id)));
            }
        }
        missing.sort(Comparator.comparing(MissingEntityDTO::type).thenComparingLong(MissingEntityDTO::entityId));
        return missing;
    }

    private CourseCensus censusCourse(long courseId, String courseTitle, String semester, String startDate, boolean active) {
        Map<String, Set<Long>> presentByType = new HashMap<>();
        for (IndexedKey key : searchableEntityWeaviateService.listIndexedKeysForCourse(courseId)) {
            presentByType.computeIfAbsent(key.type(), type -> new HashSet<>()).add(key.entityId());
        }
        Map<String, Set<Long>> expectedByType = expectedKeysByType(courseId);

        Set<String> allTypes = new TreeSet<>();
        allTypes.addAll(expectedByType.keySet());
        allTypes.addAll(presentByType.keySet());

        List<TypeCensus> types = new ArrayList<>();
        for (String type : allTypes) {
            types.add(TypeCensus.of(type, expectedByType.get(type), presentByType.getOrDefault(type, Set.of())));
        }
        return new CourseCensus(courseId, courseTitle, semester, startDate, active, types, contentCensus(courseId));
    }

    /**
     * Computes the lecture-content completeness for a course at the file level: of the units that have a PDF, how many
     * have slide content ingested; of the units that have a video, how many have transcript content ingested. The
     * expected set comes from Artemis (the PDFs and videos live here), and the present set is the distinct units that
     * actually have content in the Iris collections, so this is a real indexed-vs-expected metric, not a raw count.
     * Returns an empty list when Iris is not enabled, so the frontend hides the content columns.
     */
    private List<ContentCensus> contentCensus(long courseId) {
        if (!irisEnabled) {
            return List.of();
        }
        Set<Long> unitsWithPdf = lectureUnitRepository.findUnitIdsWithAttachmentByCourseId(courseId);
        Set<Long> unitsWithSlides = searchableEntityWeaviateService.listExternalUnitIdsForCourse(SLIDES_COLLECTION, courseId);
        Set<Long> unitsWithVideo = lectureUnitRepository.findUnitIdsWithVideoByCourseId(courseId);
        Set<Long> unitsWithTranscript = searchableEntityWeaviateService.listExternalUnitIdsForCourse(TRANSCRIPTS_COLLECTION, courseId);
        return List.of(ContentCensus.of("pdf", unitsWithPdf, unitsWithSlides), ContentCensus.of("video", unitsWithVideo, unitsWithTranscript));
    }

    /**
     * Enumerates the expected {@code (type -> entity ids)} the database says should be indexed for a course. Each set is
     * built to match exactly the condition under which that type triggers an upsert to {@code SearchableEntities}, so the
     * census measures completeness against the real indexing rules and not an approximation:
     * <ul>
     * <li>lecture, exam, faq, course: every one in the course,</li>
     * <li>exercise: course exercises plus exam exercises (resolved via exercise group -> exam -> course), matching
     * {@code getCourseViaExerciseGroupOrCourseMember()},</li>
     * <li>lecture unit: only the indexable subtypes (text, online, attachment/video), matching
     * {@code LectureUnitSearchableEntityDTO.isIndexable},</li>
     * <li>channel: non-archived channels that are course-wide or public, matching
     * {@code ChannelSearchableEntityDTO.isIndexable}.</li>
     * </ul>
     * Posts and answer posts are not enumerated here yet (they are indexed per non-archived public channel and can be
     * very numerous); they remain present-only until their loader is added. Only entity ids are read, so no lazy
     * relationship is touched.
     */
    private Map<String, Set<Long>> expectedKeysByType(long courseId) {
        Map<String, Set<Long>> expected = new HashMap<>();
        expected.put(SearchableEntitySchema.TypeValues.LECTURE, new HashSet<>(lectureRepository.findLectureIdsByCourseId(courseId)));
        expected.put(SearchableEntitySchema.TypeValues.EXAM, examRepository.findByCourseId(courseId).stream().map(Exam::getId).collect(Collectors.toSet()));
        expected.put(SearchableEntitySchema.TypeValues.FAQ, faqRepository.findAllByCourseId(courseId).stream().map(Faq::getId).collect(Collectors.toSet()));
        expected.put(SearchableEntitySchema.TypeValues.COURSE, Set.of(courseId));
        expected.put(SearchableEntitySchema.TypeValues.EXERCISE, exerciseRepository.findAllExerciseIdsByCourseIdIncludingExam(courseId));
        expected.put(SearchableEntitySchema.TypeValues.LECTURE_UNIT, lectureUnitRepository.findIndexableUnitIdsByCourseId(courseId));
        expected.put(SearchableEntitySchema.TypeValues.CHANNEL, indexableChannelIds(courseId));
        return expected;
    }

    /**
     * Resolves the display titles for a set of missing entity ids of one type, using the same title source the indexing
     * DTO for that type uses, so a missing item reads with the name it would have once indexed. Only the missing ids are
     * loaded. Types without a database source return no titles.
     *
     * @param type     the entity type discriminator
     * @param courseId the course id (used for the single course row)
     * @param ids      the ids to resolve
     * @return a map from entity id to title (ids that could not be resolved are simply absent)
     */
    private Map<Long, String> resolveTitles(String type, long courseId, Set<Long> ids) {
        Map<Long, String> titles = new HashMap<>();
        switch (type) {
            case SearchableEntitySchema.TypeValues.LECTURE -> lectureRepository.findAllById(ids).forEach(lecture -> titles.put(lecture.getId(), lecture.getTitle()));
            case SearchableEntitySchema.TypeValues.EXAM -> examRepository.findAllById(ids).forEach(exam -> titles.put(exam.getId(), exam.getTitle()));
            case SearchableEntitySchema.TypeValues.FAQ -> faqRepository.findAllById(ids).forEach(faq -> titles.put(faq.getId(), faq.getQuestionTitle()));
            case SearchableEntitySchema.TypeValues.COURSE -> courseRepository.findById(courseId).ifPresent(course -> titles.put(course.getId(), course.getTitle()));
            case SearchableEntitySchema.TypeValues.EXERCISE -> exerciseRepository.findAllById(ids).forEach(exercise -> titles.put(exercise.getId(), exercise.getTitle()));
            case SearchableEntitySchema.TypeValues.LECTURE_UNIT -> lectureUnitRepository.findAllById(ids).forEach(unit -> titles.put(unit.getId(), unit.getName()));
            case SearchableEntitySchema.TypeValues.CHANNEL -> channelRepository.findAllById(ids).forEach(channel -> titles.put(channel.getId(), channel.getName()));
            default -> {
                // Types without a database source (posts, answer posts) are never enumerated in expectedKeysByType.
            }
        }
        return titles;
    }

    /**
     * The ids of the channels in a course that are indexed: non-archived channels that are course-wide or public. This
     * reproduces {@code ChannelSearchableEntityDTO.isIndexable} in Java rather than SQL, so the two cannot drift.
     */
    private Set<Long> indexableChannelIds(long courseId) {
        return channelRepository.findChannelsByCourseId(courseId).stream().filter(channel -> !channel.getIsArchived() && (channel.getIsCourseWide() || channel.getIsPublic()))
                .map(Channel::getId).collect(Collectors.toSet());
    }

    /**
     * The census for one course.
     *
     * @param courseId    the course id
     * @param courseTitle the course title, or null when not resolved
     * @param semester    the course semester, or null when not resolved
     * @param startDate   the course start date as an ISO-8601 instant string, or null when not set
     * @param active      whether the course is active (no end date, or end date in the future)
     * @param types       the per-type entity-metadata census entries
     * @param content     the Iris lecture-content completeness (empty when Iris is not enabled)
     */
    public record CourseCensus(long courseId, String courseTitle, String semester, String startDate, boolean active, List<TypeCensus> types, List<ContentCensus> content) {
    }

    /**
     * File-level lecture-content completeness for a course: of the units that have this kind of file (pdf or video),
     * how many have content ingested into the corresponding Iris collection.
     *
     * @param key      the content key ({@code pdf} or {@code video}) used for the frontend label
     * @param expected the number of units that have this file kind in Artemis
     * @param present  the number of those units that have content ingested in Weaviate
     * @param missing  units that have the file but no ingested content ({@code expected - present})
     */
    public record ContentCensus(String key, int expected, int present, int missing) {

        static ContentCensus of(String key, Set<Long> expectedUnitIds, Set<Long> presentUnitIds) {
            int present = (int) expectedUnitIds.stream().filter(presentUnitIds::contains).count();
            return new ContentCensus(key, expectedUnitIds.size(), present, expectedUnitIds.size() - present);
        }
    }

    /**
     * Census for one entity type within a course. {@code expected}, {@code missing} and {@code orphaned} are null for
     * types without a database source yet (reported present-only).
     *
     * @param type     the entity type discriminator
     * @param expected the number of keys the database expects, or null when not computed for this type
     * @param present  the number of keys currently indexed in Weaviate
     * @param missing  expected keys absent from Weaviate, or null when {@code expected} is null
     * @param orphaned indexed keys with no database source, or null when {@code expected} is null
     */
    public record TypeCensus(String type, Integer expected, int present, Integer missing, Integer orphaned) {

        static TypeCensus of(String type, Set<Long> expectedIds, Set<Long> presentIds) {
            if (expectedIds == null) {
                return new TypeCensus(type, null, presentIds.size(), null, null);
            }
            int missing = (int) expectedIds.stream().filter(id -> !presentIds.contains(id)).count();
            int orphaned = (int) presentIds.stream().filter(id -> !expectedIds.contains(id)).count();
            return new TypeCensus(type, expectedIds.size(), presentIds.size(), missing, orphaned);
        }
    }
}
