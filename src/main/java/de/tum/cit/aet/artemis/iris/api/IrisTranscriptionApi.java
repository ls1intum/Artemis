package de.tum.cit.aet.artemis.iris.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;

/**
 * API for transcription job management.
 * Allows the nebula module to create transcription webhook jobs for callback authentication.
 */
@Conditional(IrisEnabled.class)
@Controller
@Lazy
public class IrisTranscriptionApi extends AbstractIrisApi {

    private final PyrisJobService pyrisJobService;

    public IrisTranscriptionApi(PyrisJobService pyrisJobService) {
        this.pyrisJobService = pyrisJobService;
    }

    /**
     * Creates a transcription webhook job and returns the job token.
     * This token is used as the authenticationToken in the transcription request settings.
     *
     * @param courseId      the course ID
     * @param lectureId     the lecture ID
     * @param lectureUnitId the lecture unit ID
     * @return the job token (also used as the transcription jobId)
     */
    public String createTranscriptionJob(long courseId, long lectureId, long lectureUnitId) {
        return pyrisJobService.addTranscriptionWebhookJob(courseId, lectureId, lectureUnitId);
    }
}
