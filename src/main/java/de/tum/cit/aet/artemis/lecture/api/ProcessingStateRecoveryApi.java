package de.tum.cit.aet.artemis.lecture.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.service.ProcessingStateRecoveryService;

/**
 * API for lightweight lecture processing recovery operations.
 * Allows Iris health checks to recover lost processing jobs without instantiating
 * the full lecture processing dispatch pipeline.
 */
@Conditional(LectureWithIrisEnabled.class)
@Controller
@Lazy
public class ProcessingStateRecoveryApi extends AbstractLectureApi {

    private final ProcessingStateRecoveryService processingStateRecoveryService;

    public ProcessingStateRecoveryApi(ProcessingStateRecoveryService processingStateRecoveryService) {
        this.processingStateRecoveryService = processingStateRecoveryService;
    }

    /**
     * Handle an Iris restart notification.
     * All in-flight jobs are lost and should be marked as IDLE for retry.
     *
     * @return the number of jobs that were reset
     */
    public int handleIrisReset() {
        return processingStateRecoveryService.handleIrisReset();
    }
}
