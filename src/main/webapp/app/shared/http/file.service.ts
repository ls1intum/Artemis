import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

import { SERVER_API_URL } from 'app/app.constants';
import { ProgrammingLanguage } from 'app/entities/programming-exercise';

@Injectable({ providedIn: 'root' })
export class FileService {
    private resourceUrl = SERVER_API_URL + 'api/files';

    constructor(private http: HttpClient) {}

    getTemplateFile(filename: string, language?: ProgrammingLanguage) {
        const languagePrefix = !!language ? `${language}/` : '';
        return this.http.get<string>(`${this.resourceUrl}/templates/${languagePrefix}${filename}`, { responseType: 'text' as 'json' });
    }
}
