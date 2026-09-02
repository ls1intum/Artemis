import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { HyperionExerciseGenerationApi } from 'app/openapi/api/hyperion-exercise-generation-api';
import {
    ExerciseGenerationRevertResult,
    HyperionExerciseGenerationState,
    HyperionGenerationJobStart,
    HyperionGenerationMessage,
    HyperionGenerationRequest,
    HyperionGenerationStatus,
    HyperionTitleSuggestion,
} from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

/** Client boundary for whole-exercise generation and adaptation. */
@Injectable({ providedIn: 'root' })
export class HyperionExerciseGenerationService {
    private readonly api = inject(HyperionExerciseGenerationApi);
    private readonly websocketService = inject(WebsocketService);

    generate(exerciseId: number, request: HyperionGenerationRequest): Observable<HyperionGenerationJobStart> {
        return this.api.generateExercise(exerciseId, request);
    }

    /** Asks for a draft title for the exercise a brief describes. The server answers with a usable title even when the model does not, so this only errors on transport failures. */
    suggestTitle(courseId: number, prompt: string): Observable<HyperionTitleSuggestion> {
        return this.api.suggestGenerationTitle(courseId, { prompt });
    }

    getStatus(exerciseId: number): Observable<HyperionGenerationStatus | null> {
        return this.api.getExerciseGenerationStatus(exerciseId);
    }

    cancel(exerciseId: number, jobId: string): Observable<void> {
        return this.api.cancelExerciseGeneration(exerciseId, jobId);
    }

    revertExerciseGeneration(exerciseId: number): Observable<ExerciseGenerationRevertResult> {
        return this.api.revertExerciseGeneration(exerciseId);
    }

    subscribeToStream(jobId: string): Observable<HyperionGenerationMessage> {
        return this.websocketService.subscribe<HyperionGenerationMessage>(`/user/topic/hyperion/exercise-generation/jobs/${jobId}`);
    }

    /** Subscribes to the shared lock state for one exercise. Generation details remain on the owner-only job topic. */
    subscribeToExerciseState(exerciseId: number): Observable<HyperionExerciseGenerationState> {
        return this.websocketService.subscribe<HyperionExerciseGenerationState>(`/topic/hyperion/exercise-generation/exercises/${exerciseId}/state`);
    }
}
