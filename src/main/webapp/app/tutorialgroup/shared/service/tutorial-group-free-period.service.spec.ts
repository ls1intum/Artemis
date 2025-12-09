import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { TutorialGroupFreePeriodDTO, TutorialGroupFreePeriodService } from 'app/tutorialgroup/shared/service/tutorial-group-free-period.service';
import { generateExampleTutorialGroupFreePeriod } from 'test/helpers/sample/tutorialgroup/tutorialGroupFreePeriodExampleModel';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { provideHttpClient } from '@angular/common/http';

describe('TutorialGroupFreePeriodService', () => {
    let service: TutorialGroupFreePeriodService;
    let httpMock: HttpTestingController;
    let elemDefault: TutorialGroupFreePeriod;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(TutorialGroupFreePeriodService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = generateExampleTutorialGroupFreePeriod({});
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('getOneOfConfiguration', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault);
        service
            .getOneOfConfiguration(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('create', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { id: 0 });
        const expected = Object.assign({}, returnedFromService);
        service
            .create(1, 1, new TutorialGroupFreePeriodDTO())
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('update', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { reason: 'Test' });
        const expected = Object.assign({}, returnedFromService);

        service
            .update(1, 1, 1, new TutorialGroupFreePeriodDTO())
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('delete', fakeAsync(() => {
        service
            .delete(1, 1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({});
        tick();
    }));
});
