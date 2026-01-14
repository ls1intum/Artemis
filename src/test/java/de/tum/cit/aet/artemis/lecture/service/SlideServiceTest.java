package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

class SlideServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "slideservicetest";

    @Autowired
    private SlideService slideService;

    @Autowired
    private SlideTestRepository slideRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    private Course testCourse;

    private Exercise testExercise;

    @BeforeEach
    void initTestCase() {
        // Create a test exercise
        var lecture = lectureUtilService.createCourseWithLecture(true);
        testCourse = lecture.getCourse();
        testExercise = TextExerciseFactory.generateTextExercise(ZonedDateTime.now(), ZonedDateTime.now().plusDays(7), ZonedDateTime.now().plusDays(8), testCourse);

        AttachmentVideoUnit testAttachmentVideoUnit = lectureUtilService.createAttachmentVideoUnitWithSlidesAndFile(lecture, 5, true);
        List<Slide> testSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());
        testSlides.getFirst().setHidden(testExercise.getDueDate());
        slideRepository.save(testSlides.getFirst());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testHandleDueDateChange_withNewDueDate() {
        Exercise originalExercise = testExercise;
        ZonedDateTime newDueDate = testExercise.getDueDate().plusDays(3);
        testExercise.setDueDate(newDueDate);
        Exercise updatedTextExercise = exerciseRepository.save(testExercise);

        slideService.handleDueDateChange(originalExercise, updatedTextExercise);

        // Verify the slides were updated with the new due date
        List<Slide> updatedSlides = slideRepository.findByExerciseId(testExercise.getId());
        for (Slide slide : updatedSlides) {
            assertThat(slide.getHidden()).isEqualTo(newDueDate);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testHandleDueDateChange_withNullOriginalDueDate() {
        // Create an exercise with null due date
        Exercise originalExercise = TextExerciseFactory.generateTextExercise(ZonedDateTime.now(), null, ZonedDateTime.now().plusDays(8), testCourse);
        originalExercise = exerciseRepository.save(originalExercise);

        // Create updated version with a due date
        ZonedDateTime newDueDate = ZonedDateTime.now().plusDays(5);
        Exercise updatedExercise = TextExerciseFactory.generateTextExercise(originalExercise.getReleaseDate(), newDueDate, originalExercise.getAssessmentDueDate(), testCourse);
        updatedExercise.setId(originalExercise.getId());
        updatedExercise.setTitle(originalExercise.getTitle());
        updatedExercise = exerciseRepository.save(updatedExercise);

        // Create slides linked to this exercise
        Slide slide = slideRepository.findAll().getFirst();
        slide.setExercise(originalExercise);
        slideRepository.save(slide);

        // Handle due date change
        slideService.handleDueDateChange(originalExercise, updatedExercise);

        // Verify the slide was updated
        Slide updatedSlide = slideRepository.findById(slide.getId()).orElseThrow();
        assertThat(updatedSlide.getHidden().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(newDueDate.truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testHandleDueDateChange_withUnchangedDueDate() {
        // Create original exercise
        ZonedDateTime dueDate = ZonedDateTime.now().plusDays(7);
        Exercise originalExercise = TextExerciseFactory.generateTextExercise(ZonedDateTime.now(), dueDate, ZonedDateTime.now().plusDays(8), testCourse);
        originalExercise = exerciseRepository.save(originalExercise);

        // Create updated exercise with same due date
        Exercise updatedExercise = TextExerciseFactory.generateTextExercise(originalExercise.getReleaseDate(), originalExercise.getDueDate(),
                originalExercise.getAssessmentDueDate(), testCourse);
        updatedExercise.setId(originalExercise.getId());
        updatedExercise.setTitle(originalExercise.getTitle());
        updatedExercise = exerciseRepository.save(updatedExercise);

        // Create slide with original due date as hidden date
        Slide slide = slideRepository.findAll().getFirst();
        slide.setExercise(originalExercise); // Using persisted Exercise
        slide.setHidden(dueDate);
        Slide savedSlide = slideRepository.save(slide);

        // Handle due date change (which shouldn't change anything)
        slideService.handleDueDateChange(originalExercise, updatedExercise);

        // Verify the slide hasn't changed
        Slide updatedSlide = slideRepository.findById(savedSlide.getId()).orElseThrow();
        assertThat(updatedSlide.getHidden().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(dueDate.truncatedTo(ChronoUnit.MILLIS));
    }
}
