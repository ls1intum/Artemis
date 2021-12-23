import { AfterViewInit, Component, Input, OnChanges } from '@angular/core';
import { ExerciseScoresChartService, ExerciseScoresDTO } from 'app/overview/visualizations/exercise-scores-chart.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { cloneDeep, sortBy } from 'lodash-es';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { round } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-exercise-scores-chart',
    templateUrl: './exercise-scores-chart.component.html',
    styleUrls: ['./exercise-scores-chart.component.scss'],
})
export class ExerciseScoresChartComponent implements AfterViewInit, OnChanges {
    @Input()
    filteredExerciseIDs: number[];

    courseId: number;
    isLoading = false;
    exerciseScores: ExerciseScoresDTO[] = [];
    excludedExerciseScores: ExerciseScoresDTO[] = [];

    readonly Math = Math;

    // ngx
    ngxData: any[] = [];
    backUpData: any[] = [];
    xAxisLabel = this.translateService.instant('artemisApp.exercise-scores-chart.xAxis');
    yAxisLabel = this.translateService.instant('artemisApp.exercise-scores-chart.yAxis');
    ngxColor = {
        name: 'Performance in Exercises',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: ['#87ceeb', '#fa8072', '#32cd32'],
    } as Color; // colors: blue, red, green
    backUpColor = cloneDeep(this.ngxColor);
    yourScoreLabel = this.translateService.instant('artemisApp.exercise-scores-chart.yourScoreLabel');
    averageScoreLabel = this.translateService.instant('artemisApp.exercise-scores-chart.averageScoreLabel');
    maximumScoreLabel = this.translateService.instant('artemisApp.exercise-scores-chart.maximumScoreLabel');
    maxScale = 101;

    constructor(
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
        private exerciseScoresChartService: ExerciseScoresChartService,
        private translateService: TranslateService,
    ) {}

    ngAfterViewInit() {
        this.activatedRoute.parent?.parent?.params.subscribe((params) => {
            this.courseId = +params['courseId'];
            if (this.courseId) {
                this.loadDataAndInitializeChart();
            }
        });
    }

    ngOnChanges(): void {
        this.initializeChart();
    }

    private loadDataAndInitializeChart(): void {
        this.isLoading = true;
        this.exerciseScoresChartService
            .getExerciseScoresForCourse(this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(
                (exerciseScoresResponse) => {
                    this.exerciseScores = exerciseScoresResponse.body!;
                    this.initializeChart();
                },
                (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            );
    }

    private initializeChart(): void {
        this.exerciseScores = this.exerciseScores.concat(this.excludedExerciseScores);
        this.excludedExerciseScores = this.exerciseScores.filter((score) => this.filteredExerciseIDs.includes(score.exerciseId!));
        this.exerciseScores = this.exerciseScores.filter((score) => !this.filteredExerciseIDs.includes(score.exerciseId!));
        // we show all the exercises ordered by their release data
        const sortedExerciseScores = sortBy(this.exerciseScores, (exerciseScore) => exerciseScore.releaseDate);
        this.addData(sortedExerciseScores);
    }

    /**
     * Converts the exerciseScoresDTOs into dedicated objects that can be processed by ngx-charts in order to
     * visualize the scores and pushes them to ngxData and backUpData
     * @param exerciseScoresDTOs array of objects containing the students score, the average score for this exercise and
     * the max score achieved for this exercise by a student as well as other detailed information of the exericse
     * @private
     */
    private addData(exerciseScoresDTOs: ExerciseScoresDTO[]): void {
        this.ngxData = [];
        const scoreSeries: any[] = [];
        const averageSeries: any[] = [];
        const bestScoreSeries: any[] = [];
        exerciseScoresDTOs.forEach((exerciseScoreDTO) => {
            const extraInformation = {
                exerciseId: exerciseScoreDTO.exerciseId,
                exerciseType: exerciseScoreDTO.exerciseType,
            };
            // adapt the y axis max
            this.maxScale = Math.max(
                round(exerciseScoreDTO.scoreOfStudent!),
                round(exerciseScoreDTO.averageScoreAchieved!),
                round(exerciseScoreDTO.maxScoreAchieved!),
                this.maxScale,
            );
            scoreSeries.push({ name: exerciseScoreDTO.exerciseTitle, value: round(exerciseScoreDTO.scoreOfStudent!) + 1, ...extraInformation });
            averageSeries.push({ name: exerciseScoreDTO.exerciseTitle, value: round(exerciseScoreDTO.averageScoreAchieved!) + 1, ...extraInformation });
            bestScoreSeries.push({ name: exerciseScoreDTO.exerciseTitle, value: round(exerciseScoreDTO.maxScoreAchieved!) + 1, ...extraInformation });
        });

        const studentScore = { name: this.yourScoreLabel, series: scoreSeries };
        const averageScore = { name: this.averageScoreLabel, series: averageSeries };
        const bestScore = { name: this.maximumScoreLabel, series: bestScoreSeries };
        this.ngxData.push(studentScore);
        this.ngxData.push(averageScore);
        this.ngxData.push(bestScore);
        this.ngxData = [...this.ngxData];
        this.backUpData = [...this.ngxData];
    }

    /**
     * Provides the functionality when the user interacts with the chart by clicking on it.
     * If the users click on a node in the chart, they get delegated to the corresponding exercise detail page.
     * If the users click on an entry in the legend, the corresponding line disappears or reappears depending on its previous state
     * @param data the event sent by the framework
     */
    onSelect(data: any): void {
        // delegate to the corresponding exericse if chart node is clicked
        if (data.exerciseId) {
            this.navigateToExercise(data.exerciseId);
        } else {
            // if a legend label is clicked, the corresponding line has to disappear or reappear
            const name = JSON.parse(JSON.stringify(data)) as string;
            // find the affected line in the dataset
            const index = this.ngxData.findIndex((dataPack: any) => {
                const dataName = dataPack.name as string;
                return dataName === name;
            });
            // check whether the line is currently displayed
            if (this.ngxColor.domain[index] !== 'rgba(255,255,255,0)') {
                const placeHolder = cloneDeep(this.ngxData[index]);
                placeHolder.series.forEach((piece: any) => {
                    piece.value = 0;
                });
                // exchange actual line with all-zero line and make color transparent
                this.ngxData[index] = placeHolder;
                this.ngxColor.domain[index] = 'rgba(255,255,255,0)';
            } else {
                // if the line is currently hidden, the color and the values are reset
                this.ngxColor.domain[index] = this.backUpColor.domain[index];
                this.ngxData[index] = this.backUpData[index];
            }
            // trigger a chart update
            this.ngxData = [...this.ngxData];
        }
    }

    /**
     * We navigate to the exercise sub page when the user clicks on a data point
     */
    navigateToExercise(exerciseId: number): void {
        this.router.navigate(['courses', this.courseId, 'exercises', exerciseId]);
    }
}
