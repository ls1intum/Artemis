import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TooltipItem } from 'chart.js';
import { ActivatedRoute, Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { AbstractQuizStatisticComponent } from 'app/quiz/manage/statistics/quiz-statistics';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { calculateMaxScore } from 'app/quiz/manage/statistics/quiz-statistic/quiz-statistics.utils';
import { EMPTY, startWith, switchMap } from 'rxjs';
import { round } from 'app/foundation/util/utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ChartModule } from 'primeng/chart';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { QuizStatisticsFooterComponent } from '../quiz-statistics-footer/quiz-statistics-footer.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { QuizStatisticsOverviewResponse } from 'app/quiz/manage/statistics/quiz-statistics-response.model';

@Component({
    selector: 'jhi-quiz-statistic',
    templateUrl: './quiz-statistic.component.html',
    styleUrls: ['../quiz-point-statistic/quiz-point-statistic.component.scss'],
    imports: [TranslateDirective, ChartModule, FaIconComponent, QuizStatisticsFooterComponent, ArtemisTranslatePipe],
})
export class QuizStatisticComponent extends AbstractQuizStatisticComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private accountService = inject(AccountService);
    private quizExerciseService = inject(QuizExerciseService);
    private websocketService = inject(WebsocketService);
    private destroyRef = inject(DestroyRef);

    readonly quizExercise = signal<QuizStatisticsOverviewResponse | undefined>(undefined);

    label: string[] = [];
    backgroundColor: string[] = [];
    ratedAverage = 0;
    unratedAverage = 0;

    maxScore!: number; // set in loadQuizSuccess() via calculateMaxScore() before loadData() reads it
    websocketChannelForData!: string; // set in ngOnInit() from the route params

    // Icons
    faSync = faSync;

    ngOnInit() {
        this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.setAxisLabels('artemisApp.showStatistic.quizStatistic.xAxes', 'artemisApp.showStatistic.quizStatistic.yAxes');
            this.chartEntries.update((entries) => {
                if (!entries.length) {
                    return entries;
                }
                const updated = [...entries];
                const lastEntry = updated[updated.length - 1];
                updated[updated.length - 1] = { name: this.translateService.instant('artemisApp.showStatistic.quizStatistic.average'), value: lastEntry.value };
                return updated;
            });
        });
        this.route.params
            .pipe(
                switchMap((params) => {
                    const exerciseId = params['exerciseId'];
                    this.websocketChannelForData = '/topic/statistic/' + exerciseId;
                    if (!this.accountService.isAtLeastTutor()) {
                        return EMPTY;
                    }
                    return this.websocketService.subscribe<number>(this.websocketChannelForData).pipe(
                        startWith(exerciseId),
                        switchMap(() => this.quizExerciseService.findStatisticsOverview(exerciseId)),
                    );
                }),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((res) => {
                this.loadQuizSuccess(res.body!);
            });
    }

    /**
     * This functions loads the Quiz, which is necessary to build the Web-Template
     * And it loads the new Data if the Websocket has been notified
     *
     * @param quiz the quizExercise, which this quiz-statistic presents.
     */
    loadQuizSuccess(quiz: QuizStatisticsOverviewResponse) {
        // if the Student finds a way to the Website -> the Student will be sent back to Courses
        if (!this.accountService.isAtLeastTutor()) {
            void this.router.navigate(['/courses']);
        }
        this.quizExercise.set(quiz);
        this.maxScore = calculateMaxScore(quiz);
        this.loadData();
    }

    /**
     * load the Data from the Json-entity to the chart: myChart
     */
    loadData() {
        const quizExercise = this.quizExercise();
        if (!quizExercise) {
            return;
        }
        const quizQuestions = quizExercise.quizQuestions ?? [];
        // reset old data
        this.label = [];
        this.backgroundColor = [];
        this.ratedData = [];
        this.unratedData = [];
        this.ratedAverage = 0;
        this.unratedAverage = 0;

        // set data based on the CorrectCounters in the QuestionStatistics
        for (let i = 0; i < quizQuestions.length; i++) {
            const question = quizQuestions[i];
            const statistic = question.quizQuestionStatistic;
            const ratedCounter = statistic?.ratedCorrectCounter ?? 0;
            const unratedCounter = statistic?.unRatedCorrectCounter ?? 0;
            this.label.push(i + 1 + '.');
            this.backgroundColor.push('#5bc0de');
            this.ratedData.push(ratedCounter);
            this.unratedData.push(unratedCounter);
            this.ratedAverage = this.ratedAverage + ratedCounter * question.points!;
            this.unratedAverage = this.unratedAverage + unratedCounter * question.points!;
        }

        // set Background for invalid questions = grey
        for (let i = 0; i < quizQuestions.length; i++) {
            if (quizQuestions[i].invalid) {
                this.backgroundColor[i] = '#949494';
            }
        }

        // add data for the last bar (Average)
        this.backgroundColor.push('#1e3368');
        /*
         * we do not use roundScoreSpecifiedByCourseSettings() here as it is not necessary to make the rounding of the average correct solutions
         * in a quiz dependent of the individual course settings
         */
        this.ratedData.push(round(this.ratedAverage / this.maxScore, 2));
        this.unratedData.push(round(this.unratedAverage / this.maxScore, 2));

        // add Text for last label based on the language
        const lastLabel = this.translateService.instant('artemisApp.showStatistic.quizStatistic.average');
        this.label.push(lastLabel);
        this.chartLabels = this.label;
        this.chartColors.set([...this.backgroundColor]);

        // load data into chart
        this.loadDataInDiagram();
    }

    /**
     * updates the chart by setting the data set and re-calculating the height
     */
    loadDataInDiagram(): void {
        const quizExercise = this.quizExercise();
        if (!quizExercise) {
            return;
        }
        this.setData({ participantsRated: quizExercise.participantsRated, participantsUnrated: quizExercise.participantsUnrated });
        this.updateChartData();
        this.setAxisLabels('artemisApp.showStatistic.quizStatistic.xAxes', 'artemisApp.showStatistic.quizStatistic.yAxes');
    }

    protected override formatTooltipLabel(item: TooltipItem<'bar'>): string {
        // the last bar aggregates the average across all questions rather than a single question
        const isAverageBar = item.dataIndex === this.data.length - 1;
        const key = isAverageBar ? 'artemisApp.showStatistic.tooltip.average' : 'artemisApp.showStatistic.tooltip.correctSolutions';
        return this.tooltipLine(key, item.parsed.y ?? 0);
    }
}
