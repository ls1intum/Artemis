import { Rating } from 'app/entities/rating.model';
import { Observable, of } from 'rxjs';

export class MockRatingService {
    createRating = (rating: Rating): Observable<Rating> => of(rating);
    updateRating = (rating: Rating): Observable<Rating> => of(rating);
    getRating = (ratingId: number): Observable<Rating | null> => of(null);
}
