import { Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { Color, LineChartModule, ScaleType } from '@swimlane/ngx-charts';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { NgxChartsMultiSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { round } from 'app/shared/util/utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

export interface ExercisePerformance {
    exerciseId: number;
    title: string;
    shortName?: string;
    score?: number;
    averageScore?: number;
}

const YOUR_GRAPH_COLOR = GraphColors.BLUE;
const AVERAGE_GRAPH_COLOR = GraphColors.YELLOW;

@Component({
    selector: 'jhi-course-exercise-performance',
    templateUrl: './course-exercise-performance.component.html',
    styleUrls: ['./course-exercise-performance.component.scss'],
    imports: [TranslateDirective, HelpIconComponent, LineChartModule, ArtemisTranslatePipe],
})
export class CourseExercisePerformanceComponent {
    private translateService = inject(TranslateService);
    // Track language changes as a signal to make computed translations reactive
    private readonly langChange = toSignal(this.translateService.onLangChange);

    readonly exercisePerformance = input<ExercisePerformance[]>([]);

    readonly ngxColor: Color = {
        name: 'Performance in Exercises',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [YOUR_GRAPH_COLOR, AVERAGE_GRAPH_COLOR],
    };

    protected readonly YOUR_GRAPH_COLOR = YOUR_GRAPH_COLOR;
    protected readonly AVERAGE_GRAPH_COLOR = AVERAGE_GRAPH_COLOR;

    readonly yourScoreLabel = computed(() => {
        this.langChange(); // Establish dependency on language changes
        return this.translateService.instant('artemisApp.courseStudentDashboard.exercisePerformance.yourScoreLabel');
    });
    readonly averageScoreLabel = computed(() => {
        this.langChange(); // Establish dependency on language changes
        return this.translateService.instant('artemisApp.courseStudentDashboard.exercisePerformance.averageScoreLabel');
    });

    readonly ngxData = computed<NgxChartsMultiSeriesDataEntry[]>(() => {
        return [
            {
                name: this.yourScoreLabel(),
                series: this.exercisePerformance().map((performance) => {
                    return {
                        name: performance.shortName?.toUpperCase() || performance.title,
                        value: round(performance.score || 0, 1),
                        extra: {
                            title: performance.title,
                        },
                    };
                }),
            },
            {
                name: this.averageScoreLabel(),
                series: this.exercisePerformance().map((performance) => {
                    return {
                        name: performance.shortName?.toUpperCase() || performance.title,
                        value: round(performance.averageScore || 0, 1),
                        extra: {
                            title: performance.title,
                        },
                    };
                }),
            },
        ];
    });

    readonly yScaleMax = computed(() => {
        const data = this.ngxData();
        const maxScore = Math.max(...data.flatMap((d) => d.series.map((series) => series.value)));
        return Math.max(100, Math.ceil(maxScore / 10) * 10);
    });

    readonly isDataAvailable = computed(() => {
        const data = this.ngxData();
        return data && data.length > 0 && data.some((d) => d.series.length > 0);
    });
}
