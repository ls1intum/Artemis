package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

/**
 * Service that dynamically schedules tasks to unhide slides at their expiration time.
 */
@Profile(PROFILE_SCHEDULING)
@Service
public class SlideUnhideService implements ApplicationListener<ApplicationReadyEvent> {

    private final SlideRepository slideRepository;

    private final TaskScheduler taskScheduler;

    private final AttachmentService attachmentService;

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    private static final Logger log = LoggerFactory.getLogger(SlideUnhideService.class);

    @Autowired
    public SlideUnhideService(SlideRepository slideRepository, TaskScheduler taskScheduler, AttachmentService attachmentService) {
        this.slideRepository = slideRepository;
        this.taskScheduler = taskScheduler;
        this.attachmentService = attachmentService;
    }

    /**
     * Method called when the application is ready.
     * It loads all hidden slides and schedules tasks to unhide them at their expiration time.
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        scheduleAllHiddenSlides();
    }

    /**
     * Loads all slides with a non-null hidden timestamp and schedules their unhiding.
     */
    public void scheduleAllHiddenSlides() {
        List<Slide> hiddenSlides = slideRepository.findAllByHiddenNotNull();

        for (Slide slide : hiddenSlides) {
            scheduleSlideUnhiding(slide);
        }
    }

    /**
     * Schedules a task to unhide a specific slide at its expiration time.
     * If the expiration time has already passed, unhides it immediately.
     *
     * @param slide The slide to be unhidden
     */
    public void scheduleSlideUnhiding(Slide slide) {
        if (slide.getHidden() == null) {
            return;
        }

        // Cancel any existing scheduled task for this slide
        cancelScheduledUnhiding(slide.getId());

        Date unhideDate = slide.getHidden();
        Instant unhideTime = unhideDate.toInstant();
        Instant now = Instant.now();

        if (unhideTime.isBefore(now)) {
            this.unhideSlide(slide.getId());
        }
        else {
            ScheduledFuture<?> scheduledTask = taskScheduler.schedule(() -> this.unhideSlide(slide.getId()), unhideTime);
            scheduledTasks.put(slide.getId(), scheduledTask);
        }
    }

    /**
     * Unhides a slide by setting its hidden property to null.
     * After unhiding, regenerates the student version of the attachment.
     *
     * @param slideId The ID of the slide to unhide
     */
    public void unhideSlide(Long slideId) {
        slideRepository.findById(slideId).ifPresent(slide -> {
            AttachmentUnit attachmentUnit = slide.getAttachmentUnit();
            Attachment attachment = null;
            if (attachmentUnit != null) {
                attachment = attachmentUnit.getAttachment();
            }

            // Use repository method to handle transaction
            slideRepository.unhideSlide(slideId);
            scheduledTasks.remove(slideId);

            // Regenerate student version of the attachment if applicable
            if (attachment != null) {
                try {
                    attachmentService.regenerateStudentVersion(attachment);
                }
                catch (Exception e) {
                    log.error("Failed to regenerate student version for attachment {} after unhiding slide {}: {}", attachment.getId(), slideId, e.getMessage(), e);
                }
            }
        });
    }

    /**
     * Cancels a scheduled unhiding task for a slide.
     *
     * @param slideId The ID of the slide whose task should be canceled
     */
    public void cancelScheduledUnhiding(Long slideId) {
        ScheduledFuture<?> scheduledTask = scheduledTasks.get(slideId);
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTasks.remove(slideId);
        }
    }

    /**
     * This method should be called whenever a slide's hidden timestamp is updated,
     * to make sure the scheduling is adjusted accordingly.
     *
     * @param slide The slide with updated hidden timestamp
     */
    public void handleSlideHiddenUpdate(Slide slide) {
        if (slide.getHidden() != null) {
            scheduleSlideUnhiding(slide);
        }
        else {
            cancelScheduledUnhiding(slide.getId());
        }
    }
}
