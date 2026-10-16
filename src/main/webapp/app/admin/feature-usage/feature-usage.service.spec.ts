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
        const expected = { days: 7, from: '2026-09-18' } as FeatureUsageOverview;
        let received: FeatureUsageOverview | undefined;

        service.getOverview(7).subscribe((overview) => (received = overview));

        const request = httpMock.expectOne((candidate) => candidate.url === 'api/admin/feature-usage');
        expect(request.request.method).toBe('GET');
        expect(request.request.params.get('days')).toBe('7');
        expect(request.request.params.has('callerRole')).toBe(false);
        request.flush(expected);
        expect(received).toEqual(expected);
    });

    it('should restrict the overview to a caller role when one is given', () => {
        service.getOverview(30, 'STUDENT').subscribe();

        const request = httpMock.expectOne((candidate) => candidate.url === 'api/admin/feature-usage');
        expect(request.request.params.get('callerRole')).toBe('STUDENT');
        request.flush({});
    });

    it('should chart a feature by its catalogue name', () => {
        service.getFeatureTrend('FAQ', 90, 'EDITOR').subscribe();

        const request = httpMock.expectOne((candidate) => candidate.url === 'api/admin/feature-usage/trend');
        expect(request.request.params.get('feature')).toBe('FAQ');
        expect(request.request.params.get('days')).toBe('90');
        expect(request.request.params.get('callerRole')).toBe('EDITOR');
        expect(request.request.params.has('featureIds')).toBe(false);
        request.flush([]);
    });

    it('should chart endpoints by repeating their ids', () => {
        service.getEndpointTrend([1, 2], 7).subscribe();

        const request = httpMock.expectOne((candidate) => candidate.url === 'api/admin/feature-usage/trend');
        expect(request.request.params.getAll('featureIds')).toEqual(['1', '2']);
        expect(request.request.params.has('feature')).toBe(false);
        expect(request.request.params.has('callerRole')).toBe(false);
        request.flush([]);
    });

    it('should request the adoption', () => {
        service.getAdoption().subscribe();

        httpMock.expectOne({ method: 'GET', url: 'api/admin/feature-usage/adoption' }).flush([]);
    });

    it('should send the digest email', () => {
        service.sendDigestEmail().subscribe();

        httpMock.expectOne({ method: 'POST', url: 'api/admin/feature-usage/digest/send-email' }).flush(null);
    });
});
