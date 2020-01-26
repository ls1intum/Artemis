import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable } from 'rxjs';
import { SafeHtml } from '@angular/platform-browser';

@Injectable({ providedIn: 'root' })
export class StaticHtmlContentService {
    private readonly staticContentUrl = SERVER_API_URL + 'public/content/';

    constructor(private http: HttpClient) {}

    getStaticHtmlFromArtemisServer(filename: string): Observable<SafeHtml> {
        return this.http.get(`${this.staticContentUrl}${filename}`, { responseType: 'text' });
    }
}
