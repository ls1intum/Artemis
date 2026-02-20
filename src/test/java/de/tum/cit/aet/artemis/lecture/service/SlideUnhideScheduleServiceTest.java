package de.tum.cit.aet.artemis.lecture.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
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
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
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
    private SlideTestRepository slideRepository;

    private SlideUnhideScheduleService slideUnhideScheduleService;

    private List<Slide> testSlides;

    @BeforeEach
    void initTestCase() {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);

        var lecture = lectureUtilService.createCourseWithLecture(true);

        // Create the service with SlideTestRepository instead of mocked SlideRepository
        slideUnhideScheduleService = new SlideUnhideScheduleService(slideRepository, slideUnhideExecutionService, scheduleService);

        // AttachmentVideoUnit with hidden slides
        AttachmentVideoUnit testAttachmentVideoUnit = lectureUtilService.createAttachmentVideoUnitWithSlidesAndFile(lecture, 5, true);
        testSlides = slideRepository.findAllByAttachmentVideoUnitId(testAttachmentVideoUnit.getId());

        // Make slides have different hidden timestamps
        ZonedDateTime pastDate = ZonedDateTime.now().minusDays(2);
        ZonedDateTime futureDate = ZonedDateTime.now().plusDays(7);
        testSlides.get(1).setHidden(pastDate);
        testSlides.get(3).setHidden(futureDate);
        slideRepository.save(testSlides.get(1));
        slideRepository.save(testSlides.get(3));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testScheduleAllHiddenSlides() {
        // Call the method to test
        slideUnhideScheduleService.scheduleAllHiddenSlides();

        // Capture the actual ID of the slide that should be unhidden immediately (the one with past date)
        Long pastSlideId = testSlides.get(1).getId(); // Index 1 has the past date from your setup

        // For the past date slide, verify unhideSlide is called with the correct ID
        verify(slideUnhideExecutionService).unhideSlide(pastSlideId);

        // For future date slides, verify scheduleSlideTask is called at least once
        verify(scheduleService, atLeastOnce()).scheduleSlideTask(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testScheduleSlideUnhidingByDTO_withValidSlide() {
        // Create a valid slide
        Slide slide = testSlides.getFirst();
        slide.setHidden(ZonedDateTime.now().plusDays(1));
        slideRepository.save(slide);

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
