import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiBarChartComponent, TumAetUiBarChartConfig, TumAetUiChartDatumContext, TumAetUiSelectButtonComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { ChartMultiSeriesEntry, ChartSeriesEntry } from 'app/shared-ui/chart/chart-data.model';
import { TumAetUiChartData, normalizedStackedBarChart } from 'app/shared-ui/chart/tum-aet-ui-chart-adapters';
import { MetisCheckStatus, MetisDashboardRowDTO, MetisQuestionDTO } from 'app/metis/shared/entities/metis.model';
import { RatingSeverity, ratingSeverity } from 'app/metis/shared/rating-scale.util';

/** What the bars are grouped by: the student's tutorial group, or the file a question was about. */
export type RatingDistributionMode = 'group' | 'file';

/** The lowest rating of every Likert scale; the highest one is the scale's number of points. */
const LOWEST_RATING = 1;

/** The five steps of the rating scale, lowest first: the segments of every bar, each in the colour of its step. */
const RATING_STEPS: readonly RatingSeverity[] = ['lowest', 'low', 'neutral', 'high', 'highest'];

/** The legend spells out the ends and the middle of the scale; the steps in between show their rating values alone. */
const LEGEND_KEYS: Partial<Record<RatingSeverity, string>> = {
    lowest: 'artemisApp.metis.dashboard.distribution.legendLowest',
    neutral: 'artemisApp.metis.dashboard.distribution.legendMiddle',
    highest: 'artemisApp.metis.dashboard.distribution.legendHighest',
};

/** Height of one bar row, and what the value axis, the legend and the padding need on top of the rows. */
const ROW_HEIGHT_PX = 36;
const CHART_CHROME_PX = 96;

/** The ratings that fall into one bar of the chart, and what the bar is called. */
interface RatingBucket {
    name: string;
    scores: number[];
}

/** One segment of every bar: a step of the scale, the rating values of the scale that land on it, and its legend label. */
interface RatingStep {
    severity: RatingSeverity;
    /** The values that share the step, ascending: one each on a five-point scale, two at either end of a seven-point one. */
    values: number[];
    label: string;
}

interface ModeOption {
    label: string;
    value: RatingDistributionMode;
}

function isSeriesEntry(meta: unknown): meta is ChartSeriesEntry {
    return typeof meta === 'object' && meta !== null && 'value' in meta;
}

function mainQuestions(row: MetisDashboardRowDTO): MetisQuestionDTO[] {
    return row.questions.filter((question) => question.parentQuestionId === undefined);
}

/** The rating of the first answer to each main question; follow-ups are questions of their own and stay out. */
function firstAnswerScores(questions: MetisQuestionDTO[]): number[] {
    return questions.map((question) => question.llmScore).filter((score): score is number => score !== undefined);
}

function average(scores: number[]): number | undefined {
    return scores.length ? scores.reduce((sum, score) => sum + score, 0) / scores.length : undefined;
}

/**
 * The theme token of a step's colour, passed as a `var()` reference the way the chart adapters pass the graph
 * colors: the SVG chart resolves it itself, so a theme switch needs no re-render.
 */
function ratingColor(severity: RatingSeverity): string {
    return `var(--rating-scale-${severity})`;
}

/** "1–2" for a step that two values of the scale share, "3" for a step with one value. */
function rangeText(values: number[]): string {
    return values.length === 1 ? `${values[0]}` : `${values[0]}–${values[values.length - 1]}`;
}

/**
 * The rating distribution of the comprehension check: one 100 % stacked bar per tutorial group (plus one for all
 * students) or per focus file, with one segment per step of the rating scale. A five-point scale has one rating
 * value per step; on a seven-point scale the two lowest and the two highest values share the colour of their end of
 * the scale, so the chart draws five segments rather than seven and the legend shows the range of each.
 *
 * Every student gets questions of their own, so ratings are never compared by question number; they are compared
 * by what the questions share. Only the first answer to each main question of a completed student counts.
 */
@Component({
    selector: 'jhi-rating-distribution',
    templateUrl: './rating-distribution.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FormsModule, TranslateDirective, ArtemisTranslatePipe, TumAetUiBarChartComponent, TumAetUiSelectButtonComponent],
})
export class RatingDistributionComponent {
    private readonly translateService = inject(TranslateService);

    /** Every participation of the exercise; the component picks the completed ones itself. */
    readonly rows = input.required<MetisDashboardRowDTO[]>();
    /** The number of points of the exercise's Likert scale; every rating is read against it. */
    readonly points = input.required<number>();

    protected readonly mode = signal<RatingDistributionMode>('group');

    private readonly locale = getCurrentLocaleSignal(this.translateService);

    protected readonly modeOptions = computed<ModeOption[]>(() => {
        this.locale();
        return [
            { label: this.translateService.instant('artemisApp.metis.dashboard.distribution.byGroup'), value: 'group' },
            { label: this.translateService.instant('artemisApp.metis.dashboard.distribution.byFile'), value: 'file' },
        ];
    });

    /** Only students who finished the check contribute; a half-answered check would skew every bar it belongs to. */
    private readonly completedRows = computed(() => this.rows().filter((row) => row.check.status === MetisCheckStatus.COMPLETED));

    protected readonly completedCount = computed(() => this.completedRows().length);

    /** The steps of the scale with the rating values that land on each, lowest first; a step no value lands on (the middle of a four-point scale, say) is left out. */
    private readonly ratingSteps = computed<RatingStep[]>(() => {
        this.locale();
        const points = this.points();
        const values = Array.from({ length: Math.max(0, points - LOWEST_RATING + 1) }, (_, index) => LOWEST_RATING + index);
        const steps: RatingStep[] = [];
        for (const severity of RATING_STEPS) {
            const stepValues = values.filter((value) => ratingSeverity(value, points) === severity);
            if (stepValues.length > 0) {
                steps.push({ severity, values: stepValues, label: this.legendLabel(severity, stepValues) });
            }
        }
        return steps;
    });

    private readonly buckets = computed<RatingBucket[]>(() => {
        this.locale();
        const rows = this.completedRows();
        if (this.mode() === 'group') {
            const groups = [...new Set(rows.map((row) => row.tutorialGroupTitle).filter((title): title is string => !!title))].sort((a, b) => a.localeCompare(b));
            return [
                {
                    name: this.translateService.instant('artemisApp.metis.dashboard.distribution.allStudents'),
                    scores: rows.flatMap((row) => firstAnswerScores(mainQuestions(row))),
                },
                ...groups.map((group) => ({
                    name: group,
                    scores: rows.filter((row) => row.tutorialGroupTitle === group).flatMap((row) => firstAnswerScores(mainQuestions(row))),
                })),
            ];
        }
        const filePaths = rows.flatMap((row) => mainQuestions(row).map((question) => question.focusFilePath)).filter((file): file is string => !!file);
        const files = [...new Set(filePaths)].sort((a, b) => a.localeCompare(b));
        return files.map((file) => ({
            name: file,
            scores: rows.flatMap((row) => firstAnswerScores(mainQuestions(row).filter((question) => question.focusFilePath === file))),
        }));
    });

    /** The average rating per bar, for the category labels. */
    private readonly averages = computed(() => new Map(this.buckets().map((bucket) => [bucket.name, average(bucket.scores)])));

    /**
     * One series per step of the scale, with the number of ratings of each bar that land on the step. A rating
     * outside the scale (given before the scale was changed, say) is clamped onto its nearest end rather than lost.
     */
    protected readonly chartData = computed<TumAetUiChartData>(() => {
        const points = this.points();
        const steps = this.ratingSteps();
        const entries: ChartMultiSeriesEntry[] = this.buckets().map((bucket) => ({
            name: bucket.name,
            series: steps.map((step) => ({ name: step.label, value: bucket.scores.filter((score) => ratingSeverity(score, points) === step.severity).length })),
        }));
        return normalizedStackedBarChart(
            entries,
            steps.map((step) => ratingColor(step.severity)),
        );
    });

    protected readonly chartHeight = computed(() => this.buckets().length * ROW_HEIGHT_PX + CHART_CHROME_PX);

    protected readonly chartConfig = computed<TumAetUiBarChartConfig>(() => {
        const averages = this.averages();
        return {
            horizontal: true,
            stacked: true,
            percentScale: true,
            maxBarThickness: 22,
            legend: { position: 'bottom' },
            tooltip: { label: (item) => this.tooltipLabel(item) },
            xAxis: { max: 100, tickFormatter: (value) => `${value}%` },
            yAxis: { tickFormatter: (label) => this.labelWithAverage(`${label}`, averages.get(`${label}`)) },
        };
    });

    protected onModeChange(mode: RatingDistributionMode): void {
        this.mode.set(mode);
    }

    /** "4 partial: 12 (25%)": the count of the segment behind the percentage the bar shows. */
    protected tooltipLabel(item: TumAetUiChartDatumContext): string {
        const count = isSeriesEntry(item.meta) ? item.meta.value : 0;
        return this.translateService.instant('artemisApp.metis.dashboard.distribution.tooltip', { label: item.seriesLabel ?? '', count, percent: Math.round(item.value) });
    }

    /** "1–2 no understanding", "3", "4 partial", "5", "6–7 full understanding": the values of a step, with the ends and the middle of the scale spelled out. */
    private legendLabel(severity: RatingSeverity, values: number[]): string {
        const range = rangeText(values);
        const key = LEGEND_KEYS[severity];
        return key ? this.translateService.instant(key, { value: range }) : range;
    }

    private labelWithAverage(label: string, averageScore: number | undefined): string {
        if (averageScore === undefined) {
            return label;
        }
        return `${label} · ${this.translateService.instant('artemisApp.metis.dashboard.distribution.avg', { value: averageScore.toFixed(1) })}`;
    }
}
