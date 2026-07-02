import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/foundation/service/base-api-http.service';
import { CompetencyOrchestrationResultDTO } from 'app/atlas/shared/dto/competency-orchestration-dto';

/**
 * Global default values that a course's per-course auto-orchestration overrides fall back to when
 * left empty. Sourced from the server-side `AtlasOrchestratorProperties`.
 */
export interface OrchestratorDefaults {
    debounceWindowSeconds: number;
    maxDailyOrchestrations: number;
}

@Injectable({ providedIn: 'root' })
export class CompetencyOrchestrationApiService extends BaseApiHttpService {
    private readonly basePath = 'atlas/orchestrator';

    async runForProgrammingExercise(exerciseId: number): Promise<CompetencyOrchestrationResultDTO> {
        return await this.post<CompetencyOrchestrationResultDTO>(`${this.basePath}/programming-exercises/${exerciseId}/run`);
    }

    /**
     * Fetches the global default debounce window and daily run cap, used by the course-settings form
     * to show instructors what an empty per-course override resolves to.
     */
    async getDefaults(): Promise<OrchestratorDefaults> {
        return await this.get<OrchestratorDefaults>(`${this.basePath}/defaults`);
    }
}
