package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.service.ScheduleService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.domain.SlideLifecycle;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class SlideUnhideServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "slideunhideservicetest";

    @Mock
    private ScheduleService scheduleService;

    @Mock
    private SlideUnhideExecutionService slideUnhideExecutionService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private SlideTestRepository slideRepository;

    private SlideUnhideService slideUnhideService;

    private List<Slide> testSlides;

    @BeforeEach
    void initTestCase() {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);

        // Create the service with mocks
        slideUnhideService = new SlideUnhideService(slideUnhideExecutionService, Optional.of(scheduleService));

        // AttachmentUnit with hidden slides
        AttachmentUnit testAttachmentUnit = lectureUtilService.createAttachmentUnitWithSlidesAndFile(5, true);
        testSlides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());

        // Make slides 2 and 4 hidden
        ZonedDateTime pastDate = ZonedDateTime.now().minusDays(2);
        ZonedDateTime futureDate = ZonedDateTime.now().plusDays(7);
        testSlides.get(1).setHidden(pastDate);
        testSlides.get(3).setHidden(futureDate);
        slideRepository.save(testSlides.get(1));
        slideRepository.save(testSlides.get(3));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testHandleSlideHiddenUpdate_withFutureHiddenDate() {
        // Setup a slide with future hidden date
        ZonedDateTime futureDate = ZonedDateTime.now().plusDays(2);
        Slide slideWithFutureHidden = testSlides.get(2);
        slideWithFutureHidden.setHidden(futureDate);
        slideRepository.save(slideWithFutureHidden);

        // Call handleSlideHiddenUpdate
        slideUnhideService.handleSlideHiddenUpdate(slideWithFutureHidden);

        // Capture arguments to scheduleSlideTask
        ArgumentCaptor<Slide> slideCaptor = ArgumentCaptor.forClass(Slide.class);
        ArgumentCaptor<SlideLifecycle> lifecycleCaptor = ArgumentCaptor.forClass(SlideLifecycle.class);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<String> taskNameCaptor = ArgumentCaptor.forClass(String.class);

        // Verify scheduleSlideTask was called with correct arguments
        verify(scheduleService).scheduleSlideTask(slideCaptor.capture(), lifecycleCaptor.capture(), runnableCaptor.capture(), taskNameCaptor.capture());

        // Assert the captured values
        assertThat(slideCaptor.getValue()).isEqualTo(slideWithFutureHidden);
        assertThat(lifecycleCaptor.getValue()).isEqualTo(SlideLifecycle.UNHIDE);
        assertThat(taskNameCaptor.getValue()).isEqualTo("Slide Unhiding");

        // Verify unhideSlide was not called directly
        verify(slideUnhideExecutionService, never()).unhideSlide(slideWithFutureHidden.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testHandleSlideHiddenUpdate_withPastHiddenDate() {
        // Setup a slide with past hidden date
        ZonedDateTime pastDate = ZonedDateTime.now().minusDays(2);
        Slide slideWithPastHidden = testSlides.get(2);
        slideWithPastHidden.setHidden(pastDate);
        slideRepository.save(slideWithPastHidden);

        // Call handleSlideHiddenUpdate
        slideUnhideService.handleSlideHiddenUpdate(slideWithPastHidden);

        // Verify that the slide is immediately unhidden
        verify(slideUnhideExecutionService).unhideSlide(slideWithPastHidden.getId());

        // Verify scheduleSlideTask was not called
        verify(scheduleService, never()).scheduleSlideTask(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testHandleSlideHiddenUpdate_withNullHidden() {
        // Setup a slide with hidden date
        Slide slideToUpdate = testSlides.getFirst();
        slideToUpdate.setHidden(ZonedDateTime.now().plusDays(1));
        slideRepository.save(slideToUpdate);

        // Update slide to have null hidden date
        slideToUpdate.setHidden(null);

        // Handle the update
        slideUnhideService.handleSlideHiddenUpdate(slideToUpdate);

        // Verify cancellation occurred
        verify(scheduleService).cancelScheduledTaskForSlideLifecycle(slideToUpdate.getId(), SlideLifecycle.UNHIDE);

        // Verify neither scheduling nor unhiding was called
        verify(scheduleService, never()).scheduleSlideTask(any(), any(), any(), any());
        verify(slideUnhideExecutionService, never()).unhideSlide(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testUnhideSlide() {
        // Setup a slide with a hidden date
        Slide slideToUnhide = testSlides.getFirst();
        slideToUnhide.setHidden(ZonedDateTime.now());
        slideRepository.save(slideToUnhide);

        // Directly unhide the slide
        slideUnhideService.unhideSlide(slideToUnhide.getId());

        // Verify cancellation occurred
        verify(scheduleService).cancelScheduledTaskForSlideLifecycle(slideToUnhide.getId(), SlideLifecycle.UNHIDE);

        // Verify unhideSlide was called
        verify(slideUnhideExecutionService).unhideSlide(slideToUnhide.getId());
    }
}
