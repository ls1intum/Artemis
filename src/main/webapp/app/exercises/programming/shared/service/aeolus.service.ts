import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

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
                    response = this.parseWindFile(file);
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

    /**
     * Parses the given windfile, the input is a json string, the output is a WindFile object
     * @param file the json string
     */
    parseWindFile(file: string): WindFile | undefined {
        try {
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
                if (action) {
                    action.parameters = new Map<string, string | boolean | number>();
                    if (anyAction.parameters) {
                        for (const key of Object.keys(anyAction.parameters)) {
                            action.parameters.set(key, anyAction.parameters[key]);
                        }
                    }
                    actions.push(action);
                }
            });
            // somehow, the returned content has a scriptActions field, which is not defined in the WindFile class
            delete windFile['scriptActions'];
            windFile.actions = actions;
            return windFile;
        } catch (SyntaxError) {
            return undefined;
        }
    }

    /**
     * Generates a preview of the given windfile
     * @param {WindFile} windfile
     * @returns {Observable<string>} the generated preview
     */
    generatePreview(windfile: WindFile): AeolusPreview | undefined {
        const headers = { 'Content-Type': 'application/json' };
        windfile.metadata.id = 'not-important-for-preview';
        windfile.metadata.name = 'not-important-for-preview';
        windfile.metadata.description = 'not-important-for-preview';
        let response = undefined;
        this.http
            .post<string>(`${this.resourceUrl}/preview/CLI`, this.serializeWindFile(windfile), {
                responseType: 'text' as 'json',
                headers,
            })
            .subscribe({
                next: (preview) => {
                    response = Object.assign({}, JSON.parse(preview));
                },
                error: () => {
                    response = undefined;
                },
            });
        return response;
    }

    serializeWindFile(windFile: WindFile): string {
        return JSON.stringify(windFile, this.replacer);
    }

    /**
     * This takes care of serializing maps in the windfile
     * @param _ key of the entry, not needed
     * @param value value of the entry
     */
    replacer(_: any, value: any): any {
        if (value instanceof Map) {
            const object: any = {};
            value.forEach((v, k) => {
                object[k] = v;
            });
            return object;
        } else {
            return value;
        }
    }
}
