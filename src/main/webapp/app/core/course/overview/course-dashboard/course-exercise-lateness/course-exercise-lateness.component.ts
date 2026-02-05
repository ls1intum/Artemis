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

export interface ExerciseLateness {
    exerciseId: number;
    title: string;
    shortName?: string;
    relativeLatestSubmission?: number;
    relativeAverageLatestSubmission?: number;
}

const YOUR_GRAPH_COLOR = GraphColors.BLUE;
const AVERAGE_GRAPH_COLOR = GraphColors.YELLOW;

@Component({
    selector: 'jhi-course-exercise-lateness',
    templateUrl: './course-exercise-lateness.component.html',
    styleUrls: ['./course-exercise-lateness.component.scss'],
    imports: [TranslateDirective, HelpIconComponent, LineChartModule, ArtemisTranslatePipe],
})
export class CourseExerciseLatenessComponent {
    private translateService = inject(TranslateService);
    // Track language changes as a signal to make computed translations reactive
    private readonly langChange = toSignal(this.translateService.onLangChange);

    readonly exerciseLateness = input<ExerciseLateness[]>([]);

    readonly ngxColor: Color = {
        name: 'Lateness in Exercises',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [YOUR_GRAPH_COLOR, AVERAGE_GRAPH_COLOR],
    };

    protected readonly YOUR_GRAPH_COLOR = YOUR_GRAPH_COLOR;
    protected readonly AVERAGE_GRAPH_COLOR = AVERAGE_GRAPH_COLOR;

    readonly yourLatenessLabel = computed(() => {
        this.langChange(); // Establish dependency on language changes
        return this.translateService.instant('artemisApp.courseStudentDashboard.exerciseLateness.yourLatenessLabel');
    });
    readonly averageLatenessLabel = computed(() => {
        this.langChange(); // Establish dependency on language changes
        return this.translateService.instant('artemisApp.courseStudentDashboard.exerciseLateness.averageLatenessLabel');
    });

    readonly ngxData = computed<NgxChartsMultiSeriesDataEntry[]>(() => {
        return [
            {
                name: this.yourLatenessLabel(),
                series: this.exerciseLateness().map((lateness) => {
                    return {
                        name: lateness.shortName?.toUpperCase() || lateness.title,
                        value: round(lateness.relativeLatestSubmission || 100, 1),
                        extra: {
                            title: lateness.title,
                        },
                    };
                }),
            },
            {
                name: this.averageLatenessLabel(),
                series: this.exerciseLateness().map((lateness) => {
                    return {
                        name: lateness.shortName?.toUpperCase() || lateness.title,
                        value: round(lateness.relativeAverageLatestSubmission || 100, 1),
                        extra: {
                            title: lateness.title,
                        },
                    };
                }),
            },
        ];
    });

    readonly yScaleMax = computed(() => {
        const data = this.ngxData();
        const maxRelativeTime = Math.max(...data.flatMap((d) => d.series.map((series) => series.value)));
        return Math.max(100, Math.ceil(maxRelativeTime / 10) * 10);
    });

    readonly isDataAvailable = computed(() => {
        const data = this.ngxData();
        return data && data.length > 0 && data.some((d) => d.series.length > 0);
    });
}
