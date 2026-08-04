package de.tum.cit.aet.artemis.globalsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.dto.CourseEntityIdDTO;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

/**
 * Verifies the bulk (course id, entity id) expected-set queries that feed the ingestion-observability coverage check.
 * <p>
 * The coverage recompute compares, per course, the entities the database expects to be present in the search index
 * against the objects actually stored in Weaviate. This test pins the "expected" side: that each query returns exactly
 * the ids the live indexing / content-processing path would index, attributed to the right course, and that one call
 * resolves a set of courses at once (so the recompute never fans out into a query per course).
 * <p>
 * It lives beside the search-index code these queries feed, mirroring how the sibling search query
 * ({@code ExerciseRepository#findAllForSearchMigrationWithCourseAndExam}) is covered by the migration integration test
 * rather than by a repository test inside the exercise or lecture module.
 */
class IngestionCoverageExpectedSetTest extends AbstractSpringIntegrationIndependentBatchTest {

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private long courseAId;

    private long courseBId;

    // Course A lecture units.
    private LectureUnit textUnit;

    private LectureUnit onlineUnit;

    private AttachmentVideoUnit videoUnit;

    private AttachmentVideoUnit blankVideoUnit;

    private AttachmentVideoUnit pdfUnit;

    private AttachmentVideoUnit nonPdfUnit;

    private LectureUnit exerciseUnit;

    // Course A exercises.
    private Exercise courseExerciseA;

    private Exercise examExerciseA;

    // Course B entities (a second course, to prove bulk resolution and correct per-row course attribution).
    private LectureUnit textUnitB;

    private Exercise courseExerciseB;

    @BeforeEach
    void setUp() {
        ZonedDateTime past = ZonedDateTime.now().minusDays(1);
        ZonedDateTime future = ZonedDateTime.now().plusDays(1);
        ZonedDateTime farFuture = ZonedDateTime.now().plusDays(2);

        Course courseA = courseUtilService.createCourse();
        courseAId = courseA.getId();
        Lecture lectureA = lectureUtilService.createLecture(courseA);

        textUnit = lectureUtilService.createTextUnit(lectureA);
        onlineUnit = lectureUtilService.createOnlineUnit(lectureA);
        videoUnit = seedUnitWithVideoSource(lectureA, "https://video.example/lecture-a");
        // A whitespace-only video source is treated as absent by the content-processing trigger, so it must not appear in
        // the video expected set.
        blankVideoUnit = seedUnitWithVideoSource(lectureA, "   ");
        pdfUnit = seedUnitWithAttachmentLink(lectureA, "attachments/attachment-unit/slides.pdf");
        // An attachment whose link does not end in .pdf is still an indexable unit, but its slides are not ingested.
        nonPdfUnit = seedUnitWithAttachmentLink(lectureA, "attachments/attachment-unit/notes.txt");

        courseExerciseA = exerciseRepository.save(TextExerciseFactory.generateTextExercise(past, future, farFuture, courseA));
        Exam examA = examUtilService.addExamWithExerciseGroup(courseA, true);
        examExerciseA = exerciseRepository.save(TextExerciseFactory.generateTextExerciseForExam(examA.getExerciseGroups().getFirst()));
        // An exercise unit is a lecture-unit subtype, but it is covered by the exercise expected set, not the lecture-unit
        // one, so it must be excluded from the indexable-unit query.
        exerciseUnit = lectureUtilService.createExerciseUnit(courseExerciseA, lectureA);

        Course courseB = courseUtilService.createCourse();
        courseBId = courseB.getId();
        Lecture lectureB = lectureUtilService.createLecture(courseB);
        textUnitB = lectureUtilService.createTextUnit(lectureB);
        courseExerciseB = exerciseRepository.save(TextExerciseFactory.generateTextExercise(past, future, farFuture, courseB));
    }

    @Test
    void findsIndexableUnitsExcludingExerciseUnits() {
        List<CourseEntityIdDTO> result = lectureUnitRepository.findIndexableUnitIdCourseIdPairsForCourses(List.of(courseAId));

        assertThat(result).containsExactlyInAnyOrder(new CourseEntityIdDTO(courseAId, textUnit.getId()), new CourseEntityIdDTO(courseAId, onlineUnit.getId()),
                new CourseEntityIdDTO(courseAId, videoUnit.getId()), new CourseEntityIdDTO(courseAId, blankVideoUnit.getId()), new CourseEntityIdDTO(courseAId, pdfUnit.getId()),
                new CourseEntityIdDTO(courseAId, nonPdfUnit.getId()));
        // The exercise unit is a LectureUnit but is excluded by the TYPE filter.
        assertThat(result).doesNotContain(new CourseEntityIdDTO(courseAId, exerciseUnit.getId()));
    }

    @Test
    void findsOnlyUnitsWithPdfAttachment() {
        List<CourseEntityIdDTO> result = lectureUnitRepository.findUnitIdCourseIdPairsWithPdfAttachmentForCourses(List.of(courseAId));

        assertThat(result).containsExactly(new CourseEntityIdDTO(courseAId, pdfUnit.getId()));
        // The .txt attachment and the video-only units must not be reported as expecting slides.
        assertThat(result).doesNotContain(new CourseEntityIdDTO(courseAId, nonPdfUnit.getId()), new CourseEntityIdDTO(courseAId, videoUnit.getId()));
    }

    @Test
    void findsOnlyUnitsWithNonBlankVideoSource() {
        List<CourseEntityIdDTO> result = lectureUnitRepository.findUnitIdCourseIdPairsWithVideoForCourses(List.of(courseAId));

        assertThat(result).containsExactly(new CourseEntityIdDTO(courseAId, videoUnit.getId()));
        // The whitespace-only source and the PDF/attachment units must not be reported as expecting a transcript.
        assertThat(result).doesNotContain(new CourseEntityIdDTO(courseAId, blankVideoUnit.getId()), new CourseEntityIdDTO(courseAId, pdfUnit.getId()));
    }

    @Test
    void findsCourseAndExamExercisesAttributedToTheirCourse() {
        List<CourseEntityIdDTO> result = exerciseRepository.findExerciseIdCourseIdPairsForCourses(List.of(courseAId));

        // The exam exercise has no direct course; it is attributed to course A through exerciseGroup -> exam -> course.
        assertThat(result).containsExactlyInAnyOrder(new CourseEntityIdDTO(courseAId, courseExerciseA.getId()), new CourseEntityIdDTO(courseAId, examExerciseA.getId()));
    }

    @Test
    void resolvesMultipleCoursesInOneQueryWithCorrectAttribution() {
        List<CourseEntityIdDTO> exercises = exerciseRepository.findExerciseIdCourseIdPairsForCourses(List.of(courseAId, courseBId));
        assertThat(exercises).containsExactlyInAnyOrder(new CourseEntityIdDTO(courseAId, courseExerciseA.getId()), new CourseEntityIdDTO(courseAId, examExerciseA.getId()),
                new CourseEntityIdDTO(courseBId, courseExerciseB.getId()));

        List<CourseEntityIdDTO> units = lectureUnitRepository.findIndexableUnitIdCourseIdPairsForCourses(List.of(courseAId, courseBId));
        // Course B's single text unit is bucketed under course B, not leaked into course A.
        assertThat(units).contains(new CourseEntityIdDTO(courseBId, textUnitB.getId()));
        assertThat(units).doesNotContain(new CourseEntityIdDTO(courseAId, textUnitB.getId()));
    }

    private AttachmentVideoUnit seedUnitWithVideoSource(Lecture lecture, String videoSource) {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setDescription("Test");
        unit.setLecture(lecture);
        unit.setVideoSource(videoSource);
        return attachmentVideoUnitRepository.save(unit);
    }

    private AttachmentVideoUnit seedUnitWithAttachmentLink(Lecture lecture, String link) {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setDescription("Test");
        unit.setLecture(lecture);
        unit = attachmentVideoUnitRepository.save(unit);

        Attachment attachment = new Attachment();
        attachment.setAttachmentType(AttachmentType.FILE);
        attachment.setName("Attachment");
        attachment.setVersion(1);
        attachment.setReleaseDate(ZonedDateTime.now().minusDays(1));
        attachment.setUploadDate(ZonedDateTime.now().minusDays(1));
        attachment.setLink(link);
        attachment.setAttachmentVideoUnit(unit);
        attachment = attachmentRepository.save(attachment);

        unit.setAttachment(attachment);
        return attachmentVideoUnitRepository.save(unit);
    }
}
