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
    // eslint-disable-next-line @typescript-eslint/no-explicit-any -- returns arbitrary, untyped JSON; the sole caller (core/about-us) relies on structural access (spread + `.contributors`) that `unknown` would break, and it lives outside this module so its call site cannot be re-typed here
    getStaticJsonFromArtemisServer(filename: string): Observable<any> {
        return this.http.get(`${this.STATIC_CONTENT_URL}${filename}`);
    }
}
