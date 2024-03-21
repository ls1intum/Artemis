import { TestBed } from '@angular/core/testing';
import { AnalyticsService } from 'app/core/posthog/analytics.service';
import { posthog } from 'posthog-js';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

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
        await analyticsService.initAnalytics({ postHog: { token: 'token', host: 'host' } } as any as ProfileInfo);
        expect(initSpy).toHaveBeenCalledOnce();
    });

    it('should not init posthog', async () => {
        const initSpy = jest.spyOn(posthog, 'init');
        await analyticsService.initAnalytics({} as any as ProfileInfo);
        expect(initSpy).not.toHaveBeenCalled();
    });
});
