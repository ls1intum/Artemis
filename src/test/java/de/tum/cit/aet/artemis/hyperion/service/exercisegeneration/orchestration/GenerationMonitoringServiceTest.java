package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.hyperion.dto.ActiveGenerationDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService.JobInfo;

class GenerationMonitoringServiceTest {

    private final GenerationJobService jobs = mock();

    private final LocalDataProviderService provider = new LocalDataProviderService();

    private final GenerationMonitoringService service = new GenerationMonitoringService(jobs, provider);

    @Test
    void activeGenerationsCarryModeTitleAndCourseAndSkipRevertsAndExternalMutations() {
        DistributedMap<String, JobInfo> active = provider.getMap(GenerationJobService.JOB_MAP_NAME);
        Instant earlier = Instant.parse("2026-09-11T08:00:00Z");
        Instant later = Instant.parse("2026-09-11T09:00:00Z");
        active.put("2", new JobInfo("adapt-job", "editor", 2, later, later.plusSeconds(600), "node", later, true, null, GenerationMode.ADAPT, "Bubble Sort", 7L));
        active.put("1", new JobInfo("generate-job", "instructor", 1, earlier, earlier.plusSeconds(600), "node", earlier, true, null, GenerationMode.GENERATE, "Linked List", 7L));
        active.put("3", new JobInfo("revert-x", "editor", 3, earlier, null, "node", earlier, false, null));
        active.put("4", new JobInfo(GenerationJobService.EXTERNAL_MUTATION_JOB_PREFIX + "y", "external", 4, earlier, null, "node", earlier, false, null));
        // A slot recorded before display context was tracked still lists, without a mode or title.
        active.put("5", new JobInfo("legacy-job", "instructor", 5, later.plusSeconds(1), null, "node", later, true, null));
        when(jobs.isCancelled("adapt-job")).thenReturn(true);

        var generations = service.activeGenerations();

        assertThat(generations).extracting(ActiveGenerationDTO::jobId).containsExactly("generate-job", "adapt-job", "legacy-job");
        assertThat(generations.getFirst()).satisfies(run -> {
            assertThat(run.mode()).isEqualTo(GenerationMode.GENERATE);
            assertThat(run.exerciseTitle()).isEqualTo("Linked List");
            assertThat(run.courseId()).isEqualTo(7L);
            assertThat(run.userLogin()).isEqualTo("instructor");
            assertThat(run.cancellationRequested()).isFalse();
        });
        assertThat(generations.get(1)).satisfies(run -> {
            assertThat(run.mode()).isEqualTo(GenerationMode.ADAPT);
            assertThat(run.exerciseTitle()).isEqualTo("Bubble Sort");
            assertThat(run.cancellationRequested()).isTrue();
        });
        assertThat(generations.getLast()).satisfies(run -> {
            assertThat(run.mode()).isNull();
            assertThat(run.exerciseTitle()).isNull();
            assertThat(run.courseId()).isNull();
        });
    }
}
