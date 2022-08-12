import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';

import { ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { lastValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class FileService {
    private resourceUrl = SERVER_API_URL + 'api/files';

    constructor(private http: HttpClient) {}

    /**
     * Fetches the template file for the given programming language
     * @param {string} filename
     * @param {ProgrammingLanguage} language
     * @param {ProjectType} projectType (if available)
     * @returns json test file
     */
    getTemplateFile(filename: string, language?: ProgrammingLanguage, projectType?: ProjectType) {
        const languagePrefix = !!language ? `${language}/` : '';
        const projectTypePrefix = !!projectType ? `${projectType}/` : '';
        return this.http.get<string>(`${this.resourceUrl}/templates/${languagePrefix}${projectTypePrefix}${filename}`, { responseType: 'text' as 'json' });
    }

    /**
     * Requests an access token from the server to download the file. If the access token was generated successfully, the file is then downloaded.
     *
     * @param downloadUrl url that is stored in the attachment model
     */
    downloadFileWithAccessToken(downloadUrl: string) {
        const downloadUrlComponents = downloadUrl.split('/');
        // take the last element
        const fileName = downloadUrlComponents.pop()!;
        const restOfUrl = downloadUrlComponents.join('/');
        const normalizedDownloadUrl = restOfUrl + '/' + encodeURIComponent(fileName);
        const newWindow = window.open('about:blank');
        lastValueFrom(this.http.get(`${normalizedDownloadUrl}/access-token`, { observe: 'response', responseType: 'text' })).then(
            (result: HttpResponse<string>) => {
                newWindow!.location.href = `${normalizedDownloadUrl}?access_token=${result.body}`;
            },
            () => {
                newWindow!.close();
            },
        );
        return newWindow;
    }

    /**
     * Requests an access token from the server to download the file. If the access token was generated successfully, the merged PDF file is then downloaded.
     *
     * @param courseId: the id of the course
     * @param lectureId: the id of the lecture
     */
    downloadMergedFileWithAccessToken(courseId: number, lectureId: number) {
        const newWindow = window.open('about:blank');
        lastValueFrom(this.http.get(`api/files/attachments/course/${courseId}/access-token`, { observe: 'response', responseType: 'text' })).then(
            (result: HttpResponse<string>) => {
                newWindow!.location.href = `api/files/attachments/lecture/${lectureId}/merge-pdf?access_token=${result.body}`;
            },
            () => {
                newWindow!.close();
            },
        );
        return newWindow;
    }
}
