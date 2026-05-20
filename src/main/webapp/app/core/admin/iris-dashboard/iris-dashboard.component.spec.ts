import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { Subject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { IrisDashboardComponent } from './iris-dashboard.component';
import { IrisDashboardService } from './iris-dashboard.service';
import {
    ChartDataEntry,
    IrisDashboardBreakdownEntry,
    IrisDashboardConfig,
    IrisDashboardMetric,
    IrisDashboardOverview,
    IrisDashboardRange,
    IrisDashboardSpan,
    IrisDashboardTimeSeries,
} from './iris-dashboard.model';

interface IrisDashboardComponentTestAccess {
    selectedRange: IrisDashboardRange;
    loadDashboard: () => void;
    onFilterChange: () => void;
    barData: (metric: IrisDashboardMetric) => ChartDataEntry[];
    lineData: (metric: IrisDashboardMetric) => ChartDataEntry[];
    timeSeries: {
        set: (value: Partial<Record<IrisDashboardMetric, IrisDashboardTimeSeries>>) => void;
    };
}

class IrisDashboardTestTranslateService {
    private readonly translations: Record<string, string> = {
        'artemisApp.iris.dashboard.kpis.totalSessions': 'Total Sessions',
        'artemisApp.iris.dashboard.kpis.activeSessions': 'Active Sessions',
        'artemisApp.iris.dashboard.kpis.engagementRate': 'Engagement Rate',
        'artemisApp.iris.dashboard.kpis.uniqueUsers': 'Unique Users',
        'artemisApp.iris.dashboard.kpis.noResponseRate': 'No-Response Rate',
        'artemisApp.iris.dashboard.kpis.noResponseDetail': '{{messages}} messages / {{sessions}} sessions',
        'artemisApp.iris.dashboard.kpis.thumbsUp': 'Thumbs Up',
        'artemisApp.iris.dashboard.kpis.thumbsDown': 'Thumbs Down',
        'artemisApp.iris.dashboard.kpis.llmMessageDetail': '{{rate}} of LLM messages',
        'artemisApp.iris.dashboard.kpis.sessionsWithThumbsUp': 'Sessions with Thumbs Up',
        'artemisApp.iris.dashboard.kpis.sessionsWithThumbsDown': 'Sessions with Thumbs Down',
        'artemisApp.iris.dashboard.kpis.averageResponseTime': 'Average Response Time',
        'artemisApp.iris.dashboard.kpis.responseTimePercentiles': 'P50 / P95 Response Time',
        'artemisApp.iris.dashboard.kpis.tokenCost': 'Token Cost',
        'artemisApp.iris.dashboard.series.user': 'User',
        'artemisApp.iris.dashboard.series.iris': 'Iris',
        'artemisApp.iris.dashboard.series.artifact': 'Artifact',
        'artemisApp.iris.dashboard.series.thumbsUp': 'Thumbs Up',
        'artemisApp.iris.dashboard.series.thumbsDown': 'Thumbs Down',
        'artemisApp.iris.dashboard.series.unrated': 'Unrated',
        'artemisApp.iris.dashboard.series.average': 'Average',
        'artemisApp.iris.dashboard.series.p50': 'P50',
        'artemisApp.iris.dashboard.series.p95': 'P95',
        'artemisApp.iris.dashboard.series.noResponseRate': 'No-response rate',
    };

    instant(key: string, params?: Record<string, string>): string {
        let translated = this.translations[key] ?? key;
        Object.entries(params ?? {}).forEach(([param, value]) => {
            translated = translated.replaceAll(`{{${param}}}`, value).replaceAll(`{{ ${param} }}`, value);
        });
        return translated;
    }
}

describe('IrisDashboardComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<IrisDashboardComponent>;
    let component: IrisDashboardComponent;
    let dashboardService: {
        getOverview: ReturnType<typeof vi.fn>;
        getConfig: ReturnType<typeof vi.fn>;
        getTimeSeries: ReturnType<typeof vi.fn>;
        getBreakdown: ReturnType<typeof vi.fn>;
    };

    const overview: IrisDashboardOverview = {
        totalSessions: 42,
        activeSessions: 30,
        engagementRate: 71.4,
        totalMessages: 120,
        uniqueUsers: 18,
        userMessageCount: 60,
        eligibleSessions: 35,
        noResponseRate: 5,
        noResponseMessageCount: 3,
        noResponseSessionCount: 2,
        thumbsUpRatio: 80,
        thumbsDownRatio: 20,
        thumbsUpAbsoluteRate: 16,
        thumbsDownAbsoluteRate: 4,
        sessionsWithThumbsUp: 10,
        sessionsWithThumbsDown: 4,
        averageResponseTimeSeconds: 2.3,
        p50ResponseTimeSeconds: 1.8,
        p95ResponseTimeSeconds: 5.9,
        totalTokenCostEur: 1.2345,
    };

    const config: IrisDashboardConfig = {
        maxQueryWindowDays: 365,
        staleThresholdMinutes: 5,
        digest: {
            enabled: false,
            cron: '0 0 7 * * *',
            recipients: [],
        },
        alert: {
            enabled: false,
            noResponseRateThreshold: 10,
            checkIntervalMinutes: 30,
            cooldownMinutes: 360,
            lookbackMinutes: 60,
            minimumActiveSessions: 10,
            minimumUserMessages: 20,
            recipients: [],
        },
    };

    const timeSeries: IrisDashboardTimeSeries = {
        metric: 'MESSAGES',
        entries: [
            {
                bucketStart: '2026-01-01T00:00:00Z',
                series: {
                    USER: 2,
                    LLM: 1,
                },
            },
        ],
    };

    const breakdown: IrisDashboardBreakdownEntry[] = [
        {
            name: 'Course chat',
            metrics: {
                sessions: 12,
                messages: 40,
            },
        },
    ];

    beforeEach(async () => {
        dashboardService = {
            getOverview: vi.fn().mockReturnValue(of(overview)),
            getConfig: vi.fn().mockReturnValue(of(config)),
            getTimeSeries: vi.fn((_query: unknown, _span: IrisDashboardSpan, metric: IrisDashboardMetric) => of({ ...timeSeries, metric })),
            getBreakdown: vi.fn().mockReturnValue(of(breakdown)),
        };

        await TestBed.configureTestingModule({
            imports: [IrisDashboardComponent],
            providers: [
                { provide: IrisDashboardService, useValue: dashboardService },
                { provide: TranslateService, useClass: IrisDashboardTestTranslateService },
            ],
        })
            .overrideTemplate(
                IrisDashboardComponent,
                `
                    @for (card of kpiCards(); track card.label) {
                        <div class="kpi-card">{{ card.label }}: {{ card.value }} {{ card.detail }}</div>
                    }
                    @if (loadError()) {
                        <div class="load-error">error</div>
                    }
                `,
            )
            .compileComponents();

        fixture = TestBed.createComponent(IrisDashboardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should load and render KPI cards', () => {
        expect(component).toBeTruthy();
        expect(dashboardService.getOverview).toHaveBeenCalledOnce();
        expect(dashboardService.getConfig).toHaveBeenCalledOnce();
        expect(dashboardService.getTimeSeries).toHaveBeenCalledTimes(7);
        expect(dashboardService.getBreakdown).toHaveBeenCalledTimes(3);

        const text = fixture.nativeElement.textContent;
        expect(text).toContain('Total Sessions: 42');
        expect(text).toContain('No-Response Rate: 5.0%');
        expect(text).toContain('Token Cost: EUR 1.2345');
    });

    it('should use monthly buckets for the year range', () => {
        vi.useFakeTimers();
        try {
            vi.setSystemTime(new Date('2025-02-28T12:00:00Z'));
            dashboardService.getOverview.mockClear();
            dashboardService.getConfig.mockClear();
            dashboardService.getTimeSeries.mockClear();
            dashboardService.getBreakdown.mockClear();

            const testComponent = component as unknown as IrisDashboardComponentTestAccess;
            testComponent.selectedRange = 'YEAR';
            testComponent.onFilterChange();

            expect(dashboardService.getTimeSeries).toHaveBeenCalledTimes(7);
            expect(dashboardService.getTimeSeries.mock.calls.every((call) => call[1] === 'MONTH')).toBe(true);
            const query = dashboardService.getOverview.mock.calls.at(-1)?.[0] as { from: Date; to: Date };
            expect(query.to.getTime() - query.from.getTime()).toBe(365 * 24 * 60 * 60 * 1000);
        } finally {
            vi.useRealTimers();
        }
    });

    it('should clamp month-end range starts', () => {
        vi.useFakeTimers();
        try {
            dashboardService.getOverview.mockClear();
            dashboardService.getConfig.mockClear();
            dashboardService.getTimeSeries.mockClear();
            dashboardService.getBreakdown.mockClear();

            const testComponent = component as unknown as IrisDashboardComponentTestAccess;
            vi.setSystemTime(new Date('2025-03-31T12:00:00Z'));
            testComponent.selectedRange = 'MONTH';
            testComponent.onFilterChange();
            let query = dashboardService.getOverview.mock.calls.at(-1)?.[0] as { from: Date; to: Date };
            expect(query.from.toISOString()).toBe('2025-02-28T12:00:00.000Z');

            vi.setSystemTime(new Date('2025-05-31T12:00:00Z'));
            testComponent.selectedRange = 'QUARTER';
            testComponent.onFilterChange();
            query = dashboardService.getOverview.mock.calls.at(-1)?.[0] as { from: Date; to: Date };
            expect(query.from.toISOString()).toBe('2025-02-28T12:00:00.000Z');
        } finally {
            vi.useRealTimers();
        }
    });

    it('should convert time series entries into chart data', () => {
        const testComponent = component as unknown as IrisDashboardComponentTestAccess;
        testComponent.timeSeries.set({
            MESSAGES: {
                metric: 'MESSAGES',
                entries: [
                    {
                        bucketStart: '2026-01-01T00:00:00Z',
                        series: {
                            USER: 2,
                            LLM: 1,
                            ARTIFACT: 1,
                        },
                    },
                ],
            },
            NO_RESPONSE_RATE: {
                metric: 'NO_RESPONSE_RATE',
                entries: [
                    {
                        bucketStart: '2026-01-01T00:00:00Z',
                        series: {
                            NO_RESPONSE_RATE: 7.5,
                        },
                    },
                ],
            },
            RESPONSE_TIME: {
                metric: 'RESPONSE_TIME',
                entries: [
                    {
                        bucketStart: '2026-01-01T00:00:00Z',
                        series: {
                            AVERAGE: 2.5,
                            P50: 2,
                            P95: 5,
                        },
                    },
                ],
            },
            RATINGS: {
                metric: 'RATINGS',
                entries: [
                    {
                        bucketStart: '2026-01-01T00:00:00Z',
                        series: {
                            THUMBS_UP: 2,
                            THUMBS_DOWN: 1,
                            UNRATED: 3,
                        },
                    },
                ],
            },
        });

        const barData = testComponent.barData('MESSAGES');
        const lineData = testComponent.lineData('NO_RESPONSE_RATE');
        const responseTimeData = testComponent.lineData('RESPONSE_TIME');
        const ratingData = testComponent.barData('RATINGS');

        expect(barData).toHaveLength(1);
        expect(barData[0].series).toEqual(
            expect.arrayContaining([
                { name: 'User', value: 2 },
                { name: 'Iris', value: 1 },
                { name: 'Artifact', value: 1 },
            ]),
        );
        expect(lineData).toHaveLength(1);
        expect(lineData[0].name).toBe('No-response rate');
        expect(lineData[0].series[0].value).toBe(7.5);
        expect(responseTimeData.map((entry) => entry.name)).toEqual(['Average', 'P50', 'P95']);
        expect(ratingData[0].series).toEqual(
            expect.arrayContaining([
                { name: 'Thumbs Up', value: 2 },
                { name: 'Thumbs Down', value: 1 },
                { name: 'Unrated', value: 3 },
            ]),
        );
    });

    it('should format bucket labels in UTC', () => {
        const originalTimeZone = process.env.TZ;
        process.env.TZ = 'America/Los_Angeles';
        try {
            const testComponent = component as unknown as IrisDashboardComponentTestAccess;
            testComponent.timeSeries.set({
                MESSAGES: {
                    metric: 'MESSAGES',
                    entries: [
                        {
                            bucketStart: '2026-05-01T00:00:00Z',
                            series: {
                                USER: 1,
                            },
                        },
                    ],
                },
            });

            expect(testComponent.barData('MESSAGES')[0].name).toBe('May 01');
        } finally {
            if (originalTimeZone === undefined) {
                delete process.env.TZ;
            } else {
                process.env.TZ = originalTimeZone;
            }
        }
    });

    it('should ignore stale dashboard responses', () => {
        const oldOverview = new Subject<IrisDashboardOverview>();
        const newOverview = new Subject<IrisDashboardOverview>();
        dashboardService.getOverview.mockReset();
        dashboardService.getConfig.mockReturnValue(of(config));
        dashboardService.getTimeSeries.mockImplementation((_query: unknown, _span: IrisDashboardSpan, metric: IrisDashboardMetric) => of({ ...timeSeries, metric }));
        dashboardService.getBreakdown.mockReturnValue(of(breakdown));
        dashboardService.getOverview.mockReturnValueOnce(oldOverview).mockReturnValueOnce(newOverview);

        const testComponent = component as unknown as IrisDashboardComponentTestAccess;
        testComponent.loadDashboard();
        testComponent.loadDashboard();

        newOverview.next({ ...overview, totalSessions: 100 });
        newOverview.complete();
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain('Total Sessions: 100');

        oldOverview.next({ ...overview, totalSessions: 1 });
        oldOverview.complete();
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain('Total Sessions: 100');
    });
});
