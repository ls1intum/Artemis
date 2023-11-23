import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ProgrammingLanguage, ProjectType, WindFile } from 'app/entities/programming-exercise.model';

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
        const path: string = [language, projectType].filter(Boolean).join('/');
        const params = {
            staticAnalysis: !!staticAnalysis,
            sequentialRuns: !!sequentialRuns,
            testCoverage: !!coverage,
        };
        return this.http.get<string>(`${this.resourceUrl}/templates/` + path, { responseType: 'text' as 'json', params });
    }

    generatePreview(windfile: WindFile): Observable<string> {
        return this.http.post<string>(`http://localhost:8090/generate/cli`, windfile, { responseType: 'text' as 'json' });
    }
}
