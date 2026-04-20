import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class LectureSearchService {
    private readonly http = inject(HttpClient);

    search(query: string, limit = 10): Observable<LectureSearchResult[]> {
        return this.http.post<LectureSearchResult[]>('api/iris/lecture-search', { query, limit });
    }

    ask(query: string, limit = 5): Observable<{ token: string }> {
        return this.http.post<{ token: string }>('api/iris/search-answer', { query, limit });
    }
}
