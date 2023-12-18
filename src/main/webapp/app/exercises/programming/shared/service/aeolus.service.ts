import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { BuildAction, PlatformAction, ProgrammingLanguage, ProjectType, ScriptAction, WindFile } from 'app/entities/programming-exercise.model';

export interface AeolusPreview {
    result: string;
    key?: string;
}

@Injectable({ providedIn: 'root' })
export class AeolusService {
    private resourceUrl = 'api/aeolus';

    constructor(private http: HttpClient) {}

    /**
     * Fetches the aeolus template file for the given programming language
     * @param {ProgrammingLanguage} language
     * @param {ProjectType} projectType (if available)
     * @param staticAnalysis (if available) whether static code analysis should be enabled
     * @param sequentialRuns (if available) whether sequential test runs should be enabled
     * @param coverage (if available) whether test coverage should be enabled
     * @returns WindFile or undefined if no template is available
     */
    getAeolusTemplateFile(language: ProgrammingLanguage, projectType?: ProjectType, staticAnalysis?: boolean, sequentialRuns?: boolean, coverage?: boolean): WindFile | undefined {
        const path: string = [language, projectType].filter(Boolean).join('/');
        const params = {
            staticAnalysis: !!staticAnalysis,
            sequentialRuns: !!sequentialRuns,
            testCoverage: !!coverage,
        };
        let response: WindFile | undefined = undefined;
        this.http
            .get<string>(`${this.resourceUrl}/templates/` + path, {
                responseType: 'text' as 'json',
                params,
            })
            .subscribe({
                next: (file) => {
                    if (file) {
                        const templateFile: WindFile = JSON.parse(file);
                        const windFile: WindFile = Object.assign(new WindFile(), templateFile);
                        const actions: BuildAction[] = [];
                        templateFile.actions.forEach((anyAction: any) => {
                            let action: BuildAction | undefined = undefined;
                            if (anyAction.script) {
                                action = Object.assign(new ScriptAction(), anyAction);
                            } else {
                                action = Object.assign(new PlatformAction(), anyAction);
                            }
                            if (!action) {
                                return;
                            }
                            action.parameters = new Map<string, string | boolean | number>();
                            if (anyAction.parameters) {
                                for (const key of Object.keys(anyAction.parameters)) {
                                    action.parameters.set(key, anyAction.parameters[key]);
                                }
                            }
                            actions.push(action);
                        });
                        // somehow, the returned content has a scriptActions field, which is not defined in the WindFile class
                        delete windFile['scriptActions'];
                        windFile.actions = actions;
                        response = windFile;
                    }
                },
                error: () => {
                    response = undefined;
                },
            });
        return response;
    }

    /**
     * Fetches the aeolus template file for the given programming language
     * @param {ProgrammingLanguage} language
     * @param {ProjectType} projectType (if available)
     * @param staticAnalysis (if available) whether static code analysis should be enabled
     * @param sequentialRuns (if available) whether sequential test runs should be enabled
     * @param coverage (if available) whether test coverage should be enabled
     * @returns json test file
     */
    getAeolusTemplateScript(language: ProgrammingLanguage, projectType?: ProjectType, staticAnalysis?: boolean, sequentialRuns?: boolean, coverage?: boolean): string | undefined {
        const path: string = [language, projectType].filter(Boolean).join('/');
        const params = {
            staticAnalysis: !!staticAnalysis,
            sequentialRuns: !!sequentialRuns,
            testCoverage: !!coverage,
        };
        let response: string | undefined = undefined;
        this.http
            .get<string>(`${this.resourceUrl}/templateScripts/` + path, {
                responseType: 'text' as 'json',
                params,
            })
            .subscribe({
                next: (file) => {
                    response = file;
                },
                error: () => {
                    response = undefined;
                },
            });
        return response;
    }

    generatePreview(windfile: WindFile): Observable<string> {
        const headers = { 'Content-Type': 'application/json' };
        windfile.metadata.id = 'testing';
        windfile.metadata.name = 'testing';
        windfile.metadata.description = 'testing';
        return this.http.post<string>(`http://localhost:8090/generate/cli`, JSON.stringify(windfile), {
            responseType: 'text' as 'json',
            headers,
        });
    }
}
