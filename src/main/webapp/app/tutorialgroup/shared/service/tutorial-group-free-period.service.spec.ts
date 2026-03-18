import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { HttpResponse, provideHttpClient } from '@angular/common/http';

import { TutorialGroupFreePeriodService } from './tutorial-group-free-period.service';
import { TutorialGroupFreePeriodDTO, TutorialGroupFreePeriodRequestDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-free-period-dto.model';

describe('TutorialGroupFreePeriodService', () => {
    setupTestBed({ zoneless: true });

    let service: TutorialGroupFreePeriodService;
    let httpMock: HttpTestingController;

    const elemDefault: TutorialGroupFreePeriodDTO = {
        id: 1,
        reason: 'Example Reason',
        start: '2021-01-01T00:00:00',
        end: '2021-01-01T23:59:59',
        tutorialGroupConfigurationId: 1,
    };

    const elemDefaultRequest: TutorialGroupFreePeriodRequestDTO = {
        reason: 'Example Reason',
        startDate: '2021-01-01T00:00:00',
        endDate: '2021-01-01T23:59:59',
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(TutorialGroupFreePeriodService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should GET one free period', () => {
        let result: HttpResponse<TutorialGroupFreePeriodDTO> | undefined;

        service
            .getOneOfConfiguration(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({
            method: 'GET',
            url: 'api/tutorialgroup/courses/1/tutorial-groups-configuration/1/tutorial-free-periods/1',
        });

        req.flush(elemDefault);

        expect(result?.body).toEqual(elemDefault);
        expect(result?.body?.start).toBe('2021-01-01T00:00:00');
        expect(result?.body?.end).toBe('2021-01-01T23:59:59');
    });

    it('should CREATE free period', () => {
        let result: HttpResponse<TutorialGroupFreePeriodDTO> | undefined;

        service
            .create(1, 1, elemDefaultRequest)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({
            method: 'POST',
            url: 'api/tutorialgroup/courses/1/tutorial-groups-configuration/1/tutorial-free-periods',
        });
        req.flush(elemDefault);
        expect(result?.body).toEqual(elemDefault);
    });

    it('should UPDATE free period', () => {
        const updated: TutorialGroupFreePeriodRequestDTO = {
            reason: 'Updated Reason',
            startDate: elemDefaultRequest.startDate,
            endDate: elemDefaultRequest.endDate,
        };
        const returnedFromService: TutorialGroupFreePeriodDTO = {
            ...elemDefault,
            reason: 'Updated Reason',
            start: updated.startDate,
            end: updated.endDate,
        };

        let result: HttpResponse<TutorialGroupFreePeriodDTO> | undefined;

        service
            .update(1, 1, 1, updated)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({
            method: 'PUT',
            url: 'api/tutorialgroup/courses/1/tutorial-groups-configuration/1/tutorial-free-periods/1',
        });
        req.flush(returnedFromService);
        expect(result?.body).toEqual(returnedFromService);
    });

    it('should DELETE free period', () => {
        let result: HttpResponse<void> | undefined;

        service
            .delete(1, 1, 1)
            .pipe(take(1))
            .subscribe((res) => (result = res));

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush(null);
        expect(result?.body).toBeNull();
    });
});
