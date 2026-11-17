package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.FinishedBuildJobDTO;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/** Records a finished build job in the database and announces it to the build overview. */
@Profile(PROFILE_LOCALCI)
@Lazy
@Service
public class LocalCIFinishedBuildJobService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIFinishedBuildJobService.class);

    private final BuildJobRepository buildJobRepository;

    private final Optional<LocalCIQueueWebsocketService> localCIQueueWebsocketService;

    public LocalCIFinishedBuildJobService(BuildJobRepository buildJobRepository, Optional<LocalCIQueueWebsocketService> localCIQueueWebsocketService) {
        this.buildJobRepository = buildJobRepository;
        this.localCIQueueWebsocketService = localCIQueueWebsocketService;
    }

    /**
     * Save a finished build job to the database and send a WebSocket notification.
     *
     * @param queueItem   the build job object from the queue
     * @param buildStatus the status of the build job (SUCCESSFUL, FAILED, CANCELLED)
     * @param result      the submission result
     * @return the saved build job, or null if it could not be saved
     */
    @Nullable
    BuildJob saveFinishedBuildJob(BuildJobQueueItem queueItem, BuildStatus buildStatus, @Nullable Result result) {
        return saveFinishedBuildJob(queueItem, buildStatus, result, false, false);
    }

    /**
     * Same as {@link #saveFinishedBuildJob(BuildJobQueueItem, BuildStatus, Result)}, for a container of a multi-container build.
     *
     * @param queueItem   the build job object from the queue
     * @param buildStatus the status of the build job
     * @param result      the result the job is linked to, or null if it is not linked to one
     * @param buildFailed whether the container failed to build
     * @param cleanRun    whether the container's build script exited cleanly and reported no failed test
     * @return the saved build job, or null if it could not be saved
     */
    @Nullable
    BuildJob saveFinishedBuildJob(BuildJobQueueItem queueItem, BuildStatus buildStatus, @Nullable Result result, boolean buildFailed, boolean cleanRun) {
        try {
            BuildJob buildJob = new BuildJob(queueItem, buildStatus, result, buildFailed);
            buildJob.setCleanRun(cleanRun);
            buildJobRepository.findByBuildJobId(queueItem.id()).ifPresent(existingBuildJob -> buildJob.setId(existingBuildJob.getId()));
            BuildJob savedBuildJob = buildJobRepository.save(buildJob);

            // Send WebSocket notification for the finished build job
            // Refetch with eager loading to avoid LazyInitializationException
            localCIQueueWebsocketService.ifPresent(service -> {
                try {
                    buildJobRepository.findWithDataByBuildJobId(savedBuildJob.getBuildJobId()).ifPresent(buildJobWithData -> {
                        FinishedBuildJobDTO finishedBuildJobDTO = FinishedBuildJobDTO.of(buildJobWithData);
                        service.sendFinishedBuildJobOverWebsocket(finishedBuildJobDTO);
                    });
                }
                catch (Exception e) {
                    log.warn("Could not send finished build job notification over WebSocket", e);
                }
            });

            return savedBuildJob;
        }
        catch (Exception e) {
            log.error("Could not save build job to database", e);
            return null;
        }
    }
}
