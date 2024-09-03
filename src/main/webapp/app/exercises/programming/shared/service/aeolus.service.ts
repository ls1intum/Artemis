import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BuildAction, PlatformAction, ScriptAction } from 'app/entities/programming/build.action';
import { WindFile } from 'app/entities/programming/wind.file';
import { Observable } from 'rxjs';

import { ProgrammingLanguage, ProjectType } from 'app/entities/programming/programming-exercise.model';

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
    getAeolusTemplateFile(language: ProgrammingLanguage, projectType?: ProjectType, staticAnalysis?: boolean, sequentialRuns?: boolean, coverage?: boolean): Observable<string> {
        const uriWithParams = this.buildURIWithParams(language, projectType, staticAnalysis, sequentialRuns, coverage);
        return this.http.get<string>(`${this.resourceUrl}/templates/` + uriWithParams.uri, {
            responseType: 'text' as 'json',
            params: uriWithParams.params,
        });
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
    getAeolusTemplateScript(language: ProgrammingLanguage, projectType?: ProjectType, staticAnalysis?: boolean, sequentialRuns?: boolean, coverage?: boolean): Observable<string> {
        const uriWithParams = this.buildURIWithParams(language, projectType, staticAnalysis, sequentialRuns, coverage);
        return this.http.get<string>(`${this.resourceUrl}/templateScripts/` + uriWithParams.uri, {
            responseType: 'text' as 'json',
            params: uriWithParams.params,
        });
    }

    /**
     * Parses the given windfile, the input is a json string, the output is a WindFile object
     * @param file the json string
     */
    parseWindFile(file: string): WindFile | undefined {
        try {
            const templateFile: WindFile = JSON.parse(file);
            const windfile: WindFile = Object.assign(new WindFile(), templateFile);
            const actions: BuildAction[] = [];
            templateFile.actions.forEach((anyAction: any) => {
                let action: BuildAction | undefined;
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
            // somehow, the returned content may have a scriptActions field, which is not a field of the WindFile class
            if ('scriptActions' in windfile) {
                delete windfile['scriptActions'];
            }
            windfile.actions = actions;
            return windfile;
        } catch (SyntaxError) {
            return undefined;
        }
    }

    buildURIWithParams(
        language: ProgrammingLanguage,
        projectType?: ProjectType,
        staticAnalysis?: boolean,
        sequentialRuns?: boolean,
        coverage?: boolean,
    ): { uri: string; params: any } {
        const path: string = [language, projectType].filter(Boolean).join('/');
        const params = {
            staticAnalysis: !!staticAnalysis,
            sequentialRuns: !!sequentialRuns,
            testCoverage: !!coverage,
        };
        return {
            uri: path,
            params: params,
        };
    }

    serializeWindFile(windfile: WindFile): string {
        return JSON.stringify(windfile, this.replacer);
    }

    /**
     * Serializes a value, transforming instances of Map into plain objects.
     * This function is designed for use as a replacer function in JSON.stringify.
     *
     * @param _ The key associated with the value being serialized. This is not used in the function, but is required by the JSON.stringify replacer interface.
     * @param value The value to be serialized. If the value is a Map, it will be converted to an object with string keys and values corresponding to the map's entries.
     * @returns If the value is a Map, returns a plain object with keys and values from the map. Otherwise, returns the value unchanged.
     * @template T The type of the value to be serialized. Ensures that the return type matches the type of the input value, except when the value is a Map.
     */
    replacer<T>(_: unknown, value: T): T | Record<string, unknown> {
        if (value instanceof Map) {
            const object: Record<string, unknown> = {};
            value.forEach((value, key) => {
                object[String(key)] = value;
            });
            return object;
        } else {
            return value;
        }
    }
}
