import { ChangeDetectorRef, Component, OnChanges, OnDestroy, OnInit, SimpleChanges, ViewEncapsulation, inject, viewChildren } from '@angular/core';
import { IncludedInOverallScorePickerComponent } from 'app/exercise/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { QuizReEvaluateWarningComponent } from './warning/quiz-re-evaluate-warning.component';
import { DragAndDropQuestionUtil } from 'app/quiz/shared/service/drag-and-drop-question-util.service';
import { HttpResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExercisePopupService } from 'app/quiz/manage/service/quiz-exercise-popup.service';
import { Duration } from 'app/quiz/manage/interfaces/quiz-exercise-interfaces';
import { cloneDeep } from 'lodash-es';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizExerciseValidationDirective } from 'app/quiz/manage/util/quiz-exercise-validation.directive';
import { ShortAnswerQuestionUtil } from 'app/quiz/shared/service/short-answer-question-util.service';
import { faExclamationCircle, faExclamationTriangle, faUndo } from '@fortawesome/free-solid-svg-icons';
import { ReEvaluateDragAndDropQuestionComponent } from 'app/quiz/manage/re-evaluate/drag-and-drop-question/re-evaluate-drag-and-drop-question.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ReEvaluateShortAnswerQuestionComponent } from './short-answer-question/re-evaluate-short-answer-question.component';
import { JsonPipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ReEvaluateMultipleChoiceQuestionComponent } from 'app/quiz/manage/re-evaluate/multiple-choice-question/re-evaluate-multiple-choice-question.component';

@Component({
    selector: 'jhi-quiz-re-evaluate',
    templateUrl: './quiz-re-evaluate.component.html',
    styleUrls: ['./quiz-re-evaluate.component.scss', '../../shared/quiz.scss'],
    encapsulation: ViewEncapsulation.None,
    providers: [DragAndDropQuestionUtil, ShortAnswerQuestionUtil],
    imports: [
        TranslateDirective,
        FaIconComponent,
        FormsModule,
        NgbTooltip,
        FormDateTimePickerComponent,
        IncludedInOverallScorePickerComponent,
        ReEvaluateDragAndDropQuestionComponent,
        ReEvaluateShortAnswerQuestionComponent,
        JsonPipe,
        ArtemisTranslatePipe,
        ReEvaluateMultipleChoiceQuestionComponent,
    ],
})
export class QuizReEvaluateComponent extends QuizExerciseValidationDirective implements OnInit, OnChanges, OnDestroy {
    private quizExerciseService = inject(QuizExerciseService);
    private route = inject(ActivatedRoute);
    private modalServiceC = inject(NgbModal);
    private quizExercisePopupService = inject(QuizExercisePopupService);
    private changeDetector = inject(ChangeDetectorRef);
    private navigationUtilService = inject(ArtemisNavigationUtilService);

    private subscription: Subscription;

    readonly reEvaluateDragAndDropQuestionComponents = viewChildren(ReEvaluateDragAndDropQuestionComponent);

    modalService: NgbModal;
    popupService: QuizExercisePopupService;

    isSaving: boolean;
    duration: Duration;

    // Icons
    faUndo = faUndo;
    faExclamationCircle = faExclamationCircle;
    faExclamationTriangle = faExclamationTriangle;

    ngOnInit(): void {
        this.subscription = this.route.params.subscribe((params) => {
            this.quizExerciseService.find(params['exerciseId']).subscribe((response: HttpResponse<QuizExercise>) => {
                this.quizExercise = response.body!;
                this.prepareEntity(this.quizExercise);
                this.savedEntity = cloneDeep(this.quizExercise);
                this.updateDuration();
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
        const files = new Map<string, File>();
        for (const component of this.reEvaluateDragAndDropQuestionComponents()) {
            component.fileMap.forEach((value, filename) => {
                files.set(filename, value.file);
            });
        }
        this.popupService.open(QuizReEvaluateWarningComponent as Component, this.quizExercise, files).then((res) => {
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

    /**
     @function updateDuration
     @desc Set duration according quiz exercise duration
    */
    updateDuration(): void {
        const duration = dayjs.duration(this.quizExercise.duration ?? 0, 'seconds');
        this.duration.minutes = 60 * duration.hours() + duration.minutes();
        this.duration.seconds = duration.seconds();
    }
}
