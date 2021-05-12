import { TranslateService } from '@ngx-translate/core';
import { getTestBed, TestBed, tick, fakeAsync } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTestModule } from '../test.module';
import { RatingService } from 'app/exercises/shared/rating/rating.service';
import { Rating } from 'app/entities/rating.model';
import { Result } from 'app/entities/result.model';

describe('Rating Service', () => {
    let injector: TestBed;
    let service: RatingService;
    let httpMock: HttpTestingController;
    let elemDefault: Rating;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        injector = getTestBed();
        service = injector.get(RatingService);
        httpMock = injector.get(HttpTestingController);

        elemDefault = new Rating(new Result(), 3);
    });

    it('should create a Rating', fakeAsync(() => {
        const returnedFromService = {
            id: 0,
            ...elemDefault,
        };
        service.createRating(new Rating(new Result(), 3)).pipe(take(1)).subscribe();

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
        const returnedFromService = {
            id: 0,
            ...elemDefault,
        };
        service.updateRating(new Rating(new Result(), 3)).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should get Ratings for Dashboard', fakeAsync(() => {
        const returnedFromService = Object.assign({}, [elemDefault]);
        const expected = { ...returnedFromService };
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
