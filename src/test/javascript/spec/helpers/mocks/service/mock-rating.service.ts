import { Observable, of } from 'rxjs';

/**
 * Mirrors the signatures of `RatingService`, which works with the rating value itself rather than an entity.
 */
export class MockRatingService {
    createRating = (rating: number, resultId: number): Observable<number> => of(rating);
    updateRating = (rating: number, resultId: number): Observable<number> => of(rating);
    getRating = (resultId: number): Observable<number | undefined> => of(undefined);
}
