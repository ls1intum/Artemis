import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SafeHtml } from '@angular/platform-browser';

@Injectable({ providedIn: 'root' })
export class StaticContentService {
    private http = inject(HttpClient);

    private readonly STATIC_CONTENT_URL = 'public/content/';

    /**
     * Gets the content of the file as a safe html text.
     * @param filename The name of the file as a string.
     */
    getStaticHtmlFromArtemisServer(filename: string): Observable<SafeHtml> {
        return this.http.get(`${this.STATIC_CONTENT_URL}${filename}`, { responseType: 'text' });
    }

    /**
     * Gets the content of the file.
     * @param filename The name of the file as a string.
     */
    getStaticJsonFromArtemisServer(filename: string): Observable<any> {
        return this.http.get(`${this.STATIC_CONTENT_URL}${filename}`);
    }
}
