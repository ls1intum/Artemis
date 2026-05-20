import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { IrisDashboardService } from './iris-dashboard.service';
import { IrisDashboardOverview } from './iris-dashboard.model';

describe('IrisDashboardService', () => {
    setupTestBed({ zoneless: true });

    let service: IrisDashboardService;
    let httpMock: HttpTestingController;
    const from = new Date('2026-01-01T00:00:00Z');
    const to = new Date('2026-01-02T00:00:00Z');

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(IrisDashboardService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should request overview with ISO date params and optional chat mode', () => {
        const overview = { totalSessions: 1 } as IrisDashboardOverview;
        let result: IrisDashboardOverview | undefined;

        service.getOverview({ from, to, chatMode: 'COURSE_CHAT' }).subscribe((received) => {
            result = received;
        });

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toBe('api/iris/admin/dashboard/overview');
        expect(req.request.params.get('from')).toBe('2026-01-01T00:00:00.000Z');
        expect(req.request.params.get('to')).toBe('2026-01-02T00:00:00.000Z');
        expect(req.request.params.get('chatMode')).toBe('COURSE_CHAT');
        req.flush(overview);
        expect(result).toEqual(overview);
    });

    it('should request time series metric', () => {
        service.getTimeSeries({ from, to }, 'DAY', 'MESSAGES').subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toBe('api/iris/admin/dashboard/time-series');
        expect(req.request.params.get('span')).toBe('DAY');
        expect(req.request.params.get('metric')).toBe('MESSAGES');
        expect(req.request.params.has('chatMode')).toBe(false);
        req.flush({ metric: 'MESSAGES', entries: [] });
    });

    it('should request breakdown dimension and config', () => {
        service.getBreakdown({ from, to }, 'MODEL').subscribe();
        const breakdownReq = httpMock.expectOne({ method: 'GET' });
        expect(breakdownReq.request.url).toBe('api/iris/admin/dashboard/breakdown');
        expect(breakdownReq.request.params.get('from')).toBe('2026-01-01T00:00:00.000Z');
        expect(breakdownReq.request.params.get('to')).toBe('2026-01-02T00:00:00.000Z');
        expect(breakdownReq.request.params.get('dimension')).toBe('MODEL');
        breakdownReq.flush([]);

        service.getConfig().subscribe();
        const configReq = httpMock.expectOne({ method: 'GET' });
        expect(configReq.request.url).toBe('api/iris/admin/dashboard/config');
        configReq.flush({});
    });
});
