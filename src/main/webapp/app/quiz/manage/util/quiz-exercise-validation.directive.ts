import { ChangeDetectorRef, Directive, inject } from '@angular/core';
import { QuizExercise, QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { ValidationReason } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import { MAX_QUIZ_QUESTION_LENGTH_THRESHOLD } from 'app/shared/constants/input.constants';
import { InvalidFlaggedQuestions, checkForInvalidFlaggedQuestions, computeQuizQuestionInvalidReason, isQuizQuestionValid } from 'app/quiz/shared/service/quiz-manage-util.service';
import { DragAndDropQuestionUtil } from 'app/quiz/shared/service/drag-and-drop-question-util.service';
import { ShortAnswerQuestionUtil } from 'app/quiz/shared/service/short-answer-question-util.service';
import dayjs from 'dayjs/esm';
import QuizExerciseEditorDTO from 'app/quiz/shared/entities/quiz-exercise-editor.dto';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';

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
        // Create a diff object and check if it is empty
        const diffObject = this.createDiffObject();
        if (this.isEmpty(diffObject)) {
            return false;
        }
        return true;
    }

    createDiffObject(): QuizExerciseEditorDTO {
        const changedFields: QuizExerciseEditorDTO = {};
        if (this.quizExercise.title !== this.savedEntity.title) {
            changedFields.title = this.quizExercise.title;
        }
        if (this.quizExercise.channelName !== this.savedEntity.channelName) {
            changedFields.channelName = this.quizExercise.channelName;
        }
        if (this.quizExercise.categories !== this.savedEntity.categories) {
            if (this.quizExercise.categories === undefined || this.quizExercise.categories.length === 0) {
                changedFields.categories = undefined;
            } else {
                changedFields.categories = this.quizExercise.categories;
            }
        }
        if (this.quizExercise.difficulty !== this.savedEntity.difficulty) {
            changedFields.difficulty = this.quizExercise.difficulty;
        }
        if (this.quizExercise.duration !== this.savedEntity.duration) {
            changedFields.duration = this.quizExercise.duration;
        }
        if (this.quizExercise.randomizeQuestionOrder !== this.savedEntity.randomizeQuestionOrder) {
            changedFields.randomizeQuestionOrder = this.quizExercise.randomizeQuestionOrder;
        }
        if (this.quizExercise.quizMode !== this.savedEntity.quizMode) {
            changedFields.quizMode = this.quizExercise.quizMode;
        }
        if (!dayjs(this.quizExercise.releaseDate).isSame(this.savedEntity.releaseDate)) {
            changedFields.releaseDate = this.quizExercise.releaseDate?.toISOString();
        }
        if (!dayjs(this.quizExercise.dueDate).isSame(this.savedEntity.dueDate)) {
            changedFields.dueDate = this.quizExercise.dueDate?.toISOString();
        }
        if (this.quizExercise.includedInOverallScore !== this.savedEntity.includedInOverallScore) {
            changedFields.includedInOverallScore = this.quizExercise.includedInOverallScore;
        }

        if (this.quizQuestionsChanged()) {
            changedFields.quizQuestions = this.quizExercise.quizQuestions;
        }
        return changedFields;
    }

    /**
     * Compare quiz questions while ignoring specific attributes
     */
    private quizQuestionsChanged(): boolean {
        const currentQuestions = this.quizExercise.quizQuestions ?? [];
        const savedQuestions = this.savedEntity.quizQuestions ?? [];

        // Quick length check first
        if (currentQuestions.length !== savedQuestions.length) {
            return true;
        }

        // Compare each question
        for (let i = 0; i < currentQuestions.length; i++) {
            const current = currentQuestions[i];
            const saved = savedQuestions[i];

            if (current.type !== saved.type) {
                return true;
            }

            // For ShortAnswerQuestions, compare only meaningful properties
            if (current.type === QuizQuestionType.SHORT_ANSWER) {
                const currentSA = current as ShortAnswerQuestion;
                const savedSA = saved as ShortAnswerQuestion;

                // Compare id, title, text, points, scoringType, randomizeOrder, invalid, quizQuestionStatistic, similarityValue and matchLetterCase
                if (
                    currentSA.id !== savedSA.id ||
                    currentSA.title !== savedSA.title ||
                    currentSA.text !== savedSA.text ||
                    currentSA.points !== savedSA.points ||
                    currentSA.scoringType !== savedSA.scoringType ||
                    currentSA.randomizeOrder !== savedSA.randomizeOrder ||
                    currentSA.invalid !== savedSA.invalid ||
                    currentSA.similarityValue !== savedSA.similarityValue ||
                    currentSA.matchLetterCase !== savedSA.matchLetterCase
                ) {
                    return true;
                }

                // Compare spots (ignore tempID)
                if (!this.compareArraysIgnoringAttributes(currentSA.spots, savedSA.spots, ['tempID'])) {
                    return true;
                }

                // Compare solutions (ignore tempID)
                if (!this.compareArraysIgnoringAttributes(currentSA.solutions, savedSA.solutions, ['tempID'])) {
                    return true;
                }

                // Compare correctMappings (ignore several attributes)
                if (
                    !this.compareArraysIgnoringAttributes(currentSA.correctMappings, savedSA.correctMappings, ['id', 'tempID', 'shortAnswerSpotIndex', 'shortAnswerSolutionIndex'])
                ) {
                    return true;
                }
            } else {
                // For other question types, just compare the stringified objects
                if (JSON.stringify(current) !== JSON.stringify(saved)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Compare two arrays while ignoring specific attributes
     */
    private compareArraysIgnoringAttributes(arr1: any[] | undefined, arr2: any[] | undefined, ignoredAttributes: string[]): boolean {
        if (!arr1 && !arr2) return true;
        if (!arr1 || !arr2) return false;

        for (let i = 0; i < arr1.length; i++) {
            //Create deep copies of the objects to avoid modifying the original ones
            const obj1 = JSON.parse(JSON.stringify(arr1[i]));
            const obj2 = JSON.parse(JSON.stringify(arr2[i]));

            // Remove ignored attributes
            for (const attr of ignoredAttributes) {
                delete obj1[attr];
                delete obj2[attr];

                // Also clean nested objects (like spot and solution in mappings)
                if (obj1.spot) delete obj1.spot[attr];
                if (obj2.spot) delete obj2.spot[attr];
                if (obj1.solution) delete obj1.solution[attr];
                if (obj2.solution) delete obj2.solution[attr];
            }
            // Remove all undefined attributes
            for (const key in obj1) {
                if (obj1[key] === undefined) {
                    delete obj1[key];
                }
            }
            for (const key in obj2) {
                if (obj2[key] === undefined) {
                    delete obj2[key];
                }
            }

            //Compare remaining attributes by looping over them
            if (Object.keys(obj1).length !== Object.keys(obj2).length) {
                return false;
            }
            for (const key in obj1) {
                if (obj1.hasOwnProperty(key) && obj2.hasOwnProperty(key)) {
                    // Check if the values are objects and compare them recursively
                    if (typeof obj1[key] === 'object' && typeof obj2[key] === 'object') {
                        if (!this.compareArraysIgnoringAttributes([obj1[key]], [obj2[key]], ignoredAttributes)) {
                            return false;
                        }
                    } else if (Array.isArray(obj1[key]) && Array.isArray(obj2[key])) {
                        if (!this.compareArraysIgnoringAttributes(obj1[key], obj2[key], ignoredAttributes)) {
                            return false;
                        }
                    } else {
                        // Compare primitive values
                        if (obj1[key] !== obj2[key]) {
                            return false;
                        }
                    }
                } else {
                    return false;
                }
            }
        }

        return true;
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
