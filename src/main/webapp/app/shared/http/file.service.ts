import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { lastValueFrom } from 'rxjs';
import { v4 as uuid } from 'uuid';
import { Observable } from 'rxjs';

import { ProgrammingLanguage, ProjectType } from 'app/entities/programming/programming-exercise.model';

@Injectable({ providedIn: 'root' })
export class FileService {
    private resourceUrl = 'api/files';

    constructor(private http: HttpClient) {}

    /**
     * Fetches the template file for the given programming language
     * @param {ProgrammingLanguage} language
     * @param {ProjectType} projectType (if available)
     * @returns json test file
     */
    getTemplateFile(language: ProgrammingLanguage, projectType?: ProjectType): Observable<string> {
        const urlParts: string[] = [language];
        if (projectType) {
            urlParts.push(projectType);
        }
        return this.http.get<string>(`${this.resourceUrl}/templates/` + urlParts.join('/'), { responseType: 'text' as 'json' });
    }

    /**
     * Fetches the file from the given path and returns it as a File object with a unique file name.
     * @param filePath path of the file
     * @param mapOfFiles optional map to check if the generated file name already exists
     */
    async getFile(filePath: string, mapOfFiles?: Map<string, { path?: string; file: File }>): Promise<File> {
        const blob = await lastValueFrom(this.http.get(filePath, { responseType: 'blob' }));
        const file = new File([blob], this.getUniqueFileName(this.getExtension(filePath), mapOfFiles));
        return Promise.resolve(file);
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
    getAeolusTemplateFile(language: ProgrammingLanguage, projectType?: ProjectType, staticAnalysis?: boolean, sequentialRuns?: boolean, coverage?: boolean): Observable<string> {
        const urlParts: string[] = [language];
        const params: string[] = [];
        if (projectType) {
            urlParts.push(projectType);
        }
        params.push('staticAnalysis=' + (staticAnalysis == undefined ? false : staticAnalysis));
        params.push('sequentialRuns=' + (sequentialRuns == undefined ? false : sequentialRuns));
        params.push('testCoverage=' + (coverage == undefined ? false : coverage));
        return this.http.get<string>(`${this.resourceUrl}/aeolus/templates/` + urlParts.join('/') + '?' + params.join('&'), { responseType: 'text' as 'json' });
    }

    /**
     * Fetches the template code of conduct
     * @returns markdown file
     */
    getTemplateCodeOfCondcut(): Observable<HttpResponse<string>> {
        return this.http.get<string>(`api/files/templates/code-of-conduct`, { observe: 'response', responseType: 'text' as 'json' });
    }

    /**
     * Downloads the file from the provided downloadUrl.
     *
     * @param downloadUrl url that is stored in the attachment model
     */
    downloadFile(downloadUrl: string) {
        const downloadUrlComponents = downloadUrl.split('/');
        // take the last element
        const fileName = downloadUrlComponents.pop()!;
        const restOfUrl = downloadUrlComponents.join('/');
        const normalizedDownloadUrl = restOfUrl + '/' + encodeURIComponent(fileName);
        const newWindow = window.open('about:blank');
        newWindow!.location.href = normalizedDownloadUrl;
        return newWindow;
    }

    /**
     * Downloads the merged PDF file.
     *
     * @param lectureId the id of the lecture
     */
    downloadMergedFile(lectureId: number): Observable<HttpResponse<Blob>> {
        return this.http.get(`api/files/attachments/lecture/${lectureId}/merge-pdf`, {
            observe: 'response',
            responseType: 'blob',
        });
    }

    /**
     * Returns the file extension of the given filename.
     *
     * @param filename the filename
     */
    getExtension(filename: string): string {
        return filename.split('.').pop()!;
    }

    /**
     * Returns a unique file name with the given extension.
     *
     * @param extension the file extension to add
     * @param mapOfFiles optional map to check if the generated file name already exists
     */
    getUniqueFileName(extension: string, mapOfFiles?: Map<string, { path?: string; file: File }>): string {
        let name;
        do {
            name = uuid() + '.' + extension;
        } while (mapOfFiles && mapOfFiles.has(name));
        return name;
    }
}
