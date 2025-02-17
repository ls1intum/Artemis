import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Rating } from 'app/entities/rating.model';

@Injectable({
    providedIn: 'root',
})
export class RatingService {
    private http = inject(HttpClient);

    private ratingResourceUrl = 'api/results/';

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
    getRating(resultId: number): Observable<number | null> {
        return this.http.get<number | null>(this.ratingResourceUrl + `${resultId}/rating`);
    }

    /**
     * Update rating for "resultId" Result
     * @param rating - star rating for the result
     * @param resultId - id of the linked result
     */
    updateRating(rating: number, resultId: number): Observable<number> {
        return this.http.put<number>(this.ratingResourceUrl + `${resultId}/rating/${rating}`, null);
    }

    /**
     * Get all ratings for the "courseId" course
     * @param courseId - Id of the course
     */
    getRatingsForDashboard(courseId: number): Observable<Rating[]> {
        return this.http.get<Rating[]>(`api/course/${courseId}/rating`);
    }
}
