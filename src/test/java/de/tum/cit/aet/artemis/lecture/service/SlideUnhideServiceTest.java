package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class SlideUnhideServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "slideunhideservicetest";

    @Autowired
    private SlideUnhideService slideUnhideService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private SlideTestRepository slideRepository;

    private List<Slide> testSlides;

    private ZonedDateTime futureDate;

    @BeforeEach
    void initTestCase() {
        // AttachmentUnit with hidden slides
        AttachmentUnit testAttachmentUnit = lectureUtilService.createAttachmentUnitWithSlidesAndFile(5, true);
        testSlides = slideRepository.findAllByAttachmentUnitId(testAttachmentUnit.getId());

        // Make slides 2 and 4 hidden
        ZonedDateTime pastDate = ZonedDateTime.now().minusDays(2);
        futureDate = ZonedDateTime.now().plusDays(7);
        testSlides.get(1).setHidden(pastDate);
        testSlides.get(3).setHidden(futureDate);
        slideRepository.save(testSlides.get(1));
        slideRepository.save(testSlides.get(3));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testScheduleAllHiddenSlides() {
        slideUnhideService.scheduleAllHiddenSlides();

        // Reload slides from database to get updated values
        Slide updatedSlide2 = slideRepository.findById(testSlides.get(1).getId()).orElseThrow();
        Slide updatedSlide4 = slideRepository.findById(testSlides.get(3).getId()).orElseThrow();

        assertThat(updatedSlide2.getHidden()).isNull();
        assertThat(updatedSlide4.getHidden().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(futureDate.truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testScheduleSlideUnhiding_withFutureDate() {
        // Setup a slide with future hidden date
        ZonedDateTime futureDate = ZonedDateTime.now().plusDays(2);
        Slide slideWithFutureHidden = testSlides.get(2);
        slideWithFutureHidden.setHidden(futureDate);
        slideRepository.save(slideWithFutureHidden);

        // Schedule unhiding
        slideUnhideService.scheduleSlideUnhiding(slideWithFutureHidden);

        // Verify slide is still hidden (scheduled for future)
        Slide updatedSlide = slideRepository.findById(slideWithFutureHidden.getId()).orElseThrow();

        assertThat(updatedSlide.getHidden().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(futureDate.truncatedTo(ChronoUnit.MILLIS));
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

        // Verify slide is unhidden
        Slide updatedSlide = slideRepository.findById(slideToUnhide.getId()).orElseThrow();
        assertThat(updatedSlide.getHidden()).isNull();
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

        // No assertions needed as we're primarily testing that no exception occurs
        // and the method runs successfully
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testCancelScheduledUnhiding() {
        // Setup a slide with future hidden date
        ZonedDateTime futureDate = ZonedDateTime.now().plusDays(2);
        Slide slideWithFutureHidden = testSlides.get(2);
        slideWithFutureHidden.setHidden(futureDate);
        slideRepository.save(slideWithFutureHidden);

        // Schedule unhiding
        slideUnhideService.scheduleSlideUnhiding(slideWithFutureHidden);
        slideUnhideService.cancelScheduledUnhiding(slideWithFutureHidden.getId());

        // Verify slide is still hidden (scheduled for future) since cancelScheduledUnhiding
        // only cancels the task but doesn't modify the slide
        Slide updatedSlide = slideRepository.findById(slideWithFutureHidden.getId()).orElseThrow();
        assertThat(updatedSlide.getHidden().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(futureDate.truncatedTo(ChronoUnit.MILLIS));
    }

}
