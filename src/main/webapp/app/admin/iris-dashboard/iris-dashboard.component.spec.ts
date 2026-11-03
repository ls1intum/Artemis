import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideTranslateService } from '@ngx-translate/core';
import { IrisDashboardComponent } from './iris-dashboard.component';
import { IrisDashboardConfig } from 'app/openapi/model/iris-dashboard-config';
import { IrisDashboardOverview } from 'app/openapi/model/iris-dashboard-overview';
import { IrisDashboardBreakdownEntry } from 'app/openapi/model/iris-dashboard-breakdown-entry';

const baseUrl = '/api/iris/admin/dashboard/';
const now = '2026-10-30T12:34:56.789Z';
const overview: IrisDashboardOverview = {
    totalSessions: 100,
    activeSessions: 50,
    engagementRate: 50,
    totalMessages: 200,
    uniqueUsers: 20,
    noResponseRate: 12.5,
    noResponseMessageCount: 25,
    noResponseSessionCount: 12,
    thumbsUpRatio: 80,
    thumbsDownRatio: 20,
    thumbsUpAbsoluteRate: 60,
    thumbsDownAbsoluteRate: 15,
    sessionsWithThumbsUp: 40,
    sessionsWithThumbsDown: 10,
    thumbsUpCount: 40,
    thumbsDownCount: 10,
    avgResponseTimeSeconds: 5.5,
    p50ResponseTimeSeconds: 4,
    p95ResponseTimeSeconds: 12,
    totalTokenCostEur: 150.75,
};
const config: IrisDashboardConfig = {
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
};
const chatMode: IrisDashboardBreakdownEntry[] = [{ label: 'COURSE_CHAT', values: { sessions: 10, activeSessions: 5, messages: 20, noResponseRate: 0 } }];
const courses: IrisDashboardBreakdownEntry[] = [{ label: 'Algorithms', values: { sessions: 10 } }];
const models: IrisDashboardBreakdownEntry[] = [{ label: 'model-local', values: { totalTokens: 1234, costEur: 0 } }];

function url(request: TestRequest): URL {
    return new URL(request.request.urlWithParams, 'http://localhost');
}

describe('IrisDashboardComponent with generated transport', () => {
    let component: IrisDashboardComponent;
    let fixture: ComponentFixture<IrisDashboardComponent>;
    let http: HttpTestingController;

    beforeEach(() => {
        vi.useFakeTimers({ toFake: ['Date'] });
        vi.setSystemTime(now);
        TestBed.configureTestingModule({
            imports: [IrisDashboardComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService()],
        });
        http = TestBed.inject(HttpTestingController);
        fixture = TestBed.createComponent(IrisDashboardComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        fixture.destroy();
        http.verify();
        vi.useRealTimers();
    });

    function batch(days = 30, selection = 'ALL'): TestRequest[] {
        const requests = http.match((request) => {
            const path = new URL(request.urlWithParams, 'http://localhost').pathname;
            return path.startsWith(baseUrl) && path !== baseUrl + 'config';
        });
        expect(requests).toHaveLength(4);
        for (const request of requests) {
            expect(request.request.method).toBe('GET');
            expect(url(request).searchParams.get('from')).toBe(new Date(Date.now() - days * 86400000).toISOString());
            expect(url(request).searchParams.get('to')).toBe(new Date().toISOString());
            expect(url(request).searchParams.get('aiSelection')).toBe(selection);
        }
        expect(requests.filter((request) => url(request).pathname === baseUrl + 'overview')).toHaveLength(1);
        expect(requests.filter((request) => url(request).pathname === baseUrl + 'breakdown').map((request) => url(request).searchParams.get('dimension'))).toEqual([
            'CHAT_MODE',
            'COURSE',
            'MODEL',
        ]);
        return requests;
    }

    function respond(requests: TestRequest[], data = overview): void {
        for (const request of requests) {
            const dimension = url(request).searchParams.get('dimension');
            request.flush(dimension === 'CHAT_MODE' ? chatMode : dimension === 'COURSE' ? courses : dimension === 'MODEL' ? models : data);
        }
    }

    function start(): { requests: TestRequest[]; configRequest: TestRequest } {
        fixture.detectChanges();
        TestBed.tick();
        return { requests: batch(), configRequest: http.expectOne(baseUrl + 'config') };
    }

    async function initialize(): Promise<void> {
        const initial = start();
        respond(initial.requests);
        initial.configRequest.flush(config);
        await fixture.whenStable();
        fixture.detectChanges();
    }

    it('creates behind the guarded page and loads Month/ALL with one config read', async () => {
        const initial = start();
        expect(component).toBeTruthy();
        expect(component.selectedTimeSpan().value).toBe('MONTH');
        expect(component.selectedAiSelection().value).toBe('ALL');
        expect(component.loading()).toBe(true);
        expect(component.overview()).toBeUndefined();
        expect(component.config()).toBeUndefined();
        respond(initial.requests);
        initial.configRequest.flush(config);
        await fixture.whenStable();
        fixture.detectChanges();
        expect(component.overview()).toEqual(overview);
        expect(component.config()).toEqual(config);
        expect(component.loading()).toBe(false);
        expect(component.error()).toBe(false);
        expect(component.chatModeBreakdown()).toEqual(chatMode);
        expect(component.courseBreakdown()).toEqual(courses);
        expect(component.modelBreakdown()).toEqual(models);
        expect(fixture.nativeElement.textContent).toContain('150.75');
        expect(fixture.nativeElement.textContent).toContain('COURSE_CHAT');
        const panel: HTMLElement = fixture.nativeElement.querySelector('tumaet-ui-panel');
        const toggle = panel.querySelector('button')!;
        expect(toggle.getAttribute('aria-expanded')).toBe('false');
        expect(JSON.parse(panel.querySelector('pre')!.textContent!)).toEqual(config);
        toggle.click();
        fixture.detectChanges();
        expect(toggle.getAttribute('aria-expanded')).toBe('true');
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.irisDashboard.aiSelection.hint');
        http.expectNone((request) => request.url.includes('time-series'));
    });

    it('reloads four reads for each fixed elapsed-day option while keeping CLOUD', async () => {
        await initialize();
        component.onAiSelectionChange(component.aiSelectionOptions()[1]);
        respond(batch(30, 'CLOUD'));
        for (const option of component.timeSpanOptions()) {
            vi.setSystemTime(Date.now() + 1000);
            component.onTimeSpanChange(option);
            respond(batch(option.days, 'CLOUD'));
        }
        TestBed.tick();
        http.expectNone(baseUrl + 'config');
        expect(component.selectedAiSelection().value).toBe('CLOUD');
    });

    it('reloads all data for LOCAL and retains the filter hint', async () => {
        await initialize();
        const local = component.aiSelectionOptions()[2];
        component.onAiSelectionChange(local);
        respond(batch(30, 'LOCAL'));
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.selectedAiSelection()).toBe(local);
        expect(fixture.nativeElement.textContent).toContain('artemisApp.irisDashboard.aiSelection.hint');
        http.expectNone(baseUrl + 'config');
    });

    it('changes tabs without requests and retains the tab across refresh', async () => {
        await initialize();
        component.onBreakdownTabChange(1);
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain('Algorithms');
        component.onBreakdownTabChange(2);
        component.onBreakdownTabChange(undefined);
        component.onBreakdownTabChange('1');
        TestBed.tick();
        http.expectNone((request) => request.url.startsWith(baseUrl));
        vi.setSystemTime(Date.now() + 1000);
        component.refresh();
        expect(component.loading()).toBe(true);
        expect(component.overview()).toBeUndefined();
        expect(component.chatModeBreakdown()).toEqual([]);
        expect(component.courseBreakdown()).toEqual([]);
        expect(component.modelBreakdown()).toEqual([]);
        respond(batch());
        TestBed.tick();
        expect(component.activeBreakdownTab()).toBe(2);
        expect(component.config()).toEqual(config);
        expect(fixture.nativeElement.textContent).toContain('model-local');
        http.expectNone(baseUrl + 'config');
    });

    it('clears stale data on overview failure and recovers on refresh', async () => {
        await initialize();
        component.refresh();
        for (const request of batch()) {
            if (url(request).pathname.endsWith('/overview')) {
                request.flush({}, { status: 500, statusText: 'Server error' });
            } else {
                request.flush([]);
            }
        }
        expect(component.overview()).toBeUndefined();
        expect(component.error()).toBe(true);
        expect(component.loading()).toBe(false);
        expect(component.chatModeBreakdown()).toEqual([]);
        component.refresh();
        respond(batch());
        expect(component.overview()).toEqual(overview);
        expect(component.error()).toBe(false);
        expect(component.loading()).toBe(false);
    });

    it.each(['CHAT_MODE', 'COURSE', 'MODEL'])('keeps %s failures independent of overview and other breakdowns', async (failedDimension) => {
        const initial = start();
        initial.configRequest.flush(config);
        const overviewRequest = initial.requests.find((request) => url(request).pathname.endsWith('/overview'))!;
        for (const request of initial.requests.filter((request) => request !== overviewRequest)) {
            if (url(request).searchParams.get('dimension') === failedDimension) {
                request.flush({}, { status: 500, statusText: 'Server error' });
            } else {
                respond([request]);
            }
        }
        expect(component.loading()).toBe(true);
        expect(component.error()).toBe(false);
        overviewRequest.flush(overview);
        await fixture.whenStable();
        expect(component.loading()).toBe(false);
        expect(component.error()).toBe(false);
        expect(component.chatModeBreakdown()).toEqual(failedDimension === 'CHAT_MODE' ? [] : chatMode);
        expect(component.courseBreakdown()).toEqual(failedDimension === 'COURSE' ? [] : courses);
        expect(component.modelBreakdown()).toEqual(failedDimension === 'MODEL' ? [] : models);
    });

    it('leaves config undefined on failure without changing overview or retrying on filters/refresh', async () => {
        const initial = start();
        initial.configRequest.flush({}, { status: 403, statusText: 'Forbidden' });
        respond(initial.requests);
        await fixture.whenStable();
        expect(() => component.config()).not.toThrow();
        expect(component.config()).toBeUndefined();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('tumaet-ui-panel')).toBeNull();
        expect(component.error()).toBe(false);
        component.refresh();
        respond(batch());
        component.onAiSelectionChange(component.aiSelectionOptions()[2]);
        respond(batch(30, 'LOCAL'));
        component.onTimeSpanChange(component.timeSpanOptions()[0]);
        respond(batch(1, 'LOCAL'));
        TestBed.tick();
        http.expectNone(baseUrl + 'config');
        expect(component.overview()).toEqual(overview);
    });

    it.each(['refresh', 'span', 'selection'])('cancels the preceding batch on %s and prevents late success/error from repainting', async (action) => {
        const initial = start();
        initial.configRequest.flush(config);
        vi.setSystemTime(Date.now() + 1000);
        if (action === 'span') {
            component.onTimeSpanChange(component.timeSpanOptions()[0]);
        } else if (action === 'selection') {
            component.onAiSelectionChange(component.aiSelectionOptions()[2]);
        } else {
            component.refresh();
        }
        expect(initial.requests.every((request) => request.cancelled)).toBe(true);
        const current = batch(action === 'span' ? 1 : 30, action === 'selection' ? 'LOCAL' : 'ALL');
        respond(current);
        for (const request of initial.requests) {
            expect(() => request.flush(overview)).toThrow();
            expect(() => request.flush({}, { status: 500, statusText: 'Late error' })).toThrow();
        }
        await fixture.whenStable();
        expect(component.overview()).toEqual(overview);
        expect(component.chatModeBreakdown()).toEqual(chatMode);
        expect(component.courseBreakdown()).toEqual(courses);
        expect(component.modelBreakdown()).toEqual(models);
        expect(component.loading()).toBe(false);
        expect(component.error()).toBe(false);
    });

    it('renders zero KPIs and empty tables without inventing fallback data', async () => {
        const initial = start();
        const empty: IrisDashboardOverview = {
            totalSessions: 0,
            activeSessions: 0,
            engagementRate: 0,
            totalMessages: 0,
            uniqueUsers: 0,
            noResponseRate: 0,
            noResponseMessageCount: 0,
            noResponseSessionCount: 0,
            thumbsUpRatio: 0,
            thumbsDownRatio: 0,
            thumbsUpAbsoluteRate: 0,
            thumbsDownAbsoluteRate: 0,
            sessionsWithThumbsUp: 0,
            sessionsWithThumbsDown: 0,
            thumbsUpCount: 0,
            thumbsDownCount: 0,
            avgResponseTimeSeconds: 0,
            p50ResponseTimeSeconds: 0,
            p95ResponseTimeSeconds: 0,
            totalTokenCostEur: 0,
        };
        for (const request of initial.requests) {
            request.flush(url(request).pathname.endsWith('/overview') ? empty : []);
        }
        initial.configRequest.flush(config);
        await fixture.whenStable();
        fixture.detectChanges();
        expect(component.overview()).toEqual(empty);
        expect(component.error()).toBe(false);
        expect(fixture.nativeElement.querySelector('jhi-iris-kpi-card').textContent).toContain('0');
        for (const tab of [0, 1, 2]) {
            component.onBreakdownTabChange(tab);
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(0);
        }
    });

    it('owns a separate config resource for each dashboard instance', async () => {
        const firstFixture = fixture;
        const first = start();
        fixture = TestBed.createComponent(IrisDashboardComponent);
        component = fixture.componentInstance;
        const second = start();

        firstFixture.destroy();
        expect(first.requests.every((request) => request.cancelled)).toBe(true);
        expect(first.configRequest.cancelled).toBe(true);
        expect(second.configRequest.cancelled).toBe(false);
        expect(second.requests.every((request) => !request.cancelled)).toBe(true);
        expect(component.config()).toBeUndefined();

        second.configRequest.flush(config);
        respond(second.requests);
        await fixture.whenStable();
        expect(component.config()).toEqual(config);
        expect(component.overview()).toEqual(overview);
        TestBed.tick();
        http.expectNone(baseUrl + 'config');
    });

    it('cancels the four windowed reads and pending config resource on destruction', () => {
        const initial = start();
        fixture.destroy();
        expect(initial.requests.every((request) => request.cancelled)).toBe(true);
        expect(initial.configRequest.cancelled).toBe(true);
    });
});
