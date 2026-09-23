import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';

@Injectable({
    providedIn: 'root',
})
export class LectureSearchService {
    private readonly http = inject(HttpClient);

    /**
     * Searches lecture content (slides and video transcripts) through Iris.
     *
     * @param query            the search term
     * @param limit            the maximum number of hits
     * @param courseIds        the courses to search, or undefined to search everything the user can reach
     * @param excludeCourseIds the courses to hide from the search
     * @return the matching content hits
     */
    search(query: string, limit = 10, courseIds?: number[], excludeCourseIds?: number[]): Observable<LectureSearchResult[]> {
        const body: { query: string; limit: number; courseIds?: number[]; excludeCourseIds?: number[] } = { query, limit };
        if (courseIds?.length) {
            body.courseIds = courseIds;
        }
        if (excludeCourseIds?.length) {
            body.excludeCourseIds = excludeCourseIds;
        }
        return this.http.post<LectureSearchResult[]>('api/iris/lecture-search', body);
    }
}
