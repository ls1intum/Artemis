import { TestBed } from '@angular/core/testing';
import { AnalyticsService } from 'app/core/posthog/analytics.service';
import { posthog } from 'posthog-js';

describe('AnalyticsService', () => {
    let analyticsService: AnalyticsService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            providers: [],
        });
        analyticsService = TestBed.inject(AnalyticsService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should init posthog', async () => {
        const initSpy = jest.spyOn(posthog, 'init');
        await analyticsService.initAnalytics({ postHog: { token: 'token', host: 'host' } });
        expect(initSpy).toHaveBeenCalledOnce();
    });

    it('should not init posthog', async () => {
        const initSpy = jest.spyOn(posthog, 'init');
        await analyticsService.initAnalytics({});
        expect(initSpy).not.toHaveBeenCalled();
    });
});
