import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { HyperionAuthoringRunApi } from 'app/openapi/api/hyperion-authoring-run-api';
import { AuthoringRunPage } from 'app/openapi/model/authoring-run-page';
import { HyperionExerciseGenerationApi } from 'app/openapi/api/hyperion-exercise-generation-api';
import {
    ExerciseGenerationRevertResult,
    HyperionExerciseGenerationState,
    HyperionGenerationJobStart,
    HyperionGenerationMessage,
    HyperionGenerationRequest,
    HyperionGenerationStatus,
    HyperionMetadataSuggestion,
} from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import { ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

/** Client boundary for whole-exercise generation and adaptation. */
@Service()
export class HyperionExerciseGenerationService {
    private readonly api = inject(HyperionExerciseGenerationApi);
    private readonly history = inject(HyperionAuthoringRunApi);
    private readonly websocketService = inject(WebsocketService);

    generate(exerciseId: number, request: HyperionGenerationRequest): Observable<HyperionGenerationJobStart> {
        return this.api.generateExercise(exerciseId, request);
    }

    /**
     * Asks for the metadata of the exercise a brief describes. The server answers with usable values even when the model does not, while transport and server-side lookup failures still propagate.
     */
    suggestMetadata(courseId: number, prompt: string, projectType: ProjectType): Observable<HyperionMetadataSuggestion> {
        return this.api.suggestGenerationMetadata(courseId, { prompt, projectType });
    }

    getStatus(exerciseId: number): Observable<HyperionGenerationStatus | null> {
        return this.api.getExerciseGenerationStatus(exerciseId);
    }

    getRuns(beforeId?: number): Observable<AuthoringRunPage> {
        return this.history.getAuthoringRuns(beforeId);
    }

    getRunAccess(jobIds: string[]): Observable<string[]> {
        return this.history.checkAuthoringRunAccess(jobIds);
    }

    getRunStatus(exerciseId: number, runId: string): Observable<HyperionGenerationStatus> {
        return this.history.getAuthoringRunStatus(exerciseId, runId);
    }

    cancel(exerciseId: number, jobId: string): Observable<void> {
        return this.api.cancelExerciseGeneration(exerciseId, jobId);
    }

    revertExerciseGeneration(exerciseId: number, runId: string): Observable<ExerciseGenerationRevertResult> {
        return this.api.revertExerciseGeneration(exerciseId, runId);
    }

    subscribeToStream(jobId: string): Observable<HyperionGenerationMessage> {
        return this.websocketService.subscribe<HyperionGenerationMessage>(`/user/topic/hyperion/exercise-generation/jobs/${jobId}`);
    }

    /** Subscribes to the shared lock state for one exercise. Generation details remain on the owner-only job topic. */
    subscribeToExerciseState(exerciseId: number): Observable<HyperionExerciseGenerationState> {
        return this.websocketService.subscribe<HyperionExerciseGenerationState>(`/topic/hyperion/exercise-generation/exercises/${exerciseId}/state`);
    }
}
