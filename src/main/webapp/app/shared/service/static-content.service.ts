import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class StaticContentService {
    private readonly staticContentUrl = SERVER_API_URL + 'public/content/';

    constructor(private http: HttpClient) {}

    /**
     * Gets the content of the file as a safe html text.
     * @param filename The name of the file as a string.
     */
    getStaticHtmlFromArtemisServer(filename: string): Observable<SafeHtml> {
        return this.http.get(`${this.staticContentUrl}${filename}`, { responseType: 'text' });
    }

    /**
     * Gets the content of the file.
     * @param filename The name of the file as a string.
     */
    getStaticJsonFromArtemisServer(filename: string): Observable<any> {
        return this.http.get(`${this.staticContentUrl}${filename}`);
    }
}
