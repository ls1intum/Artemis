import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { TutorialGroupFreePeriodDTO, TutorialGroupFreePeriodService } from 'app/tutorialgroup/shared/service/tutorial-group-free-period.service';
import { generateExampleTutorialGroupFreePeriod } from 'test/helpers/sample/tutorialgroup/tutorialGroupFreePeriodExampleModel';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { provideHttpClient } from '@angular/common/http';

describe('TutorialGroupFreePeriodService', () => {
    setupTestBed({ zoneless: true });

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
        vi.restoreAllMocks();
    });

    it('getOneOfConfiguration', () => {
        const returnedFromService = { ...elemDefault };
        let result: any;
        service
            .getOneOfConfiguration(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(result).toMatchObject({ body: elemDefault });
    });

    it('create', () => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        let result: any;
        service
            .create(1, 1, new TutorialGroupFreePeriodDTO())
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(result).toMatchObject({ body: expected });
    });

    it('update', () => {
        const returnedFromService = { ...elemDefault, reason: 'Test' };
        const expected = { ...returnedFromService };
        let result: any;

        service
            .update(1, 1, 1, new TutorialGroupFreePeriodDTO())
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        expect(result).toMatchObject({ body: expected });
    });

    it('delete', () => {
        let result: any;
        service
            .delete(1, 1, 1)
            .pipe(take(1))
            .subscribe((res) => (result = res));

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({});
        expect(result.body).toEqual({});
    });
});
