package de.tum.in.www1.artemis.service.connectors.pyris;

import static de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisWebhookType.DELETE;
import static de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisWebhookType.UPDATE;

import java.util.List;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisWebhookDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisWebhookSettingsDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisWebhookType;
import de.tum.in.www1.artemis.service.connectors.pyris.job.LectureWebhookJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.PyrisJob;
import de.tum.in.www1.artemis.service.connectors.pyris.job.RepositoryWebhookJob;

@Service
public class PyrisWebhookService {

    private final int max_retries = 5;

    private final int max_delay = 8; // seconds

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    public PyrisWebhookService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
    }

    public void sendLectureUnitsUpdate(List<LectureUnit> lectureUnits) {
        var job = new LectureWebhookJob(lectureUnits.stream().findFirst().map(LectureUnit::getLecture).map(Lecture::getCourse).map(Course::getId).orElse(-1L));
        callPyrisWebhook("/lecture-units", UPDATE, lectureUnits, job);
    }

    public void sendLectureUnitsDelete(List<LectureUnit> lectureUnits) {
        var job = new LectureWebhookJob(lectureUnits.stream().findFirst().map(LectureUnit::getLecture).map(Lecture::getCourse).map(Course::getId).orElse(-1L));
        callPyrisWebhook("/lecture-units", DELETE, lectureUnits, job);
    }

    public void sendRepositoryUpdate(Exercise exercise) {
        var job = new RepositoryWebhookJob(exercise.getId());
        callPyrisWebhook("/repositories", UPDATE, exercise, job);
    }

    private void callPyrisWebhook(String path, PyrisWebhookType pyrisWebhookType, Object payload, PyrisJob job) {
        var token = pyrisJobService.addJob(job);
        var settings = new PyrisWebhookSettingsDTO(token, pyrisJobService.getGitUsername(), pyrisJobService.getGitPasswordForJob(token));

        var success = false;
        for (int i = 0; i < max_retries; i++) {
            success = pyrisConnectorService.sendWebhook(path, new PyrisWebhookDTO(pyrisWebhookType, payload, settings));
            if (success) {
                break;
            }
            try {
                Thread.sleep(Math.max((long) Math.pow(2, i) * 1000, max_delay));
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
