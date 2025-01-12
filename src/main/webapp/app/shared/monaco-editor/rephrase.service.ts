import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

enum RephrasingVariant {
    FAQ = 'FAQ',
    PROBLEM_STATEMENT = 'PROBLEM_STATEMENT',
}

export default RephrasingVariant;

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
    rephraseMarkdown(toBeRephrased: string, rephrasingVariant: RephrasingVariant): Observable<string> {
        const courseId = 1;
        return this.http
            .get<{ rephrasedText: string }>(`${this.resourceUrl}/${courseId}/rephraseText`, {
                params: {
                    toBeRephrased: toBeRephrased,
                    variant: rephrasingVariant,
                },
            })
            .pipe(
                map((res: { rephrasedText: string }) => {
                    return res.rephrasedText;
                }),
            );
    }
}
