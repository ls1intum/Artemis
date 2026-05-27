import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisDashboardComponent } from './iris-dashboard.component';
import { IrisDashboardService } from './iris-dashboard.service';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('IrisDashboardComponent', () => {
    setupTestBed({ zoneless: true });

    let component: IrisDashboardComponent;
    let fixture: ComponentFixture<IrisDashboardComponent>;
    let dashboardService: IrisDashboardService;

    const mockOverview = {
        totalSessions: 100,
        activeSessions: 50,
        engagementRate: 50.0,
        totalMessages: 200,
        uniqueUsers: 20,
        noResponseRate: 12.5,
        noResponseMessageCount: 25,
        noResponseSessionCount: 12,
        thumbsUpRatio: 80.0,
        thumbsDownRatio: 20.0,
        thumbsUpAbsoluteRate: 60.0,
        thumbsDownAbsoluteRate: 15.0,
        sessionsWithThumbsUp: 40,
        sessionsWithThumbsDown: 10,
        avgResponseTimeSeconds: 5.5,
        p50ResponseTimeSeconds: 4.0,
        p95ResponseTimeSeconds: 12.0,
        totalTokenCostEur: 150.75,
    };

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [IrisDashboardComponent],
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        dashboardService = TestBed.inject(IrisDashboardService);
        vi.spyOn(dashboardService, 'getOverview').mockReturnValue(of(mockOverview));
        vi.spyOn(dashboardService, 'getConfig').mockReturnValue(
            of({
                maxQueryWindowDays: 365,
                staleThresholdMinutes: 5,
                digestEnabled: false,
                digestCron: '0 0 7 * * *',
                alertEnabled: false,
                alertNoResponseRateThreshold: 10,
                alertCheckIntervalMinutes: 30,
                alertCooldownMinutes: 360,
                alertLookbackMinutes: 60,
                alertMinimumEligibleSessions: 10,
                alertMinimumUserMessages: 20,
            }),
        );
        vi.spyOn(dashboardService, 'getBreakdown').mockReturnValue(of([]));

        fixture = TestBed.createComponent(IrisDashboardComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load overview on init', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.overview()).toEqual(mockOverview);
        expect(component.loading()).toBe(false);
    });

    it('should reload data on time span change', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        component.onTimeSpanChange(component.timeSpanOptions[0]);
        expect(dashboardService.getOverview).toHaveBeenCalledTimes(2);
    });
});
