package de.tum.in.www1.artemis.service.connectors.localci;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.lock.FencedLock;

import de.tum.in.www1.artemis.domain.BuildJob;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.BuildJobResult;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.BuildJobRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildJobQueueItem;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;
import de.tum.in.www1.artemis.service.connectors.localci.dto.ResultQueueItem;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingMessagingService;
import de.tum.in.www1.artemis.web.websocket.programmingSubmission.BuildTriggerWebsocketError;

@Profile("localci")
@Service
public class LocalCIResultProcessingService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIResultProcessingService.class);

    private final HazelcastInstance hazelcastInstance;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ProgrammingMessagingService programmingMessagingService;

    private final BuildJobRepository buildJobRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ParticipationRepository participationRepository;

    private IQueue<ResultQueueItem> resultQueue;

    private FencedLock lock;

    private UUID listenerId;

    public LocalCIResultProcessingService(HazelcastInstance hazelcastInstance, ProgrammingExerciseGradingService programmingExerciseGradingService,
            ProgrammingMessagingService programmingMessagingService, BuildJobRepository buildJobRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ParticipationRepository participationRepository) {
        this.hazelcastInstance = hazelcastInstance;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.participationRepository = participationRepository;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.programmingMessagingService = programmingMessagingService;
        this.buildJobRepository = buildJobRepository;
    }

    @PostConstruct
    public void init() {
        this.resultQueue = this.hazelcastInstance.getQueue("buildResultQueue");
        this.lock = this.hazelcastInstance.getCPSubsystem().getLock("resultQueueLock");
        this.listenerId = resultQueue.addItemListener(new ResultQueueListener(), true);
    }

    public void removeListener() {
        this.resultQueue.removeItemListener(this.listenerId);
    }

    /**
     * Processes the build job results published by the build agents, notifies the user about the result and saves the result to the database.
     */
    public void processResult() {

        // set lock to prevent multiple nodes from processing the same build job
        lock.lock();
        ResultQueueItem resultQueueItem = resultQueue.poll();
        lock.unlock();

        if (resultQueueItem == null) {
            return;
        }
        log.info("Processing build job result");

        LocalCIBuildJobQueueItem buildJob = resultQueueItem.buildJobQueueItem();
        LocalCIBuildResult buildResult = resultQueueItem.buildResult();
        Throwable ex = resultQueueItem.exception();

        SecurityUtils.setAuthorizationObject();
        Optional<Participation> participationOptional = participationRepository.findById(buildJob.participationId());

        if (buildResult != null) {
            if (participationOptional.isPresent()) {
                ProgrammingExerciseParticipation participation = (ProgrammingExerciseParticipation) participationOptional.get();

                // In case the participation does not contain the exercise, we have to load it from the database
                if (participation.getProgrammingExercise() == null) {
                    participation.setProgrammingExercise(programmingExerciseRepository.findByParticipationIdOrElseThrow(participation.getId()));
                }

                Result result = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, resultQueueItem.buildResult());
                if (result != null) {
                    programmingMessagingService.notifyUserAboutNewResult(result, participation);
                }
                else {
                    programmingMessagingService.notifyUserAboutSubmissionError((Participation) participation,
                            new BuildTriggerWebsocketError("Result could not be processed", participation.getId()));
                }
            }
            else {
                log.warn("Participation with id {} has been deleted. Cancelling the processing of the build result.", buildJob.participationId());
            }
            // save build job to database
            saveFinishedBuildJob(buildJob, BuildJobResult.SUCCESSFUL);
        }
        else {
            if (ex.getCause() instanceof CancellationException && ex.getMessage().equals("Build job with id " + buildJob.id() + " was cancelled.")) {

                if (participationOptional.isPresent()) {
                    ProgrammingExerciseParticipation participation = (ProgrammingExerciseParticipation) participationOptional.get();
                    programmingMessagingService.notifyUserAboutSubmissionError((Participation) participation,
                            new BuildTriggerWebsocketError("Build job was cancelled", participation.getId()));
                }

                saveFinishedBuildJob(buildJob, BuildJobResult.CANCELLED);
            }
            else {
                log.error("Error while processing build job: {}", buildJob, ex);

                if (participationOptional.isPresent()) {
                    ProgrammingExerciseParticipation participation = (ProgrammingExerciseParticipation) participationOptional.get();
                    programmingMessagingService.notifyUserAboutSubmissionError((Participation) participation,
                            new BuildTriggerWebsocketError(ex.getMessage(), participation.getId()));
                }
                else {
                    log.warn("Participation with id {} has been deleted. Cancelling the requeueing of the build job.", buildJob.participationId());
                }

                saveFinishedBuildJob(buildJob, BuildJobResult.FAILED);
            }

        }
    }

    /**
     * Save a finished build job to the database.
     *
     * @param queueItem the build job object from the queue
     * @param result    the result of the build job (SUCCESSFUL, FAILED, CANCELLED)
     */
    public void saveFinishedBuildJob(LocalCIBuildJobQueueItem queueItem, BuildJobResult result) {
        try {
            BuildJob buildJob = new BuildJob(queueItem, result);
            buildJobRepository.save(buildJob);
        }
        catch (Exception e) {
            log.error("Could not save build job to database", e);
        }
    }

    public class ResultQueueListener implements ItemListener<ResultQueueItem> {

        @Override
        public void itemAdded(ItemEvent<ResultQueueItem> event) {
            log.debug("Result of build job with id {} added to queue", event.getItem().buildJobQueueItem().id());
            processResult();
        }

        @Override
        public void itemRemoved(ItemEvent<ResultQueueItem> event) {

        }
    }
}
