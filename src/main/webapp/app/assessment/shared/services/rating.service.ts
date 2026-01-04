import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { RatingListItem } from 'app/assessment/shared/entities/rating-list-item.model';
import { PageableResult } from 'app/shared/table/pageable-table';

@Injectable({
    providedIn: 'root',
})
export class RatingService {
    private http = inject(HttpClient);

    private ratingResourceUrl = 'api/assessment/results/';

    /**
     * Create the student rating for feedback on the server.
     * @param rating - star rating for the result
     * @param resultId - id of the linked result
     */
    createRating(rating: number, resultId: number): Observable<number> {
        return this.http.post<number>(this.ratingResourceUrl + `${resultId}/rating/${rating}`, null);
    }

    /**
     * Get rating for "resultId" Result
     * @param resultId - id of result who's rating is received
     */
    getRating(resultId: number): Observable<number | undefined> {
        return this.http.get<number | undefined>(this.ratingResourceUrl + `${resultId}/rating`);
    }

    /**
     * Update rating for "resultId" Result
     * @param rating - star rating for the result
     * @param resultId - id of the linked result
     */
    updateRating(rating: number, resultId: number): Observable<number> {
        return this.http.put<number>(this.ratingResourceUrl + `${resultId}/rating/${rating}`, undefined);
    }

    /**
     * Get paginated ratings for the "courseId" course.
     * Pagination info is returned in HTTP headers (X-Total-Count).
     * @param courseId - Id of the course
     * @param page - page number (0-indexed)
     * @param size - number of items per page
     * @param sort - sort field and direction (e.g., 'id,desc')
     */
    getRatingsForDashboard(courseId: number, page: number = 0, size: number = 20, sort?: string): Observable<PageableResult<RatingListItem>> {
        let params = new HttpParams().set('page', page.toString()).set('size', size.toString());

        if (sort) {
            params = params.set('sort', sort);
        }

        return this.http.get<RatingListItem[]>(`api/assessment/course/${courseId}/rating`, { params, observe: 'response' }).pipe(
            map((response: HttpResponse<RatingListItem[]>) => {
                const totalCount = parseInt(response.headers.get('X-Total-Count') ?? '0', 10);
                const totalPages = Math.ceil(totalCount / size);
                return {
                    content: response.body ?? [],
                    totalElements: totalCount,
                    totalPages,
                };
            }),
        );
    }
}
