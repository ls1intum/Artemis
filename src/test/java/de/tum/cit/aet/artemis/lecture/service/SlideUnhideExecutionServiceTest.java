package de.tum.cit.aet.artemis.lecture.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class SlideUnhideExecutionServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "slideunhideexecutionservicetest";

    @Mock
    private SlideRepository slideRepository;

    @Mock
    private AttachmentService attachmentService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private SlideTestRepository slideTestRepository;

    private SlideUnhideExecutionService slideUnhideExecutionService;

    private Slide testSlide;

    private Attachment testAttachment;

    @BeforeEach
    void initTestCase() {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);

        // Create the service with mocks
        slideUnhideExecutionService = new SlideUnhideExecutionService(slideRepository, attachmentService);

        // Create test data
        AttachmentUnit testAttachmentUnit = lectureUtilService.createAttachmentUnitWithSlidesAndFile(1, true);
        testSlide = slideTestRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId()).getFirst();
        testAttachment = testAttachmentUnit.getAttachment();

        // Set up slide to be hidden
        testSlide.setHidden(ZonedDateTime.now().minusDays(1));
        slideTestRepository.save(testSlide);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testUnhideSlide_withValidSlideAndAttachment() {
        // Setup mock repository to return the test slide
        when(slideRepository.findById(testSlide.getId())).thenReturn(Optional.of(testSlide));

        // Call the method to test
        slideUnhideExecutionService.unhideSlide(testSlide.getId());

        // Verify unhideSlide repository method was called
        verify(slideRepository).unhideSlide(testSlide.getId());

        // Verify student version was regenerated
        verify(attachmentService).regenerateStudentVersion(testAttachment);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testUnhideSlide_whenSlideNotFound() {
        // Setup mock repository to return empty optional
        when(slideRepository.findById(999L)).thenReturn(Optional.empty());

        // Call the method to test
        slideUnhideExecutionService.unhideSlide(999L);

        // Verify unhideSlide repository method was not called
        verify(slideRepository, never()).unhideSlide(anyLong());

        // Verify student version was not regenerated
        verify(attachmentService, never()).regenerateStudentVersion(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testUnhideSlide_withNoAttachmentUnit() {
        // Create a slide with no attachment unit
        Slide slideWithoutAttachment = new Slide();
        slideWithoutAttachment.setId(888L);
        slideWithoutAttachment.setHidden(ZonedDateTime.now());

        // Setup mock repository to return the test slide
        when(slideRepository.findById(slideWithoutAttachment.getId())).thenReturn(Optional.of(slideWithoutAttachment));

        // Call the method to test
        slideUnhideExecutionService.unhideSlide(slideWithoutAttachment.getId());

        // Verify unhideSlide repository method was called
        verify(slideRepository).unhideSlide(slideWithoutAttachment.getId());

        // Verify student version was not regenerated
        verify(attachmentService, never()).regenerateStudentVersion(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testUnhideSlide_withAttachmentUnitButNoAttachment() {
        // Create a slide with attachment unit but no attachment
        AttachmentUnit unitWithoutAttachment = new AttachmentUnit();
        unitWithoutAttachment.setId(777L);

        Slide slideWithUnitButNoAttachment = new Slide();
        slideWithUnitButNoAttachment.setId(777L);
        slideWithUnitButNoAttachment.setHidden(ZonedDateTime.now());
        slideWithUnitButNoAttachment.setAttachmentUnit(unitWithoutAttachment);

        // Setup mock repository to return the test slide
        when(slideRepository.findById(slideWithUnitButNoAttachment.getId())).thenReturn(Optional.of(slideWithUnitButNoAttachment));

        // Call the method to test
        slideUnhideExecutionService.unhideSlide(slideWithUnitButNoAttachment.getId());

        // Verify unhideSlide repository method was called
        verify(slideRepository).unhideSlide(slideWithUnitButNoAttachment.getId());

        // Verify student version was not regenerated
        verify(attachmentService, never()).regenerateStudentVersion(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testUnhideSlide_withRegenerationException() {
        // Setup mock repository to return the test slide
        when(slideRepository.findById(testSlide.getId())).thenReturn(Optional.of(testSlide));

        // Setup attachmentService to throw exception
        doThrow(new RuntimeException("Test exception")).when(attachmentService).regenerateStudentVersion(any());

        // Call the method to test
        slideUnhideExecutionService.unhideSlide(testSlide.getId());

        // Verify unhideSlide repository method was still called
        verify(slideRepository).unhideSlide(testSlide.getId());

        // Verify student version regeneration was attempted
        verify(attachmentService).regenerateStudentVersion(testAttachment);

        // Note: We can't directly verify logging, but the test should pass if no exception is thrown
    }
}
