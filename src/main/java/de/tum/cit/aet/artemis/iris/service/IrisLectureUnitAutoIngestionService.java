package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING_AND_IRIS;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * Service to handle the scheduling of auto ingestion jobs for lecture units.
 */
@Service
@Lazy
@Profile(PROFILE_CORE_AND_SCHEDULING_AND_IRIS)
public class IrisLectureUnitAutoIngestionService {

    private static final long BACKOFF_TIME_MINUTES = 2;

    private final IrisLectureApi irisLectureApi;

    private final TaskScheduler taskScheduler;

    private final LectureUnitRepositoryApi lectureUnitRepositoryApi;

    private final Map<Long, ScheduledFuture<?>> scheduledIngestionTasks = new ConcurrentHashMap<>();

    public IrisLectureUnitAutoIngestionService(IrisLectureApi irisLectureApi, TaskScheduler taskScheduler, LectureUnitRepositoryApi lectureUnitRepositoryApi) {
        this.irisLectureApi = irisLectureApi;
        this.taskScheduler = taskScheduler;
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;

    }

    /**
     * Send the scheduling to the nodes.
     *
     * @param lectureUnitId the id of the lecture unit that should be auto ingested
     */
    public void scheduleLectureUnitAutoIngestion(Long lectureUnitId) {
        cancelLectureUnitAutoIngestion(lectureUnitId);

        LectureUnit unit = lectureUnitRepositoryApi.findByIdElseThrow(lectureUnitId);

        if (unit instanceof AttachmentVideoUnit attachmentVideoUnit) {
            ZonedDateTime triggerTime = ZonedDateTime.now().plusMinutes(BACKOFF_TIME_MINUTES);
            ScheduledFuture<?> scheduledTask = this.taskScheduler.schedule(() -> irisLectureApi.addLectureUnitToPyrisDB(attachmentVideoUnit), triggerTime.toInstant());
            scheduledIngestionTasks.put(lectureUnitId, scheduledTask);
        }
    }

    /**
     * Send the scheduling for a lecture unit.
     *
     * @param lectureUnitId the id of the lecture unit that should no longer be auto ingested
     */
    public void cancelLectureUnitAutoIngestion(Long lectureUnitId) {
        ScheduledFuture<?> scheduledTask = scheduledIngestionTasks.get(lectureUnitId);
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledIngestionTasks.remove(lectureUnitId);
        }
    }
}
