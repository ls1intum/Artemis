package de.tum.cit.aet.artemis.globalsearch.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.CourseIndexDriftDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.TypeDriftDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;

/**
 * Computes per-course index drift live: for each entity type, the number of rows actually indexed in
 * Weaviate ({@code present}) versus the number that should be indexed ({@code expected}), read fresh
 * from the database on every call. Read-only; nothing is persisted.
 * <p>
 * {@code expected} is provided for the types with an unambiguous per-course source (lecture, exam, faq,
 * and the course row itself). Exercise and lecture unit counts need dedicated handling (exam exercises,
 * lecture-to-unit traversal) and are reported {@code present}-only for now, with {@code expected} null.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class CourseIndexDriftService {

    /**
     * Types reported, in the order the admin dashboard shows them.
     */
    private static final List<String> TYPES = List.of(SearchableEntitySchema.TypeValues.EXERCISE, SearchableEntitySchema.TypeValues.LECTURE,
            SearchableEntitySchema.TypeValues.LECTURE_UNIT, SearchableEntitySchema.TypeValues.EXAM, SearchableEntitySchema.TypeValues.FAQ,
            SearchableEntitySchema.TypeValues.CHANNEL, SearchableEntitySchema.TypeValues.COURSE, SearchableEntitySchema.TypeValues.POST,
            SearchableEntitySchema.TypeValues.ANSWER_POST);

    private final SearchableEntityCountService countService;

    private final LectureRepository lectureRepository;

    private final ExamRepository examRepository;

    private final FaqRepository faqRepository;

    public CourseIndexDriftService(SearchableEntityCountService countService, LectureRepository lectureRepository, ExamRepository examRepository, FaqRepository faqRepository) {
        this.countService = countService;
        this.lectureRepository = lectureRepository;
        this.examRepository = examRepository;
        this.faqRepository = faqRepository;
    }

    /**
     * Computes the per-type indexed-vs-expected drift for a course.
     *
     * @param courseId the course id
     * @return the drift snapshot
     */
    public CourseIndexDriftDTO getDrift(long courseId) {
        List<TypeDriftDTO> types = new ArrayList<>();
        for (String type : TYPES) {
            long present = countService.countIndexed(courseId, type);
            types.add(new TypeDriftDTO(type, present, expectedCount(courseId, type)));
        }
        return new CourseIndexDriftDTO(courseId, types);
    }

    /**
     * The number of rows that should be indexed for a type in a course, or null when not computed yet.
     */
    private Long expectedCount(long courseId, String type) {
        return switch (type) {
            case SearchableEntitySchema.TypeValues.LECTURE -> (long) lectureRepository.findLectureIdsByCourseId(courseId).size();
            case SearchableEntitySchema.TypeValues.EXAM -> (long) examRepository.findByCourseId(courseId).size();
            case SearchableEntitySchema.TypeValues.FAQ -> (long) faqRepository.findAllByCourseId(courseId).size();
            case SearchableEntitySchema.TypeValues.COURSE -> 1L;
            default -> null;
        };
    }
}
