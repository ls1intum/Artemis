package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;

/**
 * Finishes TUM.Live uploads in the background. Runs on the scheduling node; every step is guarded in the database, so a second node running it would only
 * repeat work.
 */
@Conditional(LectureEnabled.class)
@Profile(PROFILE_SCHEDULING)
@Component
@Lazy
public class TumLiveUploadScheduler {

    private final TumLiveUploadService tumLiveUploadService;

    public TumLiveUploadScheduler(TumLiveUploadService tumLiveUploadService) {
        this.tumLiveUploadService = tumLiveUploadService;
    }

    /**
     * Attaches converted uploads, gives up failed and overdue ones, and deletes TUM.Live lectures Artemis no longer uses.
     */
    @Scheduled(fixedDelayString = "${artemis.tum-live.upload-check-interval:PT1M}", initialDelayString = "${artemis.tum-live.upload-check-interval:PT1M}")
    public void run() {
        SecurityUtils.runAsSystem(() -> {
            tumLiveUploadService.processPendingUploads();
            tumLiveUploadService.processCleanups();
        });
    }
}
