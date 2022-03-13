import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Rating } from 'app/entities/rating.model';

@Injectable({
    providedIn: 'root',
})
export class RatingService {
    private ratingResourceUrl = SERVER_API_URL + 'api/results/';

    constructor(private http: HttpClient) {}

    /**
     * Create the student rating for feedback on the server.
     * @param rating - Rating for the result
     * @param resultId - Id of the linked result
     */
    createRating(rating: number, resultId: number): Observable<Rating> {
        return this.http.post<Rating>(this.ratingResourceUrl + `${resultId}/rating/${rating}`, rating);
    }

    /**
     * Get rating for "resultId" Result
     * @param ratingId - Id of Result who's rating is received
     */
    getRating(ratingId: number): Observable<Rating | null> {
        return this.http.get<Rating | null>(this.ratingResourceUrl + `${ratingId}/rating`);
    }

    /**
     * Update rating for "resultId" Result
     * @param rating - Rating for the result (1- 5)
     * @param resultId - Id of the linked result
     */
    updateRating(rating: number, resultId: number): Observable<Rating> {
        return this.http.put<Rating>(this.ratingResourceUrl + `${resultId}/rating/${rating}`, null);
    }

    /**
     * Get all ratings for the "courseId" course
     * @param courseId - Id of the course
     */
    getRatingsForDashboard(courseId: number): Observable<Rating[]> {
        return this.http.get<Rating[]>(`api/course/${courseId}/rating`);
    }
}
