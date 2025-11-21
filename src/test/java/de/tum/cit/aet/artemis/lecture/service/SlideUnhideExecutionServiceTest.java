package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class SlideUnhideExecutionServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "slideunhideexecutionservicetest";

    @Mock
    private AttachmentService attachmentService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private SlideTestRepository slideTestRepository;

    @Autowired
    private AttachmentVideoUnitTestRepository attachmentVideoUnitTestRepository;

    private SlideUnhideExecutionService slideUnhideExecutionService;

    private Slide testSlide;

    private Attachment testAttachment;

    @BeforeEach
    void initTestCase() {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);

        // Create the service with slideTestRepository instead of mocked slideRepository
        slideUnhideExecutionService = new SlideUnhideExecutionService(slideTestRepository, attachmentService);

        var lecture = lectureUtilService.createCourseWithLecture(true);

        // Create test data
        AttachmentVideoUnit testAttachmentVideoUnit = lectureUtilService.createAttachmentVideoUnitWithSlidesAndFile(lecture, 5, true);
        testSlide = slideTestRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId()).getFirst();
        testAttachment = testAttachmentVideoUnit.getAttachment();

        // Set up slide to be hidden
        testSlide.setHidden(ZonedDateTime.now().minusDays(1));
        slideTestRepository.save(testSlide);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testUnhideSlide_withValidSlideAndAttachment() {
        // Call the method to test
        slideUnhideExecutionService.unhideSlide(testSlide.getId());

        // Verify the slide was unhidden in the database
        Optional<Slide> unhiddenSlide = slideTestRepository.findById(testSlide.getId());
        assertThat(unhiddenSlide).isPresent();
        assertThat(unhiddenSlide.get().getHidden()).isNull();

        // Verify student version was regenerated
        verify(attachmentService).regenerateStudentVersion(testAttachment);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testUnhideSlide_whenSlideNotFound() {
        // Use a non-existent ID
        Long nonExistentId = 999L;

        // Call the method to test
        slideUnhideExecutionService.unhideSlide(nonExistentId);

        // Verify student version was not regenerated
        verify(attachmentService, never()).regenerateStudentVersion(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testUnhideSlide_withNoAttachmentUnit() {
        // Create a slide with no attachment unit and save it
        Slide slideWithoutAttachment = new Slide();
        slideWithoutAttachment.setHidden(ZonedDateTime.now());
        slideWithoutAttachment.setSlideNumber(1);
        slideWithoutAttachment.setSlideImagePath("temp/placeholder.jpg"); // Set a valid slide image path
        slideWithoutAttachment = slideTestRepository.save(slideWithoutAttachment);

        // Call the method to test
        slideUnhideExecutionService.unhideSlide(slideWithoutAttachment.getId());

        // Verify the slide was unhidden in the database
        Optional<Slide> unhiddenSlide = slideTestRepository.findById(slideWithoutAttachment.getId());
        assertThat(unhiddenSlide).isPresent();
        assertThat(unhiddenSlide.get().getHidden()).isNull();

        // Verify student version was not regenerated
        verify(attachmentService, never()).regenerateStudentVersion(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testUnhideSlide_withAttachmentUnitButNoAttachment() {
        // Create a slide with attachment unit but no attachment
        var lecture = lectureUtilService.createCourseWithLecture(true);
        AttachmentVideoUnit unitWithoutAttachment = new AttachmentVideoUnit();
        unitWithoutAttachment.setLecture(lecture);
        unitWithoutAttachment.setDescription("Test Unit Without Attachment");

        // Save the attachment unit
        unitWithoutAttachment = attachmentVideoUnitTestRepository.save(unitWithoutAttachment);

        Slide slideWithUnitButNoAttachment = new Slide();
        slideWithUnitButNoAttachment.setHidden(ZonedDateTime.now());
        slideWithUnitButNoAttachment.setAttachmentVideoUnit(unitWithoutAttachment);
        slideWithUnitButNoAttachment.setSlideNumber(1);
        slideWithUnitButNoAttachment.setSlideImagePath("temp/placeholder.jpg"); // Set a valid slide image path
        slideWithUnitButNoAttachment = slideTestRepository.save(slideWithUnitButNoAttachment);

        // Call the method to test
        slideUnhideExecutionService.unhideSlide(slideWithUnitButNoAttachment.getId());

        // Verify the slide was unhidden in the database
        Optional<Slide> unhiddenSlide = slideTestRepository.findById(slideWithUnitButNoAttachment.getId());
        assertThat(unhiddenSlide).isPresent();
        assertThat(unhiddenSlide.get().getHidden()).isNull();

        // Verify student version was not regenerated
        verify(attachmentService, never()).regenerateStudentVersion(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testUnhideSlide_withRegenerationException() {
        // Setup attachmentService to throw exception
        doThrow(new RuntimeException("Test exception")).when(attachmentService).regenerateStudentVersion(any());

        // Call the method to test
        slideUnhideExecutionService.unhideSlide(testSlide.getId());

        // Verify the slide was still unhidden in the database
        Optional<Slide> unhiddenSlide = slideTestRepository.findById(testSlide.getId());
        assertThat(unhiddenSlide).isPresent();
        assertThat(unhiddenSlide.get().getHidden()).isNull();

        // Verify student version regeneration was attempted
        verify(attachmentService).regenerateStudentVersion(testAttachment);
    }
}
