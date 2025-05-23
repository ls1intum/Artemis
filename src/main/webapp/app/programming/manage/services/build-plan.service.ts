import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { BuildPlan } from 'app/programming/shared/entities/build-plan.model';

export type EntityResponseType = HttpResponse<BuildPlan>;

@Injectable({ providedIn: 'root' })
export class BuildPlanService {
    private http = inject(HttpClient);

    public resourceUrl = `/api/programming/programming-exercises`;

    getBuildPlan(programmingExerciseId: number): Observable<EntityResponseType> {
        return this.http.get<BuildPlan>(`${this.resourceUrl}/${programmingExerciseId}/build-plan/for-editor`, { observe: 'response' });
    }

    putBuildPlan(programmingExerciseId: number, buildPlan: BuildPlan): Observable<EntityResponseType> {
        return this.http.put<BuildPlan>(`${this.resourceUrl}/${programmingExerciseId}/build-plan`, buildPlan, { observe: 'response' });
    }
}
