import { Component, OnChanges, OnDestroy, OnInit, SimpleChanges, ChangeDetectorRef, ViewEncapsulation } from '@angular/core';
import { Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { QuizReEvaluateWarningComponent } from './quiz-re-evaluate-warning.component';
import { DragAndDropQuestionUtil } from 'app/exercises/quiz/shared/drag-and-drop-question-util.service';
import { HttpResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizExercisePopupService } from 'app/exercises/quiz/manage/quiz-exercise-popup.service';
import { Duration } from 'app/exercises/quiz/manage/quiz-exercise-interfaces';
import { cloneDeep } from 'lodash-es';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { QuizExerciseValidationDirective } from 'app/exercises/quiz/manage/quiz-exercise-validation.directive';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { faExclamationCircle, faExclamationTriangle, faUndo } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-quiz-re-evaluate',
    templateUrl: './quiz-re-evaluate.component.html',
    styleUrls: ['./quiz-re-evaluate.component.scss', '../../shared/quiz.scss'],
    encapsulation: ViewEncapsulation.None,
    providers: [DragAndDropQuestionUtil, ShortAnswerQuestionUtil],
})
export class QuizReEvaluateComponent extends QuizExerciseValidationDirective implements OnInit, OnChanges, OnDestroy {
    private subscription: Subscription;

    modalService: NgbModal;
    popupService: QuizExercisePopupService;

    isSaving: boolean;
    duration: Duration;

    // Icons
    faUndo = faUndo;
    faExclamationCircle = faExclamationCircle;
    faExclamationTriangle = faExclamationTriangle;

    constructor(
        private quizExerciseService: QuizExerciseService,
        private route: ActivatedRoute,
        private dragAndDropQuestionUtil: DragAndDropQuestionUtil,
        private shortAnswerQuestionUtil: ShortAnswerQuestionUtil,
        private modalServiceC: NgbModal,
        private quizExercisePopupService: QuizExercisePopupService,
        public changeDetector: ChangeDetectorRef,
        private navigationUtilService: ArtemisNavigationUtilService,
    ) {
        super();
    }

    ngOnInit(): void {
        this.subscription = this.route.params.subscribe((params) => {
            this.quizExerciseService.find(params['exerciseId']).subscribe((response: HttpResponse<QuizExercise>) => {
                this.quizExercise = response.body!;
                this.prepareEntity(this.quizExercise);
                this.savedEntity = cloneDeep(this.quizExercise);
            });
        });
        this.quizIsValid = true;
        this.modalService = this.modalServiceC;
        this.popupService = this.quizExercisePopupService;

        /** Initialize constants **/
        this.isSaving = false;
        this.duration = new Duration(0, 0);
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.quizExercise && changes.quizExercise.currentValue !== null) {
            this.prepareEntity(this.quizExercise);
            this.savedEntity = cloneDeep(this.quizExercise);
            this.cacheValidation(this.changeDetector);
        }
    }

    /**
     * @function deleteQuestion
     * @desc Remove question from the quiz
     * @param questionToBeDeleted {QuizQuestion} the question to remove
     */
    deleteQuestion(questionToBeDeleted: QuizQuestion): void {
        this.quizExercise.quizQuestions = this.quizExercise.quizQuestions?.filter((question) => question !== questionToBeDeleted);
        this.cacheValidation(this.changeDetector);
    }

    /**
     * @function onQuestionUpdated
     * @desc Handles the change of a question by replacing the array with a copy
     *                                      (allows for shallow comparison)
     */
    onQuestionUpdated(): void {
        this.cacheValidation(this.changeDetector);
        this.quizExercise.quizQuestions = Array.from(this.quizExercise.quizQuestions!);
    }

    /**
     * @function save
     * @desc Open Warning-Modal
     *  -> if confirmed: send changed quiz to server (in Modal-controller)
     *                              and go back to parent-template
     *  -> if canceled: close Modal
     */
    save(): void {
        this.popupService.open(QuizReEvaluateWarningComponent as Component, this.quizExercise).then((res) => {
            res.result.then(() => {
                this.savedEntity = cloneDeep(this.quizExercise);
            });
        });
    }

    /**
     * Return to the exercise overview page
     */
    back(): void {
        this.navigationUtilService.navigateBackFromExerciseUpdate(this.quizExercise);
    }

    /**
     * @function prepareEntity
     * @desc Makes sure the quizExercise is well-formed and its fields are of the correct types
     * @param quizExercise
     */
    prepareEntity(quizExercise: QuizExercise) {
        quizExercise.releaseDate = quizExercise.releaseDate ? quizExercise.releaseDate : dayjs();
        quizExercise.duration = Number(quizExercise.duration);
        quizExercise.duration = isNaN(quizExercise.duration) ? 10 : quizExercise.duration;
    }

    /**
     * @function durationString
     * @desc Gives the duration time in a string with this format: <minutes>:<seconds>
     * @returns {string} the duration as string
     */
    durationString(): string {
        if (this.duration.seconds! <= 0) {
            return this.duration.minutes + ':00';
        }
        if (this.duration.seconds! < 10) {
            return this.duration.minutes + ':0' + this.duration.seconds;
        }
        return this.duration.minutes + ':' + this.duration.seconds;
    }

    /**
     * @function resetAll
     * @desc Resets the whole Quiz
     */
    resetAll(): void {
        this.quizExercise = cloneDeep(this.savedEntity);
    }

    /**
     * @function resetQuizTitle
     * @desc Resets the quiz title
     */
    resetQuizTitle() {
        this.quizExercise.title = this.savedEntity.title;
    }

    /**
     * @function moveUp
     * @desc Move the question one position up
     * @param question {QuizQuestion} the question to move
     */
    moveUp(question: QuizQuestion): void {
        const index = this.quizExercise.quizQuestions!.indexOf(question);
        if (index === 0) {
            return;
        }
        const questionToMove: QuizQuestion = Object.assign({}, this.quizExercise.quizQuestions![index]);
        /**
         * The splice() method adds/removes items to/from an array, and returns the removed item(s).
         * We create a copy of the question we want to move and remove it from the questions array.
         * Then we reinsert it at index - 1 => move up by 1 position
         */
        this.quizExercise.quizQuestions!.splice(index, 1);
        this.quizExercise.quizQuestions!.splice(index - 1, 0, questionToMove);
    }

    /**
     * @function moveDown
     * @desc Move the question one position down
     * @param question {QuizQuestion} the question to move
     */
    moveDown(question: QuizQuestion): void {
        const index = this.quizExercise.quizQuestions!.indexOf(question);
        if (index === this.quizExercise.quizQuestions!.length - 1) {
            return;
        }
        const questionToMove: QuizQuestion = Object.assign({}, this.quizExercise.quizQuestions![index]);
        /**
         * The splice() method adds/removes items to/from an array, and returns the removed item(s).
         * We create a copy of the question we want to move and remove it from the questions array.
         * Then we reinsert it at index + 1 => move down by 1 position
         */
        this.quizExercise.quizQuestions!.splice(index, 1);
        this.quizExercise.quizQuestions!.splice(index + 1, 0, questionToMove);
    }

    ngOnDestroy(): void {
        if (this.subscription) {
            this.subscription.unsubscribe();
        }
    }

    /**
     * handles how the exercise is calculated into the course/ exam score
     */
    includedInOverallScoreChange(includedInOverallScore: IncludedInOverallScore) {
        this.quizExercise.includedInOverallScore = includedInOverallScore;
        this.cacheValidation(this.changeDetector);
    }
}
