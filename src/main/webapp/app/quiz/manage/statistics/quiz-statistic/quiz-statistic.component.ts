import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { AbstractQuizStatisticComponent } from 'app/quiz/manage/statistics/quiz-statistics';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { calculateMaxScore } from 'app/quiz/manage/statistics/quiz-statistic/quiz-statistics.utils';
import { Subscription } from 'rxjs';
import { round } from 'app/foundation/util/utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ChartModule } from 'primeng/chart';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { QuizStatisticsFooterComponent } from '../quiz-statistics-footer/quiz-statistics-footer.component';

@Component({
    selector: 'jhi-quiz-statistic',
    templateUrl: './quiz-statistic.component.html',
    styleUrls: ['../quiz-point-statistic/quiz-point-statistic.component.scss'],
    imports: [TranslateDirective, ChartModule, FaIconComponent, QuizStatisticsFooterComponent],
})
export class QuizStatisticComponent extends AbstractQuizStatisticComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private accountService = inject(AccountService);
    private quizExerciseService = inject(QuizExerciseService);
    private websocketService = inject(WebsocketService);

    quizExercise: QuizExercise;

    label: string[] = [];
    backgroundColor: string[] = [];
    ratedAverage: number;
    unratedAverage: number;

    maxScore: number;
    websocketChannelForData: string;
    private websocketSubscription?: Subscription;

    // Icons
    faSync = faSync;

    ngOnInit() {
        this.translateService.onLangChange.subscribe(() => {
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
        this.route.params.subscribe((params) => {
            // use different REST-call if the User is a Student
            if (this.accountService.isAtLeastTutor()) {
                this.quizExerciseService.find(params['exerciseId']).subscribe((res: HttpResponse<QuizExercise>) => {
                    this.loadQuizSuccess(res.body!);
                });
            }

            // subscribe websocket for new statistical data
            this.websocketChannelForData = '/topic/statistic/' + params['exerciseId'];

            // ask for new Data if the websocket for new statistical data was notified
            this.websocketSubscription = this.websocketService.subscribe<QuizExercise>(this.websocketChannelForData).subscribe(() => {
                if (this.accountService.isAtLeastTutor()) {
                    this.quizExerciseService.find(params['exerciseId']).subscribe((res) => {
                        this.loadQuizSuccess(res.body!);
                    });
                }
            });
        });
    }

    ngOnDestroy() {
        this.websocketSubscription?.unsubscribe();
    }

    /**
     * This functions loads the Quiz, which is necessary to build the Web-Template
     * And it loads the new Data if the Websocket has been notified
     *
     * @param quiz the quizExercise, which this quiz-statistic presents.
     */
    loadQuizSuccess(quiz: QuizExercise) {
        // if the Student finds a way to the Website -> the Student will be sent back to Courses
        if (!this.accountService.isAtLeastTutor()) {
            this.router.navigate(['/courses']);
        }
        this.quizExercise = quiz;
        this.maxScore = calculateMaxScore(this.quizExercise);
        this.loadData();
    }

    /**
     * load the Data from the Json-entity to the chart: myChart
     */
    loadData() {
        // reset old data
        this.label = [];
        this.backgroundColor = [];
        this.ratedData = [];
        this.unratedData = [];
        this.ratedAverage = 0;
        this.unratedAverage = 0;

        // set data based on the CorrectCounters in the QuestionStatistics
        for (let i = 0; i < this.quizExercise.quizQuestions!.length; i++) {
            const question = this.quizExercise.quizQuestions![i];
            const statistic = question.quizQuestionStatistic!;
            const ratedCounter = statistic.ratedCorrectCounter!;
            const unratedCounter = statistic.unRatedCorrectCounter!;
            this.label.push(i + 1 + '.');
            this.backgroundColor.push('#5bc0de');
            this.ratedData.push(ratedCounter);
            this.unratedData.push(unratedCounter);
            this.ratedAverage = this.ratedAverage + ratedCounter * question.points!;
            this.unratedAverage = this.unratedAverage + unratedCounter * question.points!;
        }

        // set Background for invalid questions = grey
        for (let i = 0; i < this.quizExercise.quizQuestions!.length; i++) {
            if (this.quizExercise.quizQuestions![i].invalid) {
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
        this.setData(this.quizExercise.quizPointStatistic!);
        this.updateChartData();
        this.setAxisLabels('artemisApp.showStatistic.quizStatistic.xAxes', 'artemisApp.showStatistic.quizStatistic.yAxes');
    }
}
