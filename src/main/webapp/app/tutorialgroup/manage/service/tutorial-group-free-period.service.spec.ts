import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { TutorialGroupFreePeriodDTO, TutorialGroupFreePeriodService } from 'app/tutorialgroup/manage/service/tutorial-group-free-period.service';
import { generateExampleTutorialGroupFreePeriod } from 'test/helpers/sample/tutorialgroup/tutorialGroupFreePeriodExampleModel';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';

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

    it('should send the wall clock of the zone the bounds were chosen in, not of the browser', () => {
        // The server reads these digits back in the course's zone. Auckland is far from the zone the suite runs in, so
        // routing the value through an instant would send a different day, and the holiday would cancel the wrong one.
        const startInAuckland = dayjs.tz('2025-12-17 00:00', 'Pacific/Auckland');
        const dto = new TutorialGroupFreePeriodDTO();
        dto.startDate = startInAuckland;
        dto.endDate = startInAuckland.set('hour', 23).set('minute', 59);
        dto.reason = 'Christmas holidays';

        service.create(1, 1, dto).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'POST' });
        expect(req.request.body.startDate).toBe('2025-12-17T00:00:00');
        expect(req.request.body.endDate).toBe('2025-12-17T23:59:00');
        req.flush({ ...elemDefault });
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
