package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
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
    private AttachmentRepository attachmentRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
        attachmentRepository.saveAndFlush(testAttachment1);

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
    void testRegenerateStudentVersion_withNoHiddenSlides() throws Exception {
        String originalPath = testAttachment1.getStudentVersion();
        Path actualFilePath = FilePathConverter.fileSystemPathForExternalUri(URI.create(originalPath), FilePathType.STUDENT_VERSION_SLIDES);
        Files.createDirectories(actualFilePath.getParent());
        FileUtils.writeStringToFile(actualFilePath.toFile(), "student version", StandardCharsets.UTF_8);
        assertThat(actualFilePath).exists();

        attachmentService.regenerateStudentVersion(testAttachment1);
        Attachment reloadedAttachment = attachmentRepository.findById(testAttachment1.getId()).orElseThrow();
        assertThat(reloadedAttachment.getStudentVersion()).isNull();
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(actualFilePath).doesNotExist());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testRegenerateStudentVersion_withHiddenSlides() throws Exception {
        Integer originalAttachmentVersion = testAttachment2.getVersion();
        Path sourceFilePath = FilePathConverter.fileSystemPathForExternalUri(URI.create(testAttachment2.getLink()), FilePathType.ATTACHMENT_UNIT);
        int expectedPageCount;
        try (var sourceDocument = Loader.loadPDF(sourceFilePath.toFile())) {
            expectedPageCount = sourceDocument.getNumberOfPages() - 2;
        }

        attachmentService.regenerateStudentVersion(testAttachment2);
        Attachment firstRegeneratedAttachment = attachmentRepository.findById(testAttachment2.getId()).orElseThrow();
        String firstStudentVersionPath = firstRegeneratedAttachment.getStudentVersion();
        Path actualFilePath = FilePathConverter.fileSystemPathForExternalUri(URI.create(firstStudentVersionPath), FilePathType.STUDENT_VERSION_SLIDES);

        assertThat(firstRegeneratedAttachment.getStudentVersion()).isNotNull();
        assertThat(Files.exists(actualFilePath)).isTrue();

        attachmentService.regenerateStudentVersion(firstRegeneratedAttachment);
        Attachment secondRegeneratedAttachment = attachmentRepository.findById(testAttachment2.getId()).orElseThrow();
        String secondStudentVersionPath = secondRegeneratedAttachment.getStudentVersion();
        Path secondActualFilePath = FilePathConverter.fileSystemPathForExternalUri(URI.create(secondStudentVersionPath), FilePathType.STUDENT_VERSION_SLIDES);

        assertThat(secondStudentVersionPath).isNotEqualTo(firstStudentVersionPath);
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(actualFilePath).doesNotExist();
            assertThat(secondActualFilePath).exists();
            try (var studentVersion = Loader.loadPDF(secondActualFilePath.toFile())) {
                assertThat(studentVersion.getNumberOfPages()).isEqualTo(expectedPageCount);
            }
        });
        assertThat(secondRegeneratedAttachment.getVersion()).isEqualTo(originalAttachmentVersion);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testReplaceStudentVersionFileRestoresReferenceOnRollback() throws Exception {
        String originalStudentVersion = testAttachment2.getStudentVersion();
        Path sourceFilePath = FilePathConverter.fileSystemPathForExternalUri(URI.create(testAttachment2.getLink()), FilePathType.ATTACHMENT_UNIT);
        String[] replacementStudentVersion = new String[1];

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            try {
                attachmentService.replaceStudentVersionFile(Files.readAllBytes(sourceFilePath), testAttachment2, testAttachment2.getAttachmentVideoUnit().getId());
            }
            catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            replacementStudentVersion[0] = testAttachment2.getStudentVersion();
            status.setRollbackOnly();
        });

        assertThat(replacementStudentVersion[0]).isNotEqualTo(originalStudentVersion);
        assertThat(testAttachment2.getStudentVersion()).isEqualTo(originalStudentVersion);
        Path replacementPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(replacementStudentVersion[0]), FilePathType.STUDENT_VERSION_SLIDES);
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(replacementPath).doesNotExist());
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
                FilePathConverter.fileSystemPathForExternalUri(URI.create(testAttachment2.getLink()), FilePathType.ATTACHMENT_UNIT).toFile(), hiddenSlides);

        // Verify output
        assertThat(pdfData).isNotNull();
        assertThat(pdfData.length).isGreaterThan(0);
    }

}
