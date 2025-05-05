package de.tum.cit.aet.artemis.lecture.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
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
import de.tum.cit.aet.artemis.lecture.domain.SlideLifecycle;
import de.tum.cit.aet.artemis.lecture.dto.SlideUnhideDTO;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class SlideUnhideScheduleServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "slideunhidescheduleservicetest";

    @Mock
    private ScheduleService scheduleService;

    @Mock
    private SlideUnhideExecutionService slideUnhideExecutionService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private SlideTestRepository slideTestRepository;

    private SlideUnhideScheduleService slideUnhideScheduleService;

    private List<Slide> testSlides;

    @BeforeEach
    void initTestCase() {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);

        // Create the service with SlideTestRepository instead of mocked SlideRepository
        slideUnhideScheduleService = new SlideUnhideScheduleService(slideTestRepository, slideUnhideExecutionService, scheduleService);

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
        // Insert test slide data into the real repository
        // The hidden slides are already set up in the initTestCase method

        // Call the method to test
        slideUnhideScheduleService.scheduleAllHiddenSlides();

        // We have slides with past and future dates
        // For the past date (testSlides.get(1)), we should call unhideSlide immediately
        verify(slideUnhideExecutionService).unhideSlide(testSlides.get(1).getId());

        // For the future date (testSlides.get(3)), we should schedule a task
        verify(scheduleService).scheduleSlideTask(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testScheduleSlideUnhidingByDTO_withValidSlide() {
        // Create a valid slide
        Slide slide = testSlides.getFirst();
        slide.setHidden(ZonedDateTime.now().plusDays(1));
        slideTestRepository.save(slide);

        // Setup a DTO with a valid slide ID
        SlideUnhideDTO dto = new SlideUnhideDTO(slide.getId(), slide.getHidden());

        // Call the method to test
        slideUnhideScheduleService.scheduleSlideUnhidingByDTO(dto);

        // If hidden date is in the future, we use scheduleService to schedule the task
        // If it's in the past, we directly call unhideSlide
        // In our test case, the date is in the future
        verify(scheduleService).scheduleSlideTask(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testScheduleSlideUnhidingByDTO_withNullHidden() {
        // Setup a DTO with null hidden date
        SlideUnhideDTO dto = new SlideUnhideDTO(testSlides.getFirst().getId(), null);

        // Call the method to test
        slideUnhideScheduleService.scheduleSlideUnhidingByDTO(dto);

        // Verify that scheduleService was never called
        verify(scheduleService, never()).scheduleSlideTask(any(), any(), any(), any());
        // Verify that slideUnhideExecutionService was never called
        verify(slideUnhideExecutionService, never()).unhideSlide(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testScheduleSlideUnhiding_withValidId() {
        // Use an existing slide from the database
        Slide slide = testSlides.getFirst();
        Long slideId = slide.getId();

        // Call the method to test
        slideUnhideScheduleService.scheduleSlideUnhiding(slideId);

        // Since we're calling scheduleSlideUnhiding(slideId), which calls scheduleSlideUnhidingByDTO,
        // and our slide doesn't have a hidden property set by default, verify scheduleService wasn't called
        // In real implementation, we'd verify that scheduleService.scheduleSlideTask() is called
        // if slide.getHidden() is in the future, or slideUnhideExecutionService.unhideSlide() is called
        // if slide.getHidden() is in the past
        // Here we just verify nothing happens since hidden is null
        verify(scheduleService, never()).scheduleSlideTask(any(), any(), any(), any());
        verify(slideUnhideExecutionService, never()).unhideSlide(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testScheduleSlideUnhiding_withInvalidId() {
        // Use a slide ID that doesn't exist in the database
        Long nonExistentId = 999999L;

        // Call the method to test
        slideUnhideScheduleService.scheduleSlideUnhiding(nonExistentId);

        // Verify neither scheduleService nor slideUnhideExecutionService was called since slide wasn't found
        verify(scheduleService, never()).scheduleSlideTask(any(), any(), any(), any());
        verify(slideUnhideExecutionService, never()).unhideSlide(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testCancelScheduledUnhiding() {
        Long slideId = testSlides.getFirst().getId();

        // Call the method to test
        slideUnhideScheduleService.cancelScheduledUnhiding(slideId);

        // Verify cancelScheduledTaskForSlideLifecycle was called with the correct ID and lifecycle
        verify(scheduleService).cancelScheduledTaskForSlideLifecycle(slideId, SlideLifecycle.UNHIDE);
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
