import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BuildPlan } from 'app/entities/programming/build-plan.model';

export type EntityResponseType = HttpResponse<BuildPlan>;

@Injectable({ providedIn: 'root' })
export class BuildPlanService {
    public resourceUrl = `/api/programming-exercises`;

    constructor(private http: HttpClient) {}

    getBuildPlan(programmingExerciseId: number): Observable<EntityResponseType> {
        return this.http.get<BuildPlan>(`${this.resourceUrl}/${programmingExerciseId}/build-plan/for-editor`, { observe: 'response' });
    }

    putBuildPlan(programmingExerciseId: number, buildPlan: BuildPlan): Observable<EntityResponseType> {
        return this.http.put<BuildPlan>(`${this.resourceUrl}/${programmingExerciseId}/build-plan`, buildPlan, { observe: 'response' });
    }
}
