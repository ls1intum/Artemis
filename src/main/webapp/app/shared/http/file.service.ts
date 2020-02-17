import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';

import { SERVER_API_URL } from 'app/app.constants';
import { ProgrammingLanguage } from 'app/entities/programming-exercise/programming-exercise.model';

@Injectable({ providedIn: 'root' })
export class FileService {
    private resourceUrl = SERVER_API_URL + 'api/files';

    constructor(private http: HttpClient) {}

    getTemplateFile(filename: string, language?: ProgrammingLanguage) {
        const languagePrefix = !!language ? `${language}/` : '';
        return this.http.get<string>(`${this.resourceUrl}/templates/${languagePrefix}${filename}`, { responseType: 'text' as 'json' });
    }

    /**
     * Requests an access token from the server to download the file. If the access token was generated successfully, the file is then downloaded.
     *
     * @param downloadUrl url that is stored in the attachment model
     */
    downloadAttachment(downloadUrl: string) {
        const downloadUrlComponents = downloadUrl.split('/');
        // take the last element
        const fileName = downloadUrlComponents.pop()!;
        const restOfUrl = downloadUrlComponents.join('/');
        const normalizedDownloadUrl = restOfUrl + '/' + encodeURIComponent(fileName);
        const newWindow = window.open('about:blank');
        this.http
            .get('api/files/attachments/access-token/' + fileName, { observe: 'response', responseType: 'text' })
            .toPromise()
            .then(
                (result: HttpResponse<String>) => {
                    newWindow!.location.href = `${normalizedDownloadUrl}?access_token=${result.body}`;
                },
                () => {
                    newWindow!.close();
                },
            );
        return newWindow;
    }
}
