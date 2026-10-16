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

    private Attachment testAttachment1;

    private Attachment testAttachment2;

    @BeforeEach
    void initTestCase() {
        Lecture lecture = lectureUtilService.createCourseWithLecture(true);
        // AttachmentVideoUnit with no hidden slides
        AttachmentVideoUnit testAttachmentVideoUnit1 = lectureUtilService.createAttachmentVideoUnitWithSlidesAndFile(lecture, 5, true);
        testAttachment1 = testAttachmentVideoUnit1.getAttachment();
        // Store an existing student version to verify it gets removed. It has to be stored, not only set on the entity: the reference is removed only while the database
        // still holds it.
        testAttachment1.setStudentVersion("example.pdf");
        testAttachment1 = attachmentRepository.save(testAttachment1);

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
    void regenerationFailsClosedWhileTheSlidesBelongToAnotherFile() {
        long unitId = testAttachment2.getAttachmentVideoUnit().getId();
        markSlidesAsSplitFromAnotherFile(unitId);
        var state = attachmentRepository.findStudentVersionStateById(testAttachment2.getId()).orElseThrow();
        assertThat(attachmentRepository.updateStudentVersionIfUnchangedSince(testAttachment2.getId(), "submitted.pdf", state.version(), state.sha256Hash(), state.studentVersion()))
                .isEqualTo(1);
        testAttachment2.setStudentVersion("submitted.pdf");

        assertThat(attachmentService.regenerateStudentVersionOrRemoveOutdated(testAttachment2)).isTrue();

        // Nothing is generated from page numbers of another file; the downloads close instead
        assertThat(attachmentRepository.findStudentVersionStateById(testAttachment2.getId()).orElseThrow().studentVersion()).isNull();
        attachmentService.generateMissingStudentVersion(testAttachment2);
        assertThat(attachmentRepository.findStudentVersionStateById(testAttachment2.getId()).orElseThrow().studentVersion()).isNull();
    }

    @Test
    void publishingMatchesNothingWhileTheSlidesBelongToAnotherFile() {
        long unitId = testAttachment2.getAttachmentVideoUnit().getId();
        List<Long> hiddenSlideIds = slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(unitId).stream().map(Slide::getId).toList();
        markSlidesAsSplitFromAnotherFile(unitId);
        var state = attachmentRepository.findStudentVersionStateById(testAttachment2.getId()).orElseThrow();

        assertThat(attachmentRepository.publishStudentVersionIfUnchanged(testAttachment2.getId(), unitId, "stale.pdf", state.version(), state.sha256Hash(), state.studentVersion(),
                hiddenSlideIds, hiddenSlideIds.size())).isZero();
    }

    @Test
    void publishingAndStampingTreatMissingVersionAndHashAsEqual() {
        long unitId = testAttachment2.getAttachmentVideoUnit().getId();
        Attachment stored = attachmentRepository.findById(testAttachment2.getId()).orElseThrow();
        stored.setVersion(null);
        stored.setSha256Hash(null);
        attachmentRepository.saveAndFlush(stored);
        List<Slide> current = slideRepository.findAllByAttachmentVideoUnitId(unitId);
        current.forEach(slide -> slide.setAttachmentRevision(null, null));
        slideRepository.saveAll(current);
        List<Long> hiddenSlideIds = slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(unitId).stream().map(Slide::getId).toList();

        assertThat(slideRepository.existsCurrentSlideOfOtherRevision(unitId, null, null)).isFalse();
        assertThat(attachmentRepository.publishStudentVersionIfUnchanged(testAttachment2.getId(), unitId, "current.pdf", null, null, null, hiddenSlideIds, hiddenSlideIds.size()))
                .isEqualTo(1);
        // An upload of the same bytes later fills in the hash; the slides split from that file get it too
        assertThat(slideRepository.stampHashOnCurrentSlidesWithoutHash(unitId, null, "filled-in-hash")).isEqualTo(current.size());
        assertThat(slideRepository.existsCurrentSlideOfOtherRevision(unitId, null, "filled-in-hash")).isFalse();
    }

    private void markSlidesAsSplitFromAnotherFile(long unitId) {
        List<Slide> current = slideRepository.findAllByAttachmentVideoUnitId(unitId);
        current.forEach(slide -> slide.setAttachmentRevision(-7, "slides-of-the-previous-file"));
        slideRepository.saveAll(current);
    }

    @Test
    void publishingMatchesNothingOnceTheHiddenSlidesChanged() {
        long unitId = testAttachment2.getAttachmentVideoUnit().getId();
        List<Long> hiddenSlideIds = slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(unitId).stream().map(Slide::getId).toList();
        var state = attachmentRepository.findStudentVersionStateById(testAttachment2.getId()).orElseThrow();

        // Generated while only the first of the two hidden slides was hidden
        assertThat(attachmentRepository.publishStudentVersionIfUnchanged(testAttachment2.getId(), unitId, "stale.pdf", state.version(), state.sha256Hash(), state.studentVersion(),
                List.of(hiddenSlideIds.getFirst()), 1)).isZero();
        // Generated from a different student version reference
        assertThat(attachmentRepository.publishStudentVersionIfUnchanged(testAttachment2.getId(), unitId, "stale.pdf", state.version(), state.sha256Hash(), "other.pdf",
                hiddenSlideIds, hiddenSlideIds.size())).isZero();
        assertThat(attachmentRepository.findStudentVersionStateById(testAttachment2.getId()).orElseThrow().studentVersion()).isNull();

        assertThat(attachmentRepository.publishStudentVersionIfUnchanged(testAttachment2.getId(), unitId, "current.pdf", state.version(), state.sha256Hash(),
                state.studentVersion(), hiddenSlideIds, hiddenSlideIds.size())).isEqualTo(1);
        assertThat(attachmentRepository.findStudentVersionStateById(testAttachment2.getId()).orElseThrow().studentVersion()).isEqualTo("current.pdf");
    }

    @Test
    void findsUnitsThatHideSlidesWithoutAStudentVersion() {
        long unitWithHiddenSlides = testAttachment2.getAttachmentVideoUnit().getId();
        long unitWithoutHiddenSlides = testAttachment1.getAttachmentVideoUnit().getId();

        assertThat(slideRepository.findAttachmentVideoUnitIdsWithHiddenSlidesAndWithoutStudentVersion()).contains(unitWithHiddenSlides).doesNotContain(unitWithoutHiddenSlides);

        attachmentService.regenerateStudentVersion(testAttachment2);

        assertThat(slideRepository.findAttachmentVideoUnitIdsWithHiddenSlidesAndWithoutStudentVersion()).doesNotContain(unitWithHiddenSlides);
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
