import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

import { ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';

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
    getTemplateFile(language: ProgrammingLanguage, projectType?: ProjectType) {
        const urlParts: string[] = [language];
        if (projectType) {
            urlParts.push(projectType);
        }
        return this.http.get<string>(`${this.resourceUrl}/templates/` + urlParts.join('/'), { responseType: 'text' as 'json' });
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
}
