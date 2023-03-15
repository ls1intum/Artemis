import { Observable, of } from 'rxjs';

import { Rating } from 'app/entities/rating.model';

export class MockRatingService {
    createRating = (rating: Rating): Observable<Rating> => of(rating);
    updateRating = (rating: Rating): Observable<Rating> => of(rating);
    getRating = (ratingId: number): Observable<Rating | null> => of(null);
}
