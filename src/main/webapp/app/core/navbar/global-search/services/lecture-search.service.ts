import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';

@Injectable({
    providedIn: 'root',
})
export class LectureSearchService {
    private readonly http = inject(HttpClient);

    search(query: string, limit = 10): Observable<LectureSearchResult[]> {
        return this.http.post<LectureSearchResult[]>('api/iris/lecture-search', { query, limit });
    }
}
