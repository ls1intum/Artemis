package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.Comparator;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ActiveGenerationDTO;

/** Metadata-only cluster overview; mutations still go through the existing job lifecycle. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationMonitoringService {

    private final GenerationJobService jobs;

    private final DistributedMap<String, GenerationJobService.JobInfo> active;

    public GenerationMonitoringService(GenerationJobService jobs, DistributedDataProvider data) {
        this.jobs = jobs;
        this.active = data.getMap(GenerationJobService.JOB_MAP_NAME);
    }

    /** @return active generation summaries, excluding ordinary edits and undo operations */
    public List<ActiveGenerationDTO> activeGenerations() {
        return active.values().stream().filter(GenerationJobService::isGenerationJob).sorted(Comparator.comparing(GenerationJobService.JobInfo::startedAt))
                .map(job -> new ActiveGenerationDTO(job.jobId(), job.exerciseId(), job.userLogin(), job.startedAt(), job.cancellable(), jobs.isCancelled(job.jobId()))).toList();
    }

    /**
     * Requests cancellation without bypassing the persistence fence.
     *
     * @param exerciseId the exercise
     * @param jobId      the exact run selected by an administrator
     * @return whether cancellation was accepted
     */
    public boolean cancel(long exerciseId, String jobId) {
        return jobs.requestSystemCancellation(exerciseId, jobId, "Generation cancelled by an administrator.");
    }
}
