import { ChangeDetectionStrategy, Component, OnInit, ViewEncapsulation, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BarChartModule, Color, LegendPosition, LineChartModule, ScaleType } from '@swimlane/ngx-charts';
import { faRotateRight } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { Select } from 'primeng/select';
import { ButtonDirective } from 'primeng/button';
import { Card } from 'primeng/card';
import { PanelModule } from 'primeng/panel';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TableModule } from 'primeng/table';

import { AdminTitleBarActionsDirective } from 'app/core/admin/shared/admin-title-bar-actions.directive';
import { AdminTitleBarTitleDirective } from 'app/core/admin/shared/admin-title-bar-title.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisDashboardService } from './iris-dashboard.service';
import {
    ChartDataEntry,
    IrisDashboardBreakdownDimension,
    IrisDashboardBreakdownEntry,
    IrisDashboardConfig,
    IrisDashboardMetric,
    IrisDashboardOverview,
    IrisDashboardQuery,
    IrisDashboardRange,
    IrisDashboardSessionType,
    IrisDashboardSpan,
    IrisDashboardTimeSeries,
} from './iris-dashboard.model';

interface SelectOption<T> {
    label: string;
    value: T | undefined;
}

interface ChartDefinition {
    metric: IrisDashboardMetric;
    title: string;
    type: 'stacked-bar' | 'grouped-bar' | 'line';
}

interface KpiCard {
    label: string;
    value: string;
    detail?: string;
}

@Component({
    selector: 'jhi-iris-dashboard',
    templateUrl: './iris-dashboard.component.html',
    styleUrl: './iris-dashboard.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
    host: { class: 'iris-dashboard' },
    imports: [
        AdminTitleBarActionsDirective,
        AdminTitleBarTitleDirective,
        BarChartModule,
        ButtonDirective,
        Card,
        FaIconComponent,
        FormsModule,
        LineChartModule,
        PanelModule,
        ProgressSpinnerModule,
        Select,
        TableModule,
        TranslateDirective,
    ],
})
export class IrisDashboardComponent implements OnInit {
    private readonly irisDashboardService = inject(IrisDashboardService);
    private readonly translateService = inject(TranslateService);

    protected readonly faRotateRight = faRotateRight;
    protected readonly legendPosition = LegendPosition.Below;
    protected readonly translationBase = 'artemisApp.iris.dashboard';
    protected readonly kpiSummaryLabel = this.t('aria.kpiSummary');
    protected readonly chartsLabel = this.t('aria.charts');
    protected readonly breakdownsLabel = this.t('aria.breakdowns');
    protected readonly loadingLabel = this.t('aria.loading');

    protected readonly loading = signal(false);
    protected readonly loadError = signal(false);
    protected readonly overview = signal<IrisDashboardOverview | undefined>(undefined);
    protected readonly config = signal<IrisDashboardConfig | undefined>(undefined);
    protected readonly timeSeries = signal<Partial<Record<IrisDashboardMetric, IrisDashboardTimeSeries>>>({});
    protected readonly breakdowns = signal<Partial<Record<IrisDashboardBreakdownDimension, IrisDashboardBreakdownEntry[]>>>({});

    protected selectedRange: IrisDashboardRange = 'MONTH';
    protected selectedChatMode: IrisDashboardSessionType | undefined;
    protected activeBreakdownTab = 0;
    private loadRequestId = 0;

    protected readonly rangeOptions: SelectOption<IrisDashboardRange>[] = [
        { label: this.t('ranges.day'), value: 'DAY' },
        { label: this.t('ranges.week'), value: 'WEEK' },
        { label: this.t('ranges.month'), value: 'MONTH' },
        { label: this.t('ranges.quarter'), value: 'QUARTER' },
        { label: this.t('ranges.year'), value: 'YEAR' },
    ];

    protected readonly chatModeOptions: SelectOption<IrisDashboardSessionType>[] = [
        { label: this.t('chatModes.all'), value: undefined },
        { label: this.t('chatModes.programming'), value: 'PROGRAMMING_EXERCISE_CHAT' },
        { label: this.t('chatModes.text'), value: 'TEXT_EXERCISE_CHAT' },
        { label: this.t('chatModes.course'), value: 'COURSE_CHAT' },
        { label: this.t('chatModes.lecture'), value: 'LECTURE_CHAT' },
        { label: this.t('chatModes.tutorSuggestion'), value: 'TUTOR_SUGGESTION' },
    ];

    protected readonly chartDefinitions: ChartDefinition[] = [
        { metric: 'SESSIONS', title: this.t('charts.sessions'), type: 'stacked-bar' },
        { metric: 'MESSAGES', title: this.t('charts.messages'), type: 'grouped-bar' },
        { metric: 'NO_RESPONSE_RATE', title: this.t('charts.noResponse'), type: 'line' },
        { metric: 'RESPONSE_TIME', title: this.t('charts.responseTime'), type: 'line' },
        { metric: 'RATINGS', title: this.t('charts.ratings'), type: 'stacked-bar' },
        { metric: 'TOKEN_COST', title: this.t('charts.tokenCost'), type: 'line' },
        { metric: 'ENGAGEMENT', title: this.t('charts.engagement'), type: 'line' },
    ];

    protected readonly chartColorScheme: Color = {
        name: 'iris-dashboard',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: ['#2f6f9f', '#6b8e23', '#c27c2c', '#8b5fbf', '#b94a48', '#2f8f83', '#5b6770'],
    };

    protected readonly kpiCards = computed<KpiCard[]>(() => {
        const overview = this.overview();
        if (!overview) {
            return [];
        }
        return [
            { label: this.t('kpis.totalSessions'), value: this.integer(overview.totalSessions) },
            { label: this.t('kpis.activeSessions'), value: this.integer(overview.activeSessions) },
            { label: this.t('kpis.engagementRate'), value: this.percent(overview.engagementRate) },
            { label: this.t('kpis.uniqueUsers'), value: this.integer(overview.uniqueUsers) },
            {
                label: this.t('kpis.noResponseRate'),
                value: this.percent(overview.noResponseRate),
                detail: this.t('kpis.noResponseDetail', {
                    messages: this.integer(overview.noResponseMessageCount),
                    sessions: this.integer(overview.noResponseSessionCount),
                }),
            },
            {
                label: this.t('kpis.thumbsUp'),
                value: this.percent(overview.thumbsUpRatio),
                detail: this.t('kpis.llmMessageDetail', { rate: this.percent(overview.thumbsUpAbsoluteRate) }),
            },
            {
                label: this.t('kpis.thumbsDown'),
                value: this.percent(overview.thumbsDownRatio),
                detail: this.t('kpis.llmMessageDetail', { rate: this.percent(overview.thumbsDownAbsoluteRate) }),
            },
            { label: this.t('kpis.sessionsWithThumbsUp'), value: this.integer(overview.sessionsWithThumbsUp) },
            { label: this.t('kpis.sessionsWithThumbsDown'), value: this.integer(overview.sessionsWithThumbsDown) },
            { label: this.t('kpis.averageResponseTime'), value: this.seconds(overview.averageResponseTimeSeconds) },
            { label: this.t('kpis.responseTimePercentiles'), value: `${this.seconds(overview.p50ResponseTimeSeconds)} / ${this.seconds(overview.p95ResponseTimeSeconds)}` },
            { label: this.t('kpis.tokenCost'), value: this.euro(overview.totalTokenCostEur) },
        ];
    });

    ngOnInit(): void {
        this.loadDashboard();
    }

    protected loadDashboard(): void {
        const requestId = ++this.loadRequestId;
        const query = this.query();
        const span = this.spanForRange(this.selectedRange);
        this.loading.set(true);
        this.loadError.set(false);

        forkJoin({
            overview: this.irisDashboardService.getOverview(query),
            config: this.irisDashboardService.getConfig(),
            sessions: this.irisDashboardService.getTimeSeries(query, span, 'SESSIONS'),
            messages: this.irisDashboardService.getTimeSeries(query, span, 'MESSAGES'),
            noResponseRate: this.irisDashboardService.getTimeSeries(query, span, 'NO_RESPONSE_RATE'),
            responseTime: this.irisDashboardService.getTimeSeries(query, span, 'RESPONSE_TIME'),
            ratings: this.irisDashboardService.getTimeSeries(query, span, 'RATINGS'),
            tokenCost: this.irisDashboardService.getTimeSeries(query, span, 'TOKEN_COST'),
            engagement: this.irisDashboardService.getTimeSeries(query, span, 'ENGAGEMENT'),
            chatModeBreakdown: this.irisDashboardService.getBreakdown(query, 'CHAT_MODE'),
            courseBreakdown: this.irisDashboardService.getBreakdown(query, 'COURSE'),
            modelBreakdown: this.irisDashboardService.getBreakdown(query, 'MODEL'),
        })
            .pipe(
                finalize(() => {
                    if (requestId === this.loadRequestId) {
                        this.loading.set(false);
                    }
                }),
            )
            .subscribe({
                next: (result) => {
                    if (requestId !== this.loadRequestId) {
                        return;
                    }
                    this.overview.set(result.overview);
                    this.config.set(result.config);
                    this.timeSeries.set({
                        SESSIONS: result.sessions,
                        MESSAGES: result.messages,
                        NO_RESPONSE_RATE: result.noResponseRate,
                        RESPONSE_TIME: result.responseTime,
                        RATINGS: result.ratings,
                        TOKEN_COST: result.tokenCost,
                        ENGAGEMENT: result.engagement,
                    });
                    this.breakdowns.set({
                        CHAT_MODE: result.chatModeBreakdown,
                        COURSE: result.courseBreakdown,
                        MODEL: result.modelBreakdown,
                    });
                },
                error: () => {
                    if (requestId === this.loadRequestId) {
                        this.loadError.set(true);
                    }
                },
            });
    }

    protected onFilterChange(): void {
        this.loadDashboard();
    }

    protected barData(metric: IrisDashboardMetric): ChartDataEntry[] {
        return (
            this.timeSeries()[metric]?.entries.map((entry) => ({
                name: this.bucketLabel(entry.bucketStart),
                series: Object.entries(entry.series).map(([name, value]) => ({ name: this.seriesLabel(name), value })),
            })) ?? []
        );
    }

    protected lineData(metric: IrisDashboardMetric): ChartDataEntry[] {
        const entries = this.timeSeries()[metric]?.entries ?? [];
        const seriesNames = [...new Set(entries.flatMap((entry) => Object.keys(entry.series)))];
        return seriesNames.map((seriesName) => ({
            name: this.seriesLabel(seriesName),
            series: entries.map((entry) => ({ name: this.bucketLabel(entry.bucketStart), value: entry.series[seriesName] ?? 0 })),
        }));
    }

    protected chatModeBreakdown(): IrisDashboardBreakdownEntry[] {
        return this.breakdowns().CHAT_MODE ?? [];
    }

    protected courseBreakdown(): IrisDashboardBreakdownEntry[] {
        return this.breakdowns().COURSE ?? [];
    }

    protected modelBreakdown(): IrisDashboardBreakdownEntry[] {
        return this.breakdowns().MODEL ?? [];
    }

    protected metric(entry: IrisDashboardBreakdownEntry, key: string): number {
        return entry.metrics[key] ?? 0;
    }

    protected seriesLabel(series: string): string {
        const labels: Record<string, string> = {
            PROGRAMMING_EXERCISE_CHAT: this.t('series.programming'),
            TEXT_EXERCISE_CHAT: this.t('series.text'),
            COURSE_CHAT: this.t('series.course'),
            LECTURE_CHAT: this.t('series.lecture'),
            TUTOR_SUGGESTION: this.t('series.tutorSuggestion'),
            CHAT_ATTRIBUTED: this.t('series.chatAttributed'),
            OTHER_IRIS: this.t('series.otherIris'),
            USER: this.t('series.user'),
            LLM: this.t('series.iris'),
            ARTIFACT: this.t('series.artifact'),
            THUMBS_UP: this.t('series.thumbsUp'),
            THUMBS_DOWN: this.t('series.thumbsDown'),
            UNRATED: this.t('series.unrated'),
            AVERAGE: this.t('series.average'),
            P50: this.t('series.p50'),
            P95: this.t('series.p95'),
            NO_RESPONSE_RATE: this.t('series.noResponseRate'),
            ENGAGEMENT_RATE: this.t('series.engagementRate'),
        };
        return labels[series] ?? series.replaceAll('_', ' ');
    }

    protected percent(value: number): string {
        return `${this.decimal(value, 1, 1)}%`;
    }

    protected seconds(value: number): string {
        return `${this.decimal(value, 1, 1)}s`;
    }

    protected euro(value: number): string {
        return `EUR ${this.decimal(value, 1, 4)}`;
    }

    protected integer(value: number): string {
        return this.decimal(value, 0, 0);
    }

    private query(): IrisDashboardQuery {
        const to = new Date();
        const from = new Date(to);
        switch (this.selectedRange) {
            case 'DAY':
                from.setUTCDate(from.getUTCDate() - 1);
                break;
            case 'WEEK':
                from.setUTCDate(from.getUTCDate() - 7);
                break;
            case 'MONTH':
                this.subtractUtcMonths(from, 1);
                break;
            case 'QUARTER':
                this.subtractUtcMonths(from, 3);
                break;
            case 'YEAR':
                from.setUTCDate(from.getUTCDate() - 365);
                break;
        }
        return { from, to, chatMode: this.selectedChatMode };
    }

    private spanForRange(range: IrisDashboardRange): IrisDashboardSpan {
        return range === 'YEAR' ? 'MONTH' : range === 'QUARTER' ? 'WEEK' : 'DAY';
    }

    private bucketLabel(value: string): string {
        const options: Intl.DateTimeFormatOptions =
            this.selectedRange === 'YEAR' ? { month: 'short', year: '2-digit', timeZone: 'UTC' } : { month: 'short', day: '2-digit', timeZone: 'UTC' };
        return new Intl.DateTimeFormat(undefined, options).format(new Date(value));
    }

    private subtractUtcMonths(date: Date, months: number): void {
        const originalDay = date.getUTCDate();
        date.setUTCDate(1);
        date.setUTCMonth(date.getUTCMonth() - months);
        const lastDayOfTargetMonth = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 0)).getUTCDate();
        date.setUTCDate(Math.min(originalDay, lastDayOfTargetMonth));
    }

    private decimal(value: number, minimumFractionDigits: number, maximumFractionDigits: number): string {
        return new Intl.NumberFormat(undefined, { minimumFractionDigits, maximumFractionDigits }).format(value);
    }

    private t(key: string, params?: Record<string, string>): string {
        return this.translateService.instant(`${this.translationBase}.${key}`, params);
    }
}
