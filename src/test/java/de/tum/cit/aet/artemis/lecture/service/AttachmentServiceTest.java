package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

class AttachmentServiceTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "attachmentservicetest";

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private SlideTestRepository slideRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    private Attachment testAttachment1;

    private Attachment testAttachment2;

    @BeforeEach
    void initTestCase() {
        Lecture lecture = lectureUtilService.createCourseWithLecture(true);
        // AttachmentVideoUnit with no hidden slides
        AttachmentVideoUnit testAttachmentVideoUnit1 = lectureUtilService.createAttachmentVideoUnitWithSlidesAndFile(lecture, 5, true);
        testAttachment1 = testAttachmentVideoUnit1.getAttachment();
        testAttachment1.setStudentVersion("attachments/attachment-unit/" + testAttachmentVideoUnit1.getId() + "/student/example.pdf"); // Set an existing version to verify it
        // gets removed

        // AttachmentVideoUnit with hidden slides
        AttachmentVideoUnit testAttachmentVideoUnit2 = lectureUtilService.createAttachmentVideoUnitWithSlidesAndFile(lecture, 5, true);
        testAttachment2 = testAttachmentVideoUnit2.getAttachment();
        List<Slide> testSlides2 = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit2.getId());

        // Make slides 2 and 4 hidden
        ZonedDateTime futureTime = ZonedDateTime.now().plusDays(7);
        testSlides2.get(1).setHidden(futureTime);
        testSlides2.get(3).setHidden(futureTime);
        slideRepository.save(testSlides2.get(1));
        slideRepository.save(testSlides2.get(3));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testRegenerateStudentVersion_withNoHiddenSlides() {
        Path actualFilePath = new FileSystemLocation.StudentVersionSlides(testAttachment1.getAttachmentVideoUnit().getId(), testAttachment1.getStudentVersion()).path();

        attachmentService.regenerateStudentVersion(testAttachment1);
        assertThat(testAttachment1.getStudentVersion()).isNull();
        assertThat(Files.exists(actualFilePath)).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testRegenerateStudentVersion_withHiddenSlides() {
        Integer originalAttachmentVersion = testAttachment2.getVersion();

        attachmentService.regenerateStudentVersion(testAttachment2);
        String firstStudentVersionPath = testAttachment2.getStudentVersion();
        Path actualFilePath = new FileSystemLocation.StudentVersionSlides(testAttachment2.getAttachmentVideoUnit().getId(), firstStudentVersionPath).path();

        assertThat(testAttachment2.getStudentVersion()).isNotNull();
        assertThat(Files.exists(actualFilePath)).isTrue();

        attachmentService.regenerateStudentVersion(testAttachment2);

        assertThat(testAttachment2.getStudentVersion()).isNotEqualTo(firstStudentVersionPath);
        assertThat(testAttachment2.getVersion()).isEqualTo(originalAttachmentVersion);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testRegenerateStudentVersion_withNullAttachmentVideoUnit() {
        // Create attachment with null attachment video unit
        Attachment attachmentWithoutUnit = new Attachment();
        attachmentWithoutUnit.setName("Test Attachment");
        attachmentWithoutUnit.setLink("/test/path/file.pdf");
        attachmentWithoutUnit.setAttachmentVideoUnit(null);

        // Should not throw exception
        attachmentService.regenerateStudentVersion(attachmentWithoutUnit);

        // No assertions needed as we're testing that no exception occurs
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testGenerateStudentVersionPdf() throws Exception {
        // Get hidden slides
        List<Slide> hiddenSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachment2.getAttachmentVideoUnit().getId());

        byte[] pdfData = attachmentService.generateStudentVersionPdf(
                new FileSystemLocation.AttachmentVideoUnitFile(testAttachment2.getAttachmentVideoUnit().getId(), testAttachment2.getLink()).path().toFile(), hiddenSlides);

        // Verify output
        assertThat(pdfData).isNotNull();
        assertThat(pdfData.length).isGreaterThan(0);
    }

}
