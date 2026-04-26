import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class BuildPhasesTemplateService {
    private http = inject(HttpClient);

    private resourceUrl = 'api/programming/phases/templates';

    getTemplate(language: ProgrammingLanguage, projectType?: ProjectType, staticAnalysis?: boolean, sequentialRuns?: boolean): Observable<BuildPlanPhases> {
        const uriWithParams = this.buildURIWithParams(language, projectType, staticAnalysis, sequentialRuns);
        return this.http.get<BuildPlanPhases>(`${this.resourceUrl}/` + uriWithParams.uri, {
            params: uriWithParams.params,
        });
    }

    private buildURIWithParams(language: ProgrammingLanguage, projectType?: ProjectType, staticAnalysis?: boolean, sequentialRuns?: boolean): { uri: string; params: any } {
        const path: string = [language, projectType].filter(Boolean).join('/');
        const params = {
            staticAnalysis: !!staticAnalysis,
            sequentialRuns: !!sequentialRuns,
        };
        return {
            uri: path,
            params: params,
        };
    }
}
