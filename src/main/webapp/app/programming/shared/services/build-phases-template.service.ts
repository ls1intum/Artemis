import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class BuildPhasesTemplateService {
    private http = inject(HttpClient);

    private resourceUrl = 'api/localci/phases/templates';

    /**
     * Fetches the build plan template for the given programming language and project type from the server.
     *
     * @param examMode whether the template should be used in an exam
     * @param language the programming language for which the template should be fetched
     * @param projectType the project type for which the template should be fetched
     * @param staticAnalysis whether the static analysis template should be used
     * @param sequentialRuns whether the sequential runs template should be used
     * @return an observable that emits the fetched build plan template once
     */
    getTemplate(examMode: boolean, language: ProgrammingLanguage, projectType?: ProjectType, staticAnalysis?: boolean, sequentialRuns?: boolean): Observable<BuildPlanPhases> {
        const uriWithParams = this.buildURIWithParams(examMode, language, projectType, staticAnalysis, sequentialRuns);
        return this.http.get<BuildPlanPhases>(`${this.resourceUrl}/${uriWithParams.uri}`, { params: uriWithParams.params });
    }

    private buildURIWithParams(
        examMode: boolean,
        language: ProgrammingLanguage,
        projectType?: ProjectType,
        staticAnalysis?: boolean,
        sequentialRuns?: boolean,
    ): { uri: string; params: { staticAnalysis: boolean; sequentialRuns: boolean; examMode: boolean } } {
        const path: string = [language, projectType].filter(Boolean).join('/');
        const params = {
            staticAnalysis: !!staticAnalysis,
            sequentialRuns: !!sequentialRuns,
            examMode: examMode,
        };
        return {
            uri: path,
            params: params,
        };
    }
}
