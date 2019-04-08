import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

import { SERVER_API_URL } from '../../app.constants';

@Injectable({ providedIn: 'root' })
export class FileService {
    private resourceUrl = SERVER_API_URL + 'api/files';

    constructor(private http: HttpClient) {}

    getTemplateFile(filename: string) {
        return this.http.get<string>(`${this.resourceUrl}/templates/${filename}`, { responseType: 'text' as 'json' });
    }
}
