package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.client.ExpectedCount;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.connector.IrisRequestMockProvider;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.IrisLectureUnitSyncState;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.IrisLectureUnitSyncStateRepository;
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

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private IrisLectureUnitSyncStateRepository irisLectureUnitSyncStateRepository;

    private Course testCourse;

    private AttachmentVideoUnit testAttachmentVideoUnit;

    private Slide testSlide;

    @BeforeEach
    void initTestCase() {
        irisRequestMockProvider.enableMockingOfRequests();

        var lecture = lectureUtilService.createCourseWithLecture(true);
        testCourse = lecture.getCourse();
        testCourse.setTestCourse(true);
        courseRepository.saveAndFlush(testCourse);
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
        attachmentService.regenerateStudentVersion(testAttachmentVideoUnit.getAttachment());
        String originalStudentVersion = attachmentRepository.findById(testAttachmentVideoUnit.getAttachment().getId()).orElseThrow().getStudentVersion();
        assertThat(originalStudentVersion).isNotBlank();
        Path originalStudentVersionPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(originalStudentVersion), FilePathType.STUDENT_VERSION_SLIDES);
        assertThat(originalStudentVersionPath).exists();

        ZonedDateTime newDueDate = originalDueDate.plusDays(3);
        Exercise updatedTextExercise = TextExerciseFactory.generateTextExercise(originalExercise.getReleaseDate(), newDueDate, originalExercise.getAssessmentDueDate(), testCourse);
        updatedTextExercise.setId(originalExercise.getId());
        updatedTextExercise.setTitle(originalExercise.getTitle());
        updatedTextExercise = exerciseRepository.save(updatedTextExercise);

        AtomicBoolean visibilityWebhookSeen = expectVisibilityWebhook(newDueDate);
        slideService.handleDueDateChange(originalExercise, updatedTextExercise);

        List<Slide> updatedSlides = slideRepository.findByExerciseId(originalExercise.getId());
        assertThat(updatedSlides).hasSize(1);
        assertThat(updatedSlides.getFirst().getHidden().toInstant().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(newDueDate.toInstant().truncatedTo(ChronoUnit.SECONDS));
        assertThat(attachmentRepository.findById(testAttachmentVideoUnit.getAttachment().getId()).orElseThrow().getStudentVersion()).isEqualTo(originalStudentVersion);
        assertThat(originalStudentVersionPath).exists();
        awaitVisibilityWebhook(visibilityWebhookSeen);
        irisRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testHandleDueDateChange_withNullOriginalDueDate() throws Exception {
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
        Path sourcePdfPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(testAttachmentVideoUnit.getAttachment().getLink()), FilePathType.ATTACHMENT_UNIT);
        int sourcePageCount;
        try (var sourceDocument = Loader.loadPDF(sourcePdfPath.toFile())) {
            sourcePageCount = sourceDocument.getNumberOfPages();
        }

        AtomicBoolean visibilityWebhookSeen = expectVisibilityWebhook(newDueDate);
        slideService.handleDueDateChange(originalExercise, updatedExercise);

        Slide updatedSlide = slideRepository.findById(savedSlide.getId()).orElseThrow();
        assertThat(updatedSlide.getHidden().toInstant().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(newDueDate.toInstant().truncatedTo(ChronoUnit.SECONDS));
        String studentVersion = attachmentRepository.findById(testAttachmentVideoUnit.getAttachment().getId()).orElseThrow().getStudentVersion();
        assertThat(studentVersion).isNotBlank().contains("/student/");
        Path studentVersionPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(studentVersion), FilePathType.STUDENT_VERSION_SLIDES);
        assertThat(studentVersionPath).exists();
        try (var studentDocument = Loader.loadPDF(studentVersionPath.toFile())) {
            assertThat(studentDocument.getNumberOfPages()).isEqualTo(sourcePageCount - 1);
        }
        awaitVisibilityWebhook(visibilityWebhookSeen);
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
        attachmentService.regenerateStudentVersion(testAttachmentVideoUnit.getAttachment());
        String oldStudentVersion = attachmentRepository.findById(testAttachmentVideoUnit.getAttachment().getId()).orElseThrow().getStudentVersion();
        assertThat(oldStudentVersion).isNotBlank();
        Path oldStudentVersionPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(oldStudentVersion), FilePathType.STUDENT_VERSION_SLIDES);
        assertThat(oldStudentVersionPath).exists();

        Exercise updatedExercise = TextExerciseFactory.generateTextExercise(originalExercise.getReleaseDate(), null, originalExercise.getAssessmentDueDate(), testCourse);
        updatedExercise.setId(originalExercise.getId());
        updatedExercise.setTitle(originalExercise.getTitle());
        updatedExercise = exerciseRepository.save(updatedExercise);

        AtomicBoolean visibilityWebhookSeen = expectVisibilityWebhook(null);
        slideService.handleDueDateChange(originalExercise, updatedExercise);

        Slide updatedSlide = slideRepository.findById(savedSlide.getId()).orElseThrow();
        assertThat(updatedSlide.getHidden()).isNull();
        assertThat(attachmentRepository.findById(testAttachmentVideoUnit.getAttachment().getId()).orElseThrow().getStudentVersion()).isNull();
        await().untilAsserted(() -> assertThat(oldStudentVersionPath).doesNotExist());
        awaitVisibilityWebhook(visibilityWebhookSeen);
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

    private AtomicBoolean expectVisibilityWebhook(ZonedDateTime expectedHiddenUntil) {
        AtomicBoolean visibilityWebhookSeen = new AtomicBoolean();
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
            visibilityWebhookSeen.set(true);
        }, ExpectedCount.once());
        return visibilityWebhookSeen;
    }

    private void awaitVisibilityWebhook(AtomicBoolean visibilityWebhookSeen) {
        await().until(visibilityWebhookSeen::get);
        await().untilAsserted(() -> {
            IrisLectureUnitSyncState syncState = irisLectureUnitSyncStateRepository.findByLectureUnitId(testAttachmentVideoUnit.getId()).orElseThrow();
            assertThat(syncState.getStatus()).isEqualTo(IrisLectureUnitSyncState.STATUS_CLEAN);
            assertThat(syncState.getLastSyncedVisibilityHash()).isEqualTo(syncState.getVisibilityHash());
        });
    }
}
