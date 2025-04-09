package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
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

    private Exercise testExercise;

    @BeforeEach
    void initTestCase() {
        // Create a test exercise
        Course testCourse = courseUtilService.addEmptyCourse();
        testExercise = TextExerciseFactory.generateTextExercise(ZonedDateTime.now(), ZonedDateTime.now().plusDays(7), ZonedDateTime.now().plusDays(8), testCourse);

        AttachmentUnit testAttachmentUnit = lectureUtilService.createAttachmentUnitWithSlidesAndFile(5, true);
        List<Slide> testSlides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());
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
}
