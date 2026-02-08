import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { provideHttpClient } from '@angular/common/http';

import { TutorialGroupFreePeriodService } from 'app/tutorialgroup/shared/service/tutorial-group-free-period.service';
import { TutorialGroupFreePeriodDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-free-period-dto.model';

describe('TutorialGroupFreePeriodService', () => {
    setupTestBed({ zoneless: true });

    let service: TutorialGroupFreePeriodService;
    let httpMock: HttpTestingController;
    let elemDefault: TutorialGroupFreePeriodDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(TutorialGroupFreePeriodService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = {
            id: 1,
            reason: 'Example Reason',
            start: dayjs('2021-01-01T00:00:00Z'),
            end: dayjs('2021-01-01T23:59:59Z'),
            tutorialGroupsConfiguration: { id: 1 } as any,
        };
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('getOneOfConfiguration', () => {
        const returnedFromServer = {
            ...elemDefault,
            start: elemDefault.start?.toJSON(),
            end: elemDefault.end?.toJSON(),
        };

        let result: any;
        service
            .getOneOfConfiguration(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromServer);

        expect(result.body.id).toBe(elemDefault.id);
        expect(result.body.reason).toBe(elemDefault.reason);
        expect(dayjs.isDayjs(result.body.start)).toBe(true);
        expect(dayjs.isDayjs(result.body.end)).toBe(true);
        expect(result.body.start?.toISOString()).toBe(elemDefault.start?.toISOString());
        expect(result.body.end?.toISOString()).toBe(elemDefault.end?.toISOString());
    });

    it('create', () => {
        const returnedFromServer = {
            ...elemDefault,
            id: 0,
            start: elemDefault.start?.toJSON(),
            end: elemDefault.end?.toJSON(),
        };

        let result: any;
        service
            .create(1, 1, new TutorialGroupFreePeriodDTO())
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromServer);

        expect(result.body.id).toBe(0);
        expect(dayjs.isDayjs(result.body.start)).toBe(true);
        expect(dayjs.isDayjs(result.body.end)).toBe(true);
    });

    it('update', () => {
        const returnedFromServer = {
            ...elemDefault,
            reason: 'Test',
            start: elemDefault.start?.toJSON(),
            end: elemDefault.end?.toJSON(),
        };

        let result: any;
        service
            .update(1, 1, 1, new TutorialGroupFreePeriodDTO())
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromServer);

        expect(result.body.reason).toBe('Test');
        expect(dayjs.isDayjs(result.body.start)).toBe(true);
        expect(dayjs.isDayjs(result.body.end)).toBe(true);
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
