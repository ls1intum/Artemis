import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { lastValueFrom } from 'rxjs';
import { v4 as uuid } from 'uuid';

import { ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';

@Injectable({ providedIn: 'root' })
export class FileService {
    private resourceUrl = 'api/files';

    constructor(private http: HttpClient) {}

    /**
     * Fetches the template file for the given programming language
     * @param {string} filename
     * @param {ProgrammingLanguage} language
     * @param {ProjectType} projectType (if available)
     * @returns json test file
     */
    getTemplateFile(filename: string, language?: ProgrammingLanguage, projectType?: ProjectType) {
        const languagePrefix = language ? `${language}/` : '';
        const projectTypePrefix = projectType ? `${projectType}/` : '';
        return this.http.get<string>(`${this.resourceUrl}/templates/${languagePrefix}${projectTypePrefix}${filename}`, { responseType: 'text' as 'json' });
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
     * @param courseId: the id of the course
     * @param lectureId: the id of the lecture
     */
    downloadMergedFile(courseId: number, lectureId: number) {
        const newWindow = window.open('about:blank');
        newWindow!.location.href = `api/files/attachments/lecture/${lectureId}/merge-pdf`;
        return newWindow;
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
