import { Rating } from 'app/entities/rating.model';
import { of, Observable } from 'rxjs';

export class MockRatingService {
    createRating = (rating: Rating): Observable<Rating> => of();
    updateRating = (rating: Rating): Observable<Rating> => of();
    getRating = (ratingId: number): Observable<Rating | null> => of();
}
