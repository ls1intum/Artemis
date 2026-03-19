import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { IrisSearchResult } from 'app/core/navbar/global-search/models/iris-search-result.model';
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class LectureSearchService {
    private readonly http = inject(HttpClient);
    private readonly baseURL = 'api/iris/lecture-search';

    search(query: string, limit = 10): Observable<LectureSearchResult[]> {
        return this.http.post<LectureSearchResult[]>(this.baseURL, { query, limit });
    }

    ask(query: string, limit = 5): Observable<IrisSearchResult> {
        return this.http.post<IrisSearchResult>(`${this.baseURL}/ask`, { query, limit });
    }
}
