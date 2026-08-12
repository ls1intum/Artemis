import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';

import { FeatureUsageService } from './feature-usage.service';
import { FeatureUsageOverview } from './feature-usage.model';

describe('FeatureUsageService', () => {
    let service: FeatureUsageService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), FeatureUsageService],
        });
        service = TestBed.inject(FeatureUsageService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should request the overview for the given window', () => {
        const expected: FeatureUsageOverview = { days: 7, from: '2026-07-30', trackedFeatures: 0, unusedFeatures: 0, retiredFeatures: 0, totalCalls: 0 };
        let received: FeatureUsageOverview | undefined;

        service.getOverview(7).subscribe((overview) => (received = overview));

        const request = httpMock.expectOne((candidate) => candidate.url === 'api/admin/feature-usage');
        expect(request.request.method).toBe('GET');
        expect(request.request.params.get('days')).toBe('7');
        request.flush(expected);
        expect(received).toEqual(expected);
    });

    it('should restrict the overview to a caller role when one is given', () => {
        service.getOverview(30, 'STUDENT').subscribe();

        const request = httpMock.expectOne((candidate) => candidate.url === 'api/admin/feature-usage');
        expect(request.request.params.get('callerRole')).toBe('STUDENT');
        request.flush({ days: 30, from: '2026-07-07', trackedFeatures: 0, unusedFeatures: 0, retiredFeatures: 0, totalCalls: 0 });
    });

    it('should omit the role parameter when no role is selected', () => {
        service.getOverview(30).subscribe();

        const request = httpMock.expectOne((candidate) => candidate.url === 'api/admin/feature-usage');
        expect(request.request.params.has('callerRole')).toBeFalsy();
        request.flush({ days: 30, from: '2026-07-07', trackedFeatures: 0, unusedFeatures: 0, retiredFeatures: 0, totalCalls: 0 });
    });

    it('should request the trend of every endpoint behind a feature', () => {
        service.getTrend([42, 43], 30).subscribe();

        const request = httpMock.expectOne((candidate) => candidate.url === 'api/admin/feature-usage/trend');
        // repeated rather than joined, so the server sees a list and can sum across the endpoints of one feature
        expect(request.request.params.getAll('featureIds')).toEqual(['42', '43']);
        expect(request.request.params.get('days')).toBe('30');
        request.flush([]);
    });

    it('should post to trigger the digest email', () => {
        service.sendDigestEmail().subscribe();

        const request = httpMock.expectOne('api/admin/feature-usage/digest/send-email');
        expect(request.request.method).toBe('POST');
        request.flush(null);
    });

    it('should request the adoption counts', () => {
        service.getAdoption().subscribe();

        const request = httpMock.expectOne('api/admin/feature-usage/adoption');
        expect(request.request.method).toBe('GET');
        request.flush([]);
    });
});
