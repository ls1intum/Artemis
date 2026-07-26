package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.client.ExpectedCount;

import de.tum.cit.aet.artemis.core.connector.IrisRequestMockProvider;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

class SlideServiceIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "slideservicetest";

    @Autowired
    private SlideService slideService;

    @Autowired
    private SlideTestRepository slideRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private IrisRequestMockProvider irisRequestMockProvider;

    private Course testCourse;

    private AttachmentVideoUnit testAttachmentVideoUnit;

    private Slide testSlide;

    @BeforeEach
    void initTestCase() {
        irisRequestMockProvider.enableMockingOfRequests();

        var lecture = lectureUtilService.createCourseWithLecture(true);
        testCourse = lecture.getCourse();
        testAttachmentVideoUnit = lectureUtilService.createAttachmentVideoUnitWithSlidesAndFile(lecture, 5, true);
        testSlide = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId()).getFirst();
    }

    @AfterEach
    void tearDown() throws Exception {
        irisRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testHandleDueDateChange_withNewDueDate() {
        ZonedDateTime originalDueDate = ZonedDateTime.now().plusDays(7);
        Exercise originalExercise = TextExerciseFactory.generateTextExercise(ZonedDateTime.now(), originalDueDate, ZonedDateTime.now().plusDays(8), testCourse);
        originalExercise = exerciseRepository.save(originalExercise);
        testSlide.setExercise(originalExercise);
        testSlide.setHidden(originalDueDate);
        slideRepository.save(testSlide);

        ZonedDateTime newDueDate = originalDueDate.plusDays(3);
        Exercise updatedTextExercise = TextExerciseFactory.generateTextExercise(originalExercise.getReleaseDate(), newDueDate, originalExercise.getAssessmentDueDate(), testCourse);
        updatedTextExercise.setId(originalExercise.getId());
        updatedTextExercise.setTitle(originalExercise.getTitle());
        updatedTextExercise = exerciseRepository.save(updatedTextExercise);

        expectVisibilityWebhook(newDueDate);
        slideService.handleDueDateChange(originalExercise, updatedTextExercise);

        List<Slide> updatedSlides = slideRepository.findByExerciseId(originalExercise.getId());
        assertThat(updatedSlides).hasSize(1);
        assertThat(updatedSlides.getFirst().getHidden().toInstant().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(newDueDate.toInstant().truncatedTo(ChronoUnit.SECONDS));
        irisRequestMockProvider.verify();
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
        testSlide.setExercise(originalExercise);
        Slide savedSlide = slideRepository.save(testSlide);

        expectVisibilityWebhook(newDueDate);
        slideService.handleDueDateChange(originalExercise, updatedExercise);

        Slide updatedSlide = slideRepository.findById(savedSlide.getId()).orElseThrow();
        assertThat(updatedSlide.getHidden().toInstant().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(newDueDate.toInstant().truncatedTo(ChronoUnit.SECONDS));
        irisRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testHandleDueDateChange_withNullUpdatedDueDate() {
        ZonedDateTime originalDueDate = ZonedDateTime.now().plusDays(7);
        Exercise originalExercise = TextExerciseFactory.generateTextExercise(ZonedDateTime.now(), originalDueDate, ZonedDateTime.now().plusDays(8), testCourse);
        originalExercise = exerciseRepository.save(originalExercise);
        testSlide.setExercise(originalExercise);
        testSlide.setHidden(originalDueDate);
        Slide savedSlide = slideRepository.save(testSlide);

        Exercise updatedExercise = TextExerciseFactory.generateTextExercise(originalExercise.getReleaseDate(), null, originalExercise.getAssessmentDueDate(), testCourse);
        updatedExercise.setId(originalExercise.getId());
        updatedExercise.setTitle(originalExercise.getTitle());
        updatedExercise = exerciseRepository.save(updatedExercise);

        AtomicBoolean visibilityWebhookSeen = expectNullVisibilityWebhook();
        slideService.handleDueDateChange(originalExercise, updatedExercise);

        Slide updatedSlide = slideRepository.findById(savedSlide.getId()).orElseThrow();
        assertThat(updatedSlide.getHidden()).isNull();
        assertThat(visibilityWebhookSeen).isTrue();
        irisRequestMockProvider.verify();
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

        testSlide.setExercise(originalExercise);
        testSlide.setHidden(dueDate);
        Slide savedSlide = slideRepository.save(testSlide);

        slideService.handleDueDateChange(originalExercise, updatedExercise);

        Slide updatedSlide = slideRepository.findById(savedSlide.getId()).orElseThrow();
        assertThat(updatedSlide.getHidden().toInstant().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(dueDate.toInstant().truncatedTo(ChronoUnit.SECONDS));
    }

    private void expectVisibilityWebhook(ZonedDateTime expectedHiddenUntil) {
        irisRequestMockProvider.mockLectureUnitVisibilityWebhookRunResponse(dto -> {
            assertThat(dto.lectureUnitId()).isEqualTo(testAttachmentVideoUnit.getId());
            assertThat(dto.slides()).anySatisfy(slide -> {
                assertThat(slide.slideNumber()).isEqualTo(testSlide.getSlideNumber());
                if (expectedHiddenUntil == null) {
                    assertThat(slide.hiddenUntil()).isNull();
                }
                else {
                    assertThat(slide.hiddenUntil().toInstant().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(expectedHiddenUntil.toInstant().truncatedTo(ChronoUnit.SECONDS));
                }
            });
        }, ExpectedCount.once());
    }

    private AtomicBoolean expectNullVisibilityWebhook() {
        AtomicBoolean visibilityWebhookSeen = new AtomicBoolean();
        irisRequestMockProvider.mockLectureUnitVisibilityWebhookRunResponse(dto -> {
            if (!dto.lectureUnitId().equals(testAttachmentVideoUnit.getId())) {
                return;
            }
            assertThat(dto.slides()).anySatisfy(slide -> {
                assertThat(slide.slideNumber()).isEqualTo(testSlide.getSlideNumber());
                assertThat(slide.hiddenUntil()).isNull();
            });
            visibilityWebhookSeen.set(true);
        }, ExpectedCount.manyTimes());
        return visibilityWebhookSeen;
    }
}
