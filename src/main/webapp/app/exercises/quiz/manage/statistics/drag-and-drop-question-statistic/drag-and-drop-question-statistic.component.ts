import { ChangeDetectorRef, Component, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { QuizStatisticUtil } from 'app/exercises/quiz/shared/quiz-statistic-util.service';
import { DragAndDropQuestionUtil } from 'app/exercises/quiz/shared/drag-and-drop-question-util.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { DragAndDropQuestionStatistic } from 'app/entities/quiz/drag-and-drop-question-statistic.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuestionStatisticComponent, blueColor, greenColor } from 'app/exercises/quiz/manage/statistics/question-statistic.component';
import { faCheckCircle, faSync, faTimesCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-drag-and-drop-question-statistic',
    templateUrl: './drag-and-drop-question-statistic.component.html',
    providers: [QuizStatisticUtil, DragAndDropQuestionUtil],
    styleUrls: [
        '../../../../../shared/chart/vertical-bar-chart.scss',
        '../quiz-point-statistic/quiz-point-statistic.component.scss',
        './drag-and-drop-question-statistic.component.scss',
    ],
    encapsulation: ViewEncapsulation.None,
})
export class DragAndDropQuestionStatisticComponent extends QuestionStatisticComponent {
    question: DragAndDropQuestion;

    // Icons
    faSync = faSync;
    faCheckCircle = faCheckCircle;
    faTimesCircle = faTimesCircle;

    constructor(
        route: ActivatedRoute,
        router: Router,
        accountService: AccountService,
        translateService: TranslateService,
        quizExerciseService: QuizExerciseService,
        jhiWebsocketService: JhiWebsocketService,
        quizStatisticUtil: QuizStatisticUtil,
        private dragAndDropQuestionUtil: DragAndDropQuestionUtil,
        private artemisMarkdown: ArtemisMarkdownService,
        protected changeDetector: ChangeDetectorRef,
    ) {
        super(route, router, accountService, translateService, quizExerciseService, jhiWebsocketService, changeDetector);
    }

    /**
     * load the Quiz, which is necessary to build the Web-Template
     *
     * @param {QuizExercise} quiz: the quizExercise, which the selected question is part of.
     * @param {boolean} refresh: true if method is called from Websocket
     */
    loadQuiz(quiz: QuizExercise, refresh: boolean) {
        const updatedQuestion = super.loadQuizCommon(quiz);
        if (!updatedQuestion) {
            return;
        }
        // load Layout only at the opening (not if the websocket refreshed the data)
        if (!refresh) {
            this.questionTextRendered = this.artemisMarkdown.safeHtmlForMarkdown(this.question.text);
            this.loadLayout();
        }
        this.loadData();
    }

    /**
     * build the Chart-Layout based on the Json-entity (questionStatistic)
     */
    loadLayout() {
        this.orderDropLocationByPos();
        this.resetLabelsColors();

        // set label and background color based on the dropLocations
        this.question.dropLocations!.forEach((dropLocation, i) => {
            this.labels.push(this.getLetter(i) + '.');
            this.solutionLabels.push(this.getLetter(i) + '.');
            this.backgroundColors.push(blueColor);
            this.backgroundSolutionColors.push(greenColor);
        });

        this.addLastBarLayout(this.question.dropLocations!.length);
        this.loadInvalidLayout(this.question.dropLocations!);
    }

    /**
     * load the Data from the Json-entity to the chart: myChart
     */
    loadData() {
        this.resetData();

        // set data based on the dropLocations for each dropLocation
        this.question.dropLocations!.forEach((dropLocation) => {
            const dropLocationCounter = (this.questionStatistic as DragAndDropQuestionStatistic).dropLocationCounters?.find(
                (dlCounter) => dropLocation.id === dlCounter.dropLocation!.id,
            )!;
            this.addData(dropLocationCounter.ratedCounter!, dropLocationCounter.unRatedCounter!);
        });
        this.updateData();
    }

    /**
     * order DropLocations by Position
     */
    orderDropLocationByPos() {
        let change = true;
        while (change) {
            change = false;
            if (this.question.dropLocations && this.question.dropLocations.length > 0) {
                for (let i = 0; i < this.question.dropLocations.length - 1; i++) {
                    if (this.question.dropLocations[i].posX! > this.question.dropLocations[i + 1].posX!) {
                        // switch DropLocations
                        const temp = this.question.dropLocations[i];
                        this.question.dropLocations[i] = this.question.dropLocations[i + 1];
                        this.question.dropLocations[i + 1] = temp;
                        change = true;
                    }
                }
            }
        }
    }

    /**
     * Get the drag item that was mapped to the given drop location in the sample solution
     *
     * @param dropLocation {object} the drop location that the drag item should be mapped to
     * @return {DragItem | undefined} the mapped drag item, or undefined if no drag item has been mapped to this location
     */
    correctDragItemForDropLocation(dropLocation: DropLocation) {
        const currMapping = this.dragAndDropQuestionUtil.solve(this.question, undefined).filter((mapping) => mapping.dropLocation!.id === dropLocation.id)[0];
        return currMapping.dragItem;
    }
}
