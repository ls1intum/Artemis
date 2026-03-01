package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.hyperion.dto.HyperionCodeGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class HyperionCodeGenerationTaskServiceTest {

    @Mock
    private HyperionCodeGenerationExecutionService executionService;

    @Mock
    private HyperionWebsocketService websocket;

    private HyperionCodeGenerationTaskService service;

    private User user;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new HyperionCodeGenerationTaskService(executionService, websocket);

        user = new User();
        user.setLogin("student1");

        exercise = new ProgrammingExercise();
        exercise.setId(7L);
    }

    @Test
    void runJobAsync_sendsStartedAndRunsCleanup() {
        Runnable cleanup = mock(Runnable.class);

        service.runJobAsync("job-1", user, exercise, RepositoryType.SOLUTION, cleanup);

        ArgumentCaptor<HyperionCodeGenerationEventDTO> payloadCaptor = ArgumentCaptor.forClass(HyperionCodeGenerationEventDTO.class);
        verify(websocket).send(eq("student1"), eq("code-generation/jobs/job-1"), payloadCaptor.capture());
        HyperionCodeGenerationEventDTO payload = payloadCaptor.getValue();
        assertThat(payload.type()).isEqualTo(HyperionCodeGenerationEventDTO.Type.STARTED);
        assertThat(payload.jobId()).isEqualTo("job-1");
        assertThat(payload.exerciseId()).isEqualTo(7L);
        assertThat(payload.repositoryType()).isEqualTo(RepositoryType.SOLUTION);
        assertThat(payload.message()).isEqualTo("Started");
        verify(executionService).generateAndCompileCode(eq(exercise), eq(user), eq(RepositoryType.SOLUTION), any(HyperionCodeGenerationEventPublisher.class));
        verify(cleanup).run();
    }

    @Test
    void runJobAsync_whenExecutionFails_sendsErrorAndRunsCleanup() {
        Runnable cleanup = mock(Runnable.class);
        doThrow(new RuntimeException("boom")).when(executionService).generateAndCompileCode(eq(exercise), eq(user), eq(RepositoryType.TEMPLATE), any());

        service.runJobAsync("job-2", user, exercise, RepositoryType.TEMPLATE, cleanup);

        ArgumentCaptor<HyperionCodeGenerationEventDTO> payloadCaptor = ArgumentCaptor.forClass(HyperionCodeGenerationEventDTO.class);
        verify(websocket, times(2)).send(eq("student1"), eq("code-generation/jobs/job-2"), payloadCaptor.capture());

        List<HyperionCodeGenerationEventDTO> payloads = payloadCaptor.getAllValues();
        assertThat(payloads).extracting(HyperionCodeGenerationEventDTO::type).containsExactly(HyperionCodeGenerationEventDTO.Type.STARTED,
                HyperionCodeGenerationEventDTO.Type.ERROR);
        assertThat(payloads.get(1).message()).isEqualTo("Unhandled error: boom");
        verify(cleanup).run();
    }
}
