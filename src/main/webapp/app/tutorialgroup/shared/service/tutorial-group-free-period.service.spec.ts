import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { TutorialGroupFreePeriodDTO, TutorialGroupFreePeriodService } from 'app/tutorialgroup/shared/service/tutorial-group-free-period.service';
import { generateExampleTutorialGroupFreePeriod } from 'test/helpers/sample/tutorialgroup/tutorialGroupFreePeriodExampleModel';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { provideHttpClient } from '@angular/common/http';
import { TutorialGroupFreePeriodApi } from 'app/openapi/api/tutorial-group-free-period-api';
import { of } from 'rxjs';

describe('TutorialGroupFreePeriodService', () => {
    let service: TutorialGroupFreePeriodService;
    let httpMock: HttpTestingController;
    let tutorialGroupFreePeriodApi: jest.Mocked<TutorialGroupFreePeriodApi>;
    let elemDefault: TutorialGroupFreePeriod;

    beforeEach(() => {
        const spyFreePeriodApi = {
            delete: jest.fn(),
        };
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TutorialGroupFreePeriodApi, useValue: spyFreePeriodApi }],
        });
        service = TestBed.inject(TutorialGroupFreePeriodService);
        httpMock = TestBed.inject(HttpTestingController);
        tutorialGroupFreePeriodApi = TestBed.inject(TutorialGroupFreePeriodApi) as jest.Mocked<TutorialGroupFreePeriodApi>;

        elemDefault = generateExampleTutorialGroupFreePeriod({});
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('getOneOfConfiguration', fakeAsync(() => {
        const returnedFromService = { ...elemDefault };
        service
            .getOneOfConfiguration(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('create', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        service
            .create(1, 1, new TutorialGroupFreePeriodDTO())
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('update', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, reason: 'Test' };
        const expected = { ...returnedFromService };

        service
            .update(1, 1, 1, new TutorialGroupFreePeriodDTO())
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('delete', fakeAsync(() => {
        tutorialGroupFreePeriodApi.delete.mockReturnValue(of(undefined));
        service
            .delete(1, 1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toBeUndefined());
        expect(tutorialGroupFreePeriodApi.delete).toHaveBeenCalledWith(1, 1, 1);
        tick();
    }));
});
