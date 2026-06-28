package de.tum.cit.aet.artemis.globalsearch.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ChannelSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.CourseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExamSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExerciseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.FaqSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.LectureSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.LectureUnitSearchableEntityDTO;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

/**
 * Re-indexes all searchable entities into Weaviate from the database.
 * Invoked after a schema migration that drops and recreates the collection (e.g. V1→V2),
 * or via the admin re-index endpoint.
 * <p>
 * Uses synchronous, sequential upserts rather than the {@code @Async} helpers so that only one
 * Ollama embedding request is in-flight at a time. The {@code @Async} path dispatches all upserts
 * concurrently, saturating Ollama's queue and causing gRPC timeouts on slow embedding models.
 * <p>
 * Posts and answer posts are excluded — they re-index on next write.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityReindexService {

    private static final Logger log = LoggerFactory.getLogger(SearchableEntityReindexService.class);

    private final SearchableEntityWeaviateService weaviateService;

    private final CourseRepository courseRepository;

    private final ExerciseRepository exerciseRepository;

    private final ExamRepository examRepository;

    private final FaqRepository faqRepository;

    private final ChannelRepository channelRepository;

    private final Optional<LectureRepository> lectureRepository;

    private final Optional<LectureUnitRepository> lectureUnitRepository;

    public SearchableEntityReindexService(SearchableEntityWeaviateService weaviateService, CourseRepository courseRepository, ExerciseRepository exerciseRepository,
            ExamRepository examRepository, FaqRepository faqRepository, ChannelRepository channelRepository, Optional<LectureRepository> lectureRepository,
            Optional<LectureUnitRepository> lectureUnitRepository) {
        this.weaviateService = weaviateService;
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
        this.examRepository = examRepository;
        this.faqRepository = faqRepository;
        this.channelRepository = channelRepository;
        this.lectureRepository = lectureRepository;
        this.lectureUnitRepository = lectureUnitRepository;
    }

    /**
     * Re-indexes all searchable entities from the database into Weaviate, sequentially.
     * Each upsert is synchronous to avoid overloading the embedding backend (Ollama).
     * Failures on individual entities are logged and skipped.
     *
     * @return a one-line summary of entity counts indexed per type
     */
    @Transactional(readOnly = true)
    public String reindexAll() {
        log.info("Starting full Weaviate re-index (sequential upserts)...");
        weaviateService.deleteAllSearchableEntities();
        int courses = reindexCourses();
        int exercises = reindexExercises();
        int exams = reindexExams();
        int faqs = reindexFaqs();
        int channels = reindexChannels();
        int lectures = reindexLectures();
        int lectureUnits = reindexLectureUnits();
        String summary = String.format("courses=%d exercises=%d exams=%d faqs=%d channels=%d lectures=%d lectureUnits=%d", courses, exercises, exams, faqs, channels, lectures,
                lectureUnits);
        log.info("Full re-index complete: {}", summary);
        return summary;
    }

    private int reindexCourses() {
        int count = 0;
        for (var course : courseRepository.findAll()) {
            try {
                var dto = CourseSearchableEntityDTO.fromCourse(course);
                weaviateService.upsertRowSync(SearchableEntitySchema.TypeValues.COURSE, dto.courseId(), dto.toPropertyMap());
                count++;
            }
            catch (Exception e) {
                log.warn("Re-index: skipping course {} — {}", course.getId(), e.getMessage());
            }
        }
        log.info("Re-index: {} courses done", count);
        return count;
    }

    private int reindexExercises() {
        int count = 0;
        for (Exercise exercise : exerciseRepository.findAllForSearchReindex()) {
            try {
                var dto = ExerciseSearchableEntityDTO.fromExercise(exercise);
                weaviateService.upsertRowSync(SearchableEntitySchema.TypeValues.EXERCISE, dto.exerciseId(), dto.toPropertyMap());
                count++;
            }
            catch (Exception e) {
                log.warn("Re-index: skipping exercise {} — {}", exercise.getId(), e.getMessage());
            }
        }
        log.info("Re-index: {} exercises done", count);
        return count;
    }

    private int reindexExams() {
        int count = 0;
        for (var exam : examRepository.findAllForSearchReindex()) {
            try {
                var dto = ExamSearchableEntityDTO.fromExam(exam);
                weaviateService.upsertRowSync(SearchableEntitySchema.TypeValues.EXAM, dto.examId(), dto.toPropertyMap());
                count++;
            }
            catch (Exception e) {
                log.warn("Re-index: skipping exam {} — {}", exam.getId(), e.getMessage());
            }
        }
        log.info("Re-index: {} exams done", count);
        return count;
    }

    private int reindexFaqs() {
        int count = 0;
        for (Faq faq : faqRepository.findAllForSearchReindex()) {
            try {
                var dto = FaqSearchableEntityDTO.fromFaq(faq);
                weaviateService.upsertRowSync(SearchableEntitySchema.TypeValues.FAQ, dto.faqId(), dto.toPropertyMap());
                count++;
            }
            catch (Exception e) {
                log.warn("Re-index: skipping FAQ {} — {}", faq.getId(), e.getMessage());
            }
        }
        log.info("Re-index: {} FAQs done", count);
        return count;
    }

    private int reindexChannels() {
        int count = 0;
        for (Channel channel : channelRepository.findAllForSearchReindex()) {
            try {
                if (!ChannelSearchableEntityDTO.isIndexable(channel)) {
                    continue;
                }
                var dto = ChannelSearchableEntityDTO.fromChannel(channel);
                weaviateService.upsertRowSync(SearchableEntitySchema.TypeValues.CHANNEL, dto.channelId(), dto.toPropertyMap());
                count++;
            }
            catch (Exception e) {
                log.warn("Re-index: skipping channel {} — {}", channel.getId(), e.getMessage());
            }
        }
        log.info("Re-index: {} channels done", count);
        return count;
    }

    private int reindexLectures() {
        if (lectureRepository.isEmpty()) {
            log.info("Re-index: lecture module not available, skipping lectures");
            return 0;
        }
        int count = 0;
        for (Lecture lecture : lectureRepository.get().findAllForSearchReindex()) {
            try {
                var dto = LectureSearchableEntityDTO.fromLecture(lecture);
                weaviateService.upsertRowSync(SearchableEntitySchema.TypeValues.LECTURE, dto.lectureId(), dto.toPropertyMap());
                count++;
            }
            catch (Exception e) {
                log.warn("Re-index: skipping lecture {} — {}", lecture.getId(), e.getMessage());
            }
        }
        log.info("Re-index: {} lectures done", count);
        return count;
    }

    private int reindexLectureUnits() {
        if (lectureUnitRepository.isEmpty()) {
            log.info("Re-index: lecture module not available, skipping lecture units");
            return 0;
        }
        int count = 0;
        for (LectureUnit unit : lectureUnitRepository.get().findAllForSearchReindex()) {
            try {
                var dto = LectureUnitSearchableEntityDTO.fromLectureUnit(unit);
                weaviateService.upsertRowSync(SearchableEntitySchema.TypeValues.LECTURE_UNIT, dto.lectureUnitId(), dto.toPropertyMap());
                count++;
            }
            catch (IllegalArgumentException e) {
                // ExerciseUnit and other unsupported types — expected, not an error
                log.debug("Re-index: skipping unsupported lecture unit type {} ({})", unit.getId(), unit.getClass().getSimpleName());
            }
            catch (Exception e) {
                log.warn("Re-index: skipping lecture unit {} — {}", unit.getId(), e.getMessage());
            }
        }
        log.info("Re-index: {} lecture units done", count);
        return count;
    }
}
