package de.tum.cit.aet.artemis.lecture.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.service.ScheduleService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.dto.SlideUnhideDTO;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class SlideUnhideScheduleServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "slideunhidescheduleservicetest";

    @Mock
    private ScheduleService scheduleService;

    @Mock
    private SlideUnhideService slideUnhideService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private SlideTestRepository slideTestRepository;

    @Mock
    private SlideRepository slideRepository;

    private SlideUnhideScheduleService slideUnhideScheduleService;

    private List<Slide> testSlides;

    @BeforeEach
    void initTestCase() {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);

        // Create the service with mocks
        slideUnhideScheduleService = new SlideUnhideScheduleService(slideRepository, slideUnhideService, scheduleService);

        // AttachmentUnit with hidden slides
        AttachmentUnit testAttachmentUnit = lectureUtilService.createAttachmentUnitWithSlidesAndFile(5, true);
        testSlides = slideTestRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());

        // Make slides have different hidden timestamps
        ZonedDateTime pastDate = ZonedDateTime.now().minusDays(2);
        ZonedDateTime futureDate = ZonedDateTime.now().plusDays(7);
        testSlides.get(1).setHidden(pastDate);
        testSlides.get(3).setHidden(futureDate);
        slideTestRepository.save(testSlides.get(1));
        slideTestRepository.save(testSlides.get(3));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testScheduleAllHiddenSlides() {
        // Setup mock repository to return slide DTOs
        SlideUnhideDTO dto1 = new SlideUnhideDTO(testSlides.get(1).getId(), testSlides.get(1).getHidden());
        SlideUnhideDTO dto2 = new SlideUnhideDTO(testSlides.get(3).getId(), testSlides.get(3).getHidden());
        List<SlideUnhideDTO> mockDTOs = Arrays.asList(dto1, dto2);

        when(slideRepository.findHiddenSlidesProjection()).thenReturn(mockDTOs);
        when(slideRepository.findById(testSlides.get(1).getId())).thenReturn(java.util.Optional.of(testSlides.get(1)));
        when(slideRepository.findById(testSlides.get(3).getId())).thenReturn(java.util.Optional.of(testSlides.get(3)));

        // Call the method to test
        slideUnhideScheduleService.scheduleAllHiddenSlides();

        // Verify each slide is processed
        verify(slideUnhideService).handleSlideHiddenUpdate(testSlides.get(1));
        verify(slideUnhideService).handleSlideHiddenUpdate(testSlides.get(3));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testScheduleSlideUnhidingByDTO_withValidSlide() {
        // Setup a DTO with a valid slide ID
        SlideUnhideDTO dto = new SlideUnhideDTO(testSlides.getFirst().getId(), ZonedDateTime.now().plusDays(1));

        // Setup mock to return the slide
        when(slideRepository.findById(dto.id())).thenReturn(java.util.Optional.of(testSlides.getFirst()));

        // Call the method to test
        slideUnhideScheduleService.scheduleSlideUnhidingByDTO(dto);

        // Verify handleSlideHiddenUpdate was called
        verify(slideUnhideService).handleSlideHiddenUpdate(testSlides.getFirst());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testScheduleSlideUnhidingByDTO_withNullHidden() {
        // Setup a DTO with null hidden date
        SlideUnhideDTO dto = new SlideUnhideDTO(testSlides.getFirst().getId(), null);

        // Call the method to test
        slideUnhideScheduleService.scheduleSlideUnhidingByDTO(dto);

        // Verify that the repository was never accessed
        verify(slideRepository, never()).findById(any());
        // Verify that unhide service was never called
        verify(slideUnhideService, never()).handleSlideHiddenUpdate(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testScheduleSlideUnhiding_withValidId() {
        // Setup mock to return the slide
        when(slideRepository.findById(testSlides.getFirst().getId())).thenReturn(java.util.Optional.of(testSlides.getFirst()));

        // Call the method to test
        slideUnhideScheduleService.scheduleSlideUnhiding(testSlides.getFirst().getId());

        // Verify handleSlideHiddenUpdate was called
        verify(slideUnhideService).handleSlideHiddenUpdate(testSlides.getFirst());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testScheduleSlideUnhiding_withInvalidId() {
        // Setup mock to return empty optional
        when(slideRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        // Call the method to test
        slideUnhideScheduleService.scheduleSlideUnhiding(999L);

        // Verify handleSlideHiddenUpdate was never called
        verify(slideUnhideService, never()).handleSlideHiddenUpdate(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testCancelScheduledUnhiding() {
        Long slideId = testSlides.getFirst().getId();

        // Call the method to test
        slideUnhideScheduleService.cancelScheduledUnhiding(slideId);

        // Verify cancelAllScheduledSlideTasks was called with the correct ID
        verify(scheduleService).cancelAllScheduledSlideTasks(slideId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testOnApplicationReady() {
        // Create a spy of the service to verify scheduleAllHiddenSlides is called
        SlideUnhideScheduleService serviceSpy = spy(slideUnhideScheduleService);

        // Call the method to test
        serviceSpy.onApplicationReady();

        // Verify scheduleAllHiddenSlides was called
        verify(serviceSpy).scheduleAllHiddenSlides();
    }
}
