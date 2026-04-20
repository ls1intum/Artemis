import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';

export enum CompetencyOrchestrationStatus {
    Success = 'SUCCESS',
    Preview = 'PREVIEW',
    Failed = 'FAILED',
    InProgress = 'IN_PROGRESS',
}

export enum AppliedActionType {
    Create = 'CREATE',
    Edit = 'EDIT',
    Assign = 'ASSIGN',
    Unassign = 'UNASSIGN',
    Delete = 'DELETE',
}

export interface AppliedActionDTO {
    type: AppliedActionType;
    competencyId?: number;
    competencyTitle?: string;
    exerciseId?: number;
    weight?: number;
    detail?: string;
    justification?: string;
}

export interface CompetencyOrchestrationResultDTO {
    status: CompetencyOrchestrationStatus;
    message?: string;
    appliedActions?: AppliedActionDTO[];
}

@Injectable({ providedIn: 'root' })
export class CompetencyOrchestrationApiService extends BaseApiHttpService {
    private readonly basePath = 'atlas/orchestrator';

    async runForProgrammingExercise(exerciseId: number): Promise<CompetencyOrchestrationResultDTO> {
        return await this.post<CompetencyOrchestrationResultDTO>(`${this.basePath}/programming-exercises/${exerciseId}/run`, {});
    }
}
