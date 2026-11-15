import { Service, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { RatingListItem } from 'app/assessment/shared/entities/rating-list-item.model';
import { PageableResult } from 'app/foundation/pagination/pageable-table';

/**
 * Kept only for the instructor dashboard list: the generated client does not expose the `X-Total-Count` header that carries the page count.
 */
@Service()
export class RatingService {
    private http = inject(HttpClient);

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

        return this.http.get<RatingListItem[]>(`api/assessment/courses/${courseId}/rating`, { params, observe: 'response' }).pipe(
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
