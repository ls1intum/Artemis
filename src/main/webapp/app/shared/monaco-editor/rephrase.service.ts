import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

/**
 * Service providing shared functionality for rephrasing context of the markdown editor.
 * This service is intended to be used by components that need to rephrase text of the Monaco editors.
 */

@Injectable({ providedIn: 'root' })
export class RephraseService {
    public resourceUrl = 'api/courses';

    private http = inject(HttpClient);
    /**
     * Rephrases the given.
     * @returns The rephrased text.
     */
    rephraseMarkdown(text: string): Observable<string> {
        return this.http.get<{ rephrasedText: string }>(`${this.resourceUrl}/rephraseText`, { params: { text } }).pipe(
            map((res: { rephrasedText: string }) => {
                return res.rephrasedText; // Gibt nur den umformulierten Text zurück
            }),
        );
    }
}
