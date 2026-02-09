import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { take } from 'rxjs/operators';
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
            startDate: new Date(2021, 0, 1, 0, 0, 0),
            endDate: new Date(2021, 0, 1, 23, 59, 59),
            tutorialGroupsConfiguration: { id: 1 },
        };
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('getOneOfConfiguration', () => {
        // server returns LocalDateTime strings without a timezone
        const returnedFromServer = {
            id: elemDefault.id,
            reason: elemDefault.reason,
            start: '2021-01-01T00:00:00',
            end: '2021-01-01T23:59:59',
            tutorialGroupsConfiguration: elemDefault.tutorialGroupsConfiguration,
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

        // Assert Dates
        expect(result.body.startDate instanceof Date).toBeTrue();
        expect(result.body.endDate instanceof Date).toBeTrue();
        expect(result.body.startDate.toISOString()).toBe(elemDefault.startDate!.toISOString());
        expect(result.body.endDate.toISOString()).toBe(elemDefault.endDate!.toISOString());
    });

    it('create', () => {
        const returnedFromServer = {
            id: 0,
            reason: elemDefault.reason,
            start: '2021-01-01T00:00:00',
            end: '2021-01-01T23:59:59',
            tutorialGroupsConfiguration: elemDefault.tutorialGroupsConfiguration,
        };

        let result: any;
        service
            .create(1, 1, new TutorialGroupFreePeriodDTO())
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromServer);

        expect(result.body.id).toBe(0);
        expect(result.body.startDate instanceof Date).toBeTrue();
        expect(result.body.endDate instanceof Date).toBeTrue();
    });

    it('update', () => {
        const returnedFromServer = {
            id: elemDefault.id,
            reason: 'Test',
            start: '2021-01-01T00:00:00',
            end: '2021-01-01T23:59:59',
            tutorialGroupsConfiguration: elemDefault.tutorialGroupsConfiguration,
        };

        let result: any;
        service
            .update(1, 1, 1, new TutorialGroupFreePeriodDTO())
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromServer);

        expect(result.body.reason).toBe('Test');
        expect(result.body.startDate instanceof Date).toBeTrue();
        expect(result.body.endDate instanceof Date).toBeTrue();
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
