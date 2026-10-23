import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisDashboardComponent } from './iris-dashboard.component';
import { IrisDashboardService } from './iris-dashboard.service';
import { of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';

describe('IrisDashboardComponent', () => {
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
        thumbsUpCount: 40,
        thumbsDownCount: 10,
        avgResponseTimeSeconds: 5.5,
        p50ResponseTimeSeconds: 4.0,
        p95ResponseTimeSeconds: 12.0,
        totalTokenCostEur: 150.75,
    };

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [IrisDashboardComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), TranslateService, provideTranslateService()],
        });

        dashboardService = TestBed.inject(IrisDashboardService);
        vi.spyOn(dashboardService, 'getOverview').mockReturnValue(of(mockOverview));
        vi.spyOn(dashboardService, 'getConfig').mockReturnValue(
            of({
                maxQueryWindowDays: 90,
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
        expect(component.error()).toBe(false);
    });

    it('should reload data on time span change', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        component.onTimeSpanChange(component.timeSpanOptions()[0]);
        await fixture.whenStable();
        expect(dashboardService.getOverview).toHaveBeenCalledTimes(2);
    });

    it('should request all users by default', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.selectedAiSelection().value).toBe('ALL');
        expect(dashboardService.getOverview).toHaveBeenCalledWith(expect.any(String), expect.any(String), 'ALL');
        expect(dashboardService.getBreakdown).toHaveBeenCalledWith(expect.any(String), expect.any(String), 'CHAT_MODE', 'ALL');
        expect(dashboardService.getBreakdown).toHaveBeenCalledWith(expect.any(String), expect.any(String), 'COURSE', 'ALL');
        expect(dashboardService.getBreakdown).toHaveBeenCalledWith(expect.any(String), expect.any(String), 'MODEL', 'ALL');
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.irisDashboard.aiSelection.hint');
    });

    it('should reload all data for the chosen AI selection', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        vi.mocked(dashboardService.getOverview).mockClear();
        vi.mocked(dashboardService.getBreakdown).mockClear();

        const local = component.aiSelectionOptions().find((option) => option.value === 'LOCAL')!;
        component.onAiSelectionChange(local);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.selectedAiSelection()).toBe(local);
        expect(dashboardService.getOverview).toHaveBeenCalledOnce();
        expect(dashboardService.getOverview).toHaveBeenCalledWith(expect.any(String), expect.any(String), 'LOCAL');
        expect(dashboardService.getBreakdown).toHaveBeenCalledTimes(3);
        expect(dashboardService.getBreakdown).toHaveBeenCalledWith(expect.any(String), expect.any(String), 'CHAT_MODE', 'LOCAL');
        expect(dashboardService.getBreakdown).toHaveBeenCalledWith(expect.any(String), expect.any(String), 'COURSE', 'LOCAL');
        expect(dashboardService.getBreakdown).toHaveBeenCalledWith(expect.any(String), expect.any(String), 'MODEL', 'LOCAL');
        expect(fixture.nativeElement.textContent).toContain('artemisApp.irisDashboard.aiSelection.hint');
    });

    it('should keep the AI selection when the time span changes', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        component.onAiSelectionChange(component.aiSelectionOptions().find((option) => option.value === 'CLOUD')!);
        component.onTimeSpanChange(component.timeSpanOptions()[0]);
        await fixture.whenStable();
        expect(dashboardService.getOverview).toHaveBeenLastCalledWith(expect.any(String), expect.any(String), 'CLOUD');
    });

    it('should set error signal on overview failure', async () => {
        vi.spyOn(dashboardService, 'getOverview').mockReturnValue(throwError(() => new Error('Server error')));
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.error()).toBe(true);
        expect(component.loading()).toBe(false);
        expect(component.overview()).toBeUndefined();
    });

    it('should clear stale data on reload', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.overview()).toEqual(mockOverview);

        vi.spyOn(dashboardService, 'getOverview').mockReturnValue(throwError(() => new Error('fail')));
        component.refresh();
        await fixture.whenStable();
        expect(component.overview()).toBeUndefined();
        expect(component.error()).toBe(true);
    });
});
