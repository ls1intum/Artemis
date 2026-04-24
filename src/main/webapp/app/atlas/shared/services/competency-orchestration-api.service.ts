import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';
import { CompetencyOrchestrationResultDTO } from 'app/atlas/shared/dto/competency-orchestration-dto';

@Injectable({ providedIn: 'root' })
export class CompetencyOrchestrationApiService extends BaseApiHttpService {
    private readonly basePath = 'atlas/orchestrator';

    async runForProgrammingExercise(exerciseId: number): Promise<CompetencyOrchestrationResultDTO> {
        return await this.post<CompetencyOrchestrationResultDTO>(`${this.basePath}/programming-exercises/${exerciseId}/run`, {});
    }
}
