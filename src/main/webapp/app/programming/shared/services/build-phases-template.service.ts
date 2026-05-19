import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { BuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { EMPTY, Subject } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';

@Injectable()
export class BuildPhasesTemplateService {
    private http = inject(HttpClient);

    private resourceUrl = 'api/programming/phases/templates';

    // shared signal
    readonly buildPlan = signal<BuildPlanPhases | undefined>(BuildPhasesTemplateService.createDefaultBuildPlan());

    private fetchTemplateSubject = new Subject<{
        examMode: boolean;
        language: ProgrammingLanguage;
        projectType?: ProjectType;
        staticAnalysis?: boolean;
        sequentialRuns?: boolean;
    }>();

    constructor() {
        this.fetchTemplateSubject
            .pipe(
                switchMap(({ examMode, language, projectType, staticAnalysis, sequentialRuns }) => {
                    const uriWithParams = this.buildURIWithParams(examMode, language, projectType, staticAnalysis, sequentialRuns);

                    return this.http.get<BuildPlanPhases>(`${this.resourceUrl}/${uriWithParams.uri}`, { params: uriWithParams.params }).pipe(
                        catchError(() => {
                            this.resetToDefault();
                            return EMPTY;
                        }),
                    );
                }),
            )
            .subscribe((result) => {
                this.buildPlan.set(result);
            });
    }

    /**
     * Fetches the build plan template for the given programming language and project type from the server.
     * Updates the shared buildPlan signal with the result.
     *
     * @param examMode whether the template should be used in an exam
     * @param language the programming language for which the template should be fetched
     * @param projectType the project type for which the template should be fetched
     * @param staticAnalysis whether the static analysis template should be used
     * @param sequentialRuns whether the sequential runs template should be used
     */
    fetchTemplate(examMode: boolean, language: ProgrammingLanguage, projectType?: ProjectType, staticAnalysis?: boolean, sequentialRuns?: boolean): void {
        this.fetchTemplateSubject.next({ language, projectType, staticAnalysis, sequentialRuns, examMode });
    }

    /**
     * Resets the shared buildPlan signal to the default build plan.
     */
    resetToDefault(): void {
        this.buildPlan.set(BuildPhasesTemplateService.createDefaultBuildPlan());
    }

    private buildURIWithParams(
        examMode: boolean,
        language: ProgrammingLanguage,
        projectType?: ProjectType,
        staticAnalysis?: boolean,
        sequentialRuns?: boolean,
    ): { uri: string; params: any } {
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

    private static createDefaultBuildPlan(): BuildPlanPhases {
        return {
            phases: [
                {
                    name: '',
                    script: '# enter the script of this phase',
                    condition: 'ALWAYS',
                    forceRun: false,
                    resultPaths: [],
                },
            ],
        };
    }
}
