import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { RatingService } from 'app/assessment/shared/services/rating.service';
import { Rating } from 'app/assessment/shared/entities/rating.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { provideHttpClient } from '@angular/common/http';

describe('Rating Service', () => {
    let service: RatingService;
    let httpMock: HttpTestingController;
    let elemDefault: Rating;
    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(RatingService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new Rating(new Result(), 3);
    });

    it('should create a Rating', fakeAsync(() => {
        const returnedFromService = Object.assign({ id: 0 }, elemDefault);
        service.createRating(3, 0).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get a Rating', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault);
        service.getRating(0).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should update a Rating', fakeAsync(() => {
        const returnedFromService = Object.assign({ id: 0 }, elemDefault);
        service.updateRating(3, 0).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get Ratings for Dashboard', fakeAsync(() => {
        const returnedFromService = Object.assign({}, [elemDefault]);
        const expected = Object.assign({}, returnedFromService);
        service
            .getRatingsForDashboard(0)
            .pipe(take(1))
            .subscribe((ratings) => {
                expect(ratings).toEqual(expected);
            });

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
