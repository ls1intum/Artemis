import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriodDTO, TutorialGroupFreePeriodService } from 'app/tutorialgroup/manage/service/tutorial-group-free-period.service';
import { TutorialGroupFreePeriod } from 'app/openapi/model/tutorial-group-free-period';

const FREE_PERIODS_URL = '/api/tutorialgroup/courses/1/tutorial-groups-configurations/2/tutorial-free-periods';

describe('TutorialGroupFreePeriodService', () => {
    let service: TutorialGroupFreePeriodService;
    let httpMock: HttpTestingController;
    const holiday: TutorialGroupFreePeriodDTO = { startDate: dayjs.utc('2025-12-17T00:00:00'), endDate: dayjs.utc('2025-12-17T23:59:00'), reason: 'Holiday' };
    const saved: TutorialGroupFreePeriod = { id: 3, start: '2025-12-17T00:00:00Z', end: '2025-12-17T23:59:00Z', reason: 'Holiday' };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(TutorialGroupFreePeriodService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should create a free period', () => {
        let result: TutorialGroupFreePeriod | undefined;
        service.create(1, 2, holiday).subscribe((created) => (result = created));

        const req = httpMock.expectOne({ method: 'POST', url: FREE_PERIODS_URL });
        expect(req.request.body).toEqual({ startDate: '2025-12-17T00:00:00', endDate: '2025-12-17T23:59:00', reason: 'Holiday' });
        req.flush(saved);
        expect(result).toEqual(saved);
    });

    it('should send the wall clock of the zone the bounds were chosen in, not of the browser', () => {
        // The server reads these digits back in the course's zone. Auckland is far from the zone the suite runs in, so
        // routing the value through an instant would send a different day, and the holiday would cancel the wrong one.
        const startInAuckland = dayjs.tz('2025-12-17 00:00', 'Pacific/Auckland');
        service.create(1, 2, { startDate: startInAuckland, endDate: startInAuckland.set('hour', 23).set('minute', 59), reason: 'Christmas holidays' }).subscribe();

        const req = httpMock.expectOne({ method: 'POST', url: FREE_PERIODS_URL });
        expect(req.request.body.startDate).toBe('2025-12-17T00:00:00');
        expect(req.request.body.endDate).toBe('2025-12-17T23:59:00');
        req.flush(saved);
    });

    it('should update a free period', () => {
        let result: TutorialGroupFreePeriod | undefined;
        service.update(1, 2, 3, holiday).subscribe((updated) => (result = updated));

        const req = httpMock.expectOne({ method: 'PUT', url: `${FREE_PERIODS_URL}/3` });
        expect(req.request.body.startDate).toBe('2025-12-17T00:00:00');
        req.flush(saved);
        expect(result).toEqual(saved);
    });

    it('should delete a free period', () => {
        let completed = false;
        service.delete(1, 2, 3).subscribe({ complete: () => (completed = true) });

        httpMock.expectOne({ method: 'DELETE', url: `${FREE_PERIODS_URL}/3` }).flush(null);
        expect(completed).toBe(true);
    });

    it('should ask for the overlapping sessions in the wall clock of the chosen zone', () => {
        let result: number | undefined;
        service.getOverlappingSessionCount(1, dayjs.utc('2025-12-17T09:00:00'), dayjs.utc('2025-12-17T10:00:00'), 3).subscribe((count) => (result = count));

        const req = httpMock.expectOne((request) => request.url.startsWith('/api/tutorialgroup/courses/1/tutorial-free-periods/overlapping-session-count'));
        const query = new URLSearchParams(req.request.url.split('?')[1]);
        expect(query.get('from')).toBe('2025-12-17T09:00:00');
        expect(query.get('to')).toBe('2025-12-17T10:00:00');
        expect(query.get('editedFreePeriodId')).toBe('3');
        req.flush(2);
        expect(result).toBe(2);
    });
});
