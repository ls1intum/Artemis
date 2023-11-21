import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';

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
     * @returns json test file
     */
    getAeolusTemplateFile(language: ProgrammingLanguage, projectType?: ProjectType, staticAnalysis?: boolean, sequentialRuns?: boolean, coverage?: boolean): Observable<string> {
        const urlParts: string[] = [language];
        const params: string[] = [];
        if (projectType) {
            urlParts.push(projectType);
        }
        params.push('staticAnalysis=' + (staticAnalysis == undefined ? false : staticAnalysis));
        params.push('sequentialRuns=' + (sequentialRuns == undefined ? false : sequentialRuns));
        params.push('testCoverage=' + (coverage == undefined ? false : coverage));
        return this.http.get<string>(`${this.resourceUrl}/templates/` + urlParts.join('/') + '?' + params.join('&'), { responseType: 'text' as 'json' });
    }
}
