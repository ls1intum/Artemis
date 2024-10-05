import { ChangeDetectorRef, Directive, inject } from '@angular/core';
import { QuizExercise, QuizMode } from 'app/entities/quiz/quiz-exercise.model';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { ValidationReason } from 'app/entities/exercise.model';
import { ButtonType } from 'app/shared/components/button.component';
import { MAX_QUIZ_QUESTION_LENGTH_THRESHOLD } from 'app/shared/constants/input.constants';
import {
    InvalidFlaggedQuestions,
    checkForInvalidFlaggedQuestions,
    computeQuizQuestionInvalidReason,
    isQuizQuestionValid,
} from 'app/exercises/quiz/shared/quiz-manage-util.service';
import { DragAndDropQuestionUtil } from 'app/exercises/quiz/shared/drag-and-drop-question-util.service';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';

@Directive()
export abstract class QuizExerciseValidationDirective {
    protected dragAndDropQuestionUtil = inject(DragAndDropQuestionUtil);
    protected shortAnswerQuestionUtil = inject(ShortAnswerQuestionUtil);

    // Make constants available to html for comparison
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;
    readonly QuizMode = QuizMode;
    readonly ButtonType = ButtonType;

    warningQuizCache = false;
    quizIsValid: boolean;
    quizExercise: QuizExercise;

    savedEntity: QuizExercise;
    isExamMode: boolean;
    isImport: boolean;

    invalidReasons: ValidationReason[];
    invalidWarnings: ValidationReason[];

    protected invalidFlaggedQuestions: InvalidFlaggedQuestions = {};
    pendingChangesCache: boolean;

    /**
     * 1. Check whether the inputs in the quiz are valid
     * 2. Check if warning are needed for the inputs
     * 3. Display the warnings/invalid reasons in the html file if needed
     */
    cacheValidation(changeDetector: ChangeDetectorRef): void {
        this.warningQuizCache = this.computeInvalidWarnings().length > 0;
        this.quizIsValid = this.isValidQuiz();
        this.pendingChangesCache = this.pendingChanges();
        this.checkForInvalidFlaggedQuestions();
        this.invalidReasons = this.computeInvalidReasons();
        this.invalidWarnings = this.computeInvalidWarnings();
        changeDetector.detectChanges();
    }

    isValidQuiz(): boolean {
        if (!this.quizExercise) {
            return false;
        }

        const isGenerallyValid =
            this.quizExercise.title != undefined &&
            this.quizExercise.title !== '' &&
            this.quizExercise.title.length < MAX_QUIZ_QUESTION_LENGTH_THRESHOLD &&
            this.quizExercise.duration !== 0 &&
            this.quizExercise.quizQuestions != undefined &&
            !!this.quizExercise.quizQuestions.length;

        const areAllQuestionsValid = this.quizExercise.quizQuestions?.every(function (question) {
            return isQuizQuestionValid(question, this.dragAndDropQuestionUtil, this.shortAnswerQuestionUtil);
        }, this);
        const maxPointsReachableInQuiz = this.quizExercise.quizQuestions?.map((quizQuestion) => quizQuestion.points ?? 0).reduce((a, b) => a + b, 0);

        return (
            isGenerallyValid &&
            areAllQuestionsValid === true &&
            maxPointsReachableInQuiz !== undefined &&
            maxPointsReachableInQuiz > 0 &&
            !this.testRunExistsAndShouldNotBeIgnored()
        );
    }

    /**
     * Checks if the test runs for the quiz exist and should be ignored
     * @returns {boolean} true if a test run exists for the quiz and should not be ignored
     */
    testRunExistsAndShouldNotBeIgnored(): boolean {
        return !this.isImport && this.isExamMode && !!this.quizExercise.testRunParticipationsExist;
    }

    /**
     * Get the reasons, why the quiz needs warnings
     * @returns {Array} array of objects with fields 'translateKey' and 'translateValues'
     */
    computeInvalidWarnings(): ValidationReason[] {
        const invalidWarnings = !this.quizExercise
            ? []
            : this.quizExercise.quizQuestions
                  ?.map((question, index) => {
                      if (question.type === QuizQuestionType.MULTIPLE_CHOICE && (<MultipleChoiceQuestion>question).answerOptions!.some((option) => !option.explanation)) {
                          return {
                              translateKey: 'artemisApp.quizExercise.invalidReasons.explanationIsMissing',
                              translateValues: { index: index + 1 },
                          };
                      }
                  })
                  .filter(Boolean);

        return invalidWarnings as ValidationReason[];
    }

    /**
     * Get the reasons, why the quiz is invalid
     * @returns {Array} array of objects with fields 'translateKey' and 'translateValues'
     */
    computeInvalidReasons(): ValidationReason[] {
        const invalidReasons = new Array<ValidationReason>();
        if (!this.quizExercise) {
            return [];
        }

        if (!this.quizExercise.title || this.quizExercise.title === '') {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.quizTitle',
                translateValues: {},
            });
        }
        if (this.quizExercise.title!.length >= MAX_QUIZ_QUESTION_LENGTH_THRESHOLD) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.quizTitleLength',
                translateValues: { threshold: MAX_QUIZ_QUESTION_LENGTH_THRESHOLD },
            });
        }
        if (!this.quizExercise.duration) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.quizDuration',
                translateValues: {},
            });
        }
        if (!this.quizExercise.quizQuestions || this.quizExercise.quizQuestions.length === 0) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.noQuestion',
                translateValues: {},
            });
        }
        if (this.testRunExistsAndShouldNotBeIgnored()) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.edit.testRunSubmissionsExist',
                translateValues: {},
            });
        }

        // TODO: quiz cleanup: properly validate start (and due) date and deduplicate the checks (see isValidQuiz)
        /** We only verify the releaseDate if the checkbox is activated **/
        // if (this.quizExercise.isPlannedToStart) {
        //     if (!this.quizExercise.releaseDate || !dayjs(this.quizExercise.releaseDate).isValid()) {
        //         invalidReasons.push({
        //             translateKey: 'artemisApp.quizExercise.invalidReasons.invalidStartTime',
        //             translateValues: {},
        //         });
        //     }
        // }
        this.quizExercise.quizQuestions!.forEach(function (question: QuizQuestion, index: number) {
            computeQuizQuestionInvalidReason(invalidReasons, question, index, this.dragAndDropQuestionUtil, this.shortAnswerQuestionUtil);
        }, this);
        const invalidFlaggedReasons = !this.quizExercise
            ? []
            : this.quizExercise.quizQuestions
                  ?.map((question, index) => {
                      if (this.invalidFlaggedQuestions[question.id!]) {
                          return {
                              translateKey: 'artemisApp.quizExercise.invalidReasons.questionHasInvalidFlaggedElements',
                              translateValues: { index: index + 1 },
                          };
                      }
                  })
                  .filter(Boolean);

        return invalidReasons.concat(invalidFlaggedReasons as ValidationReason[]);
    }

    /**
     * @function pendingChanges
     * @desc Determine if there are any changes waiting to be saved
     * @returns {boolean} true if there are any pending changes, false otherwise
     */
    pendingChanges(): boolean {
        if (!this.quizExercise || !this.savedEntity) {
            return false;
        }
        return JSON.stringify(this.quizExercise) !== JSON.stringify(this.savedEntity);
    }

    checkForInvalidFlaggedQuestions() {
        if (!this.quizExercise) {
            return;
        }
        this.invalidFlaggedQuestions = checkForInvalidFlaggedQuestions(this.quizExercise.quizQuestions ?? []);
    }

    /**
     * check if Dictionary is empty
     * @param obj the dictionary to be checked
     */
    protected isEmpty(obj: any) {
        return Object.keys(obj).length === 0;
    }
}
