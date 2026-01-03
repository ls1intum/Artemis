import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { RatingService } from 'app/assessment/shared/services/rating.service';
import { RatingListItem } from 'app/assessment/shared/entities/rating-list-item.model';
import { provideHttpClient } from '@angular/common/http';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

describe('Rating Service', () => {
    setupTestBed({ zoneless: true });
    let service: RatingService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(RatingService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('should create a Rating', () => {
        const returnedFromService = 3;
        service.createRating(3, 1).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'POST', url: 'api/assessment/results/1/rating/3' });
        req.flush(returnedFromService);
    });

    it('should get a Rating', () => {
        const ratingValue = 3;
        let expectedResult: number | undefined;
        service
            .getRating(1)
            .pipe(take(1))
            .subscribe((rating) => {
                expectedResult = rating;
            });

        const req = httpMock.expectOne({ method: 'GET', url: 'api/assessment/results/1/rating' });
        req.flush(ratingValue);

        expect(expectedResult).toBe(ratingValue);
    });

    it('should update a Rating', () => {
        const returnedFromService = 5;
        service.updateRating(5, 1).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'PUT', url: 'api/assessment/results/1/rating/5' });
        req.flush(returnedFromService);
    });

    it('should get paginated Ratings for Dashboard with default parameters', () => {
        const mockRatings: RatingListItem[] = [
            {
                id: 1,
                rating: 4,
                assessmentType: AssessmentType.MANUAL,
                assessorLogin: 'tutor1',
                assessorName: 'Tutor One',
                participationId: 10,
                exerciseId: 100,
                exerciseTitle: 'Exercise 1',
                exerciseType: ExerciseType.TEXT,
                resultId: 0,
                submissionId: 0,
            },
        ];

        let result: RatingListItem[] = [];

        service
            .getRatingsForDashboard(123)
            .pipe(take(1))
            .subscribe((response) => {
                result = response.content;
            });

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toBe('api/assessment/course/123/rating');
        expect(req.request.params.get('page')).toBe('0');
        expect(req.request.params.get('size')).toBe('20');
        expect(req.request.params.get('sort')).toBeFalsy();

        req.flush(mockRatings);

        expect(result).toEqual(mockRatings);
    });

    it('should get paginated Ratings for Dashboard with custom parameters', () => {
        const mockRatings: RatingListItem[] = [
            {
                id: 2,
                rating: 5,
                assessmentType: AssessmentType.SEMI_AUTOMATIC,
                assessorLogin: 'tutor2',
                assessorName: 'Tutor Two',
                participationId: 20,
                exerciseId: 200,
                exerciseTitle: 'Exercise 2',
                exerciseType: ExerciseType.PROGRAMMING,
                resultId: 0,
                submissionId: 0,
            },
        ];

        let result: RatingListItem[] = [];

        service
            .getRatingsForDashboard(456, 2, 10, 'rating,desc')
            .pipe(take(1))
            .subscribe((response) => {
                result = response.content;
            });

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toBe('api/assessment/course/456/rating');
        expect(req.request.params.get('page')).toBe('2');
        expect(req.request.params.get('size')).toBe('10');
        expect(req.request.params.get('sort')).toBe('rating,desc');

        req.flush(mockRatings);

        expect(result).toEqual(mockRatings);
    });

    it('should handle empty ratings list', () => {
        let result: RatingListItem[] = [];

        service
            .getRatingsForDashboard(789)
            .pipe(take(1))
            .subscribe((response) => {
                result = response.content;
            });

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush([]);

        expect(result).toHaveLength(0);
    });

    afterEach(() => {
        httpMock.verify();
    });
});
