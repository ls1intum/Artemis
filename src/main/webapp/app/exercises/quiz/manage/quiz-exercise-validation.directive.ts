import { ChangeDetectorRef, Directive } from '@angular/core';
import { QuizExercise, QuizMode } from 'app/entities/quiz/quiz-exercise.model';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { CanBecomeInvalid, DropLocation } from 'app/entities/quiz/drop-location.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { captureException } from '@sentry/browser';
import { ValidationReason } from 'app/entities/exercise.model';
import { ButtonType } from 'app/shared/components/button.component';

type InvalidFlaggedQuestions = {
    [title: string]: (AnswerOption | ShortAnswerSolution | ShortAnswerMapping | ShortAnswerSpot | DropLocation | DragItem | DragAndDropMapping)[] | undefined;
};

@Directive()
export abstract class QuizExerciseValidationDirective {
    // Make constants available to html for comparison
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;
    readonly QuizMode = QuizMode;
    readonly ButtonType = ButtonType;

    readonly maxLengthThreshold = 250;
    readonly explanationLengthThreshold = 500;
    readonly hintLengthThreshold = 255;

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
            this.quizExercise.title.length < this.maxLengthThreshold &&
            this.quizExercise.duration !== 0 &&
            this.quizExercise.quizQuestions != undefined &&
            !!this.quizExercise.quizQuestions.length;

        const areAllQuestionsValid = this.quizExercise.quizQuestions?.every(function (question) {
            if (question.points == undefined || question.points < 1) {
                return false;
            }
            if (question.explanation && question.explanation.length > this.explanationLengthThreshold) {
                return false;
            }
            if (question.hint && question.hint.length > this.hintLengthThreshold) {
                return false;
            }
            switch (question.type) {
                case QuizQuestionType.MULTIPLE_CHOICE: {
                    const mcQuestion = question as MultipleChoiceQuestion;
                    const correctOptions = mcQuestion.answerOptions!.filter((answerOption) => answerOption.isCorrect).length;
                    return (
                        (mcQuestion.singleChoice ? correctOptions === 1 : correctOptions > 0) &&
                        question.title &&
                        question.title !== '' &&
                        question.title.length < this.maxLengthThreshold &&
                        mcQuestion.answerOptions!.every(
                            (answerOption) =>
                                (!answerOption.explanation || answerOption.explanation.length <= this.explanationLengthThreshold) &&
                                (!answerOption.hint || answerOption.hint.length <= this.hintLengthThreshold),
                        )
                    );
                }
                case QuizQuestionType.DRAG_AND_DROP: {
                    const dndQuestion = question as DragAndDropQuestion;
                    return (
                        question.title &&
                        question.title !== '' &&
                        question.title.length < this.maxLengthThreshold &&
                        dndQuestion.correctMappings &&
                        dndQuestion.correctMappings.length > 0 &&
                        this.dragAndDropQuestionUtil.solve(dndQuestion).length &&
                        this.dragAndDropQuestionUtil.validateNoMisleadingCorrectMapping(dndQuestion)
                    );
                }
                case QuizQuestionType.SHORT_ANSWER: {
                    const shortAnswerQuestion = question as ShortAnswerQuestion;
                    return (
                        question.title &&
                        question.title !== '' &&
                        shortAnswerQuestion.correctMappings &&
                        shortAnswerQuestion.correctMappings.length > 0 &&
                        this.shortAnswerQuestionUtil.validateNoMisleadingShortAnswerMapping(shortAnswerQuestion) &&
                        this.shortAnswerQuestionUtil.everySpotHasASolution(shortAnswerQuestion.correctMappings, shortAnswerQuestion.spots!) &&
                        this.shortAnswerQuestionUtil.everyMappedSolutionHasASpot(shortAnswerQuestion.correctMappings) &&
                        shortAnswerQuestion.solutions?.filter((solution) => solution.text!.trim() === '').length === 0 &&
                        shortAnswerQuestion.solutions?.filter((solution) => solution.text!.trim().length >= this.maxLengthThreshold).length === 0 &&
                        !this.shortAnswerQuestionUtil.hasMappingDuplicateValues(shortAnswerQuestion.correctMappings) &&
                        this.shortAnswerQuestionUtil.atLeastAsManySolutionsAsSpots(shortAnswerQuestion) &&
                        this.shortAnswerQuestionUtil.everyNumberSpotHasValidSolution(shortAnswerQuestion.correctMappings, shortAnswerQuestion.spots!)
                    );
                }
                default: {
                    captureException(new Error('Unknown question type: ' + question));
                    return question.title && question.title !== '';
                }
            }
        }, this);
        const maxPointsReachableInQuiz = this.quizExercise.quizQuestions?.map((quizQuestion) => quizQuestion.points ?? 0).reduce((a, b) => a + b, 0);

        return (
            isGenerallyValid &&
            areAllQuestionsValid === true &&
            this.isEmpty(this.invalidFlaggedQuestions) &&
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
        if (this.quizExercise.title!.length >= this.maxLengthThreshold) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.quizTitleLength',
                translateValues: { threshold: this.maxLengthThreshold },
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
            if (!question.title || question.title === '') {
                invalidReasons.push({
                    translateKey: 'artemisApp.quizExercise.invalidReasons.questionTitle',
                    translateValues: { index: index + 1 },
                });
            }
            if (question.points == undefined || question.points < 1) {
                invalidReasons.push({
                    translateKey: 'artemisApp.quizExercise.invalidReasons.questionScore',
                    translateValues: { index: index + 1 },
                });
            }
            if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                const mcQuestion = question as MultipleChoiceQuestion;
                const correctOptions = mcQuestion.answerOptions!.filter((answerOption) => answerOption.isCorrect).length;
                if (correctOptions === 0) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.questionCorrectAnswerOption',
                        translateValues: { index: index + 1 },
                    });
                }
                if (mcQuestion.singleChoice && correctOptions > 1) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.questionSingleChoiceCorrectAnswerOptions',
                        translateValues: { index: index + 1 },
                    });
                }
                if (!mcQuestion.answerOptions!.every((answerOption) => answerOption.explanation !== '')) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.explanationIsMissing',
                        translateValues: { index: index + 1 },
                    });
                }
                if (mcQuestion.answerOptions!.some((answerOption) => answerOption.explanation && answerOption.explanation.length > this.explanationLengthThreshold)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.answerExplanationLength',
                        translateValues: { index: index + 1, threshold: this.explanationLengthThreshold },
                    });
                }
                if (mcQuestion.answerOptions!.some((answerOption) => answerOption.hint && answerOption.hint.length > this.hintLengthThreshold)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.answerHintLength',
                        translateValues: { index: index + 1, threshold: this.hintLengthThreshold },
                    });
                }
            }
            if (question.title && question.title.length >= this.maxLengthThreshold) {
                invalidReasons.push({
                    translateKey: 'artemisApp.quizExercise.invalidReasons.questionTitleLength',
                    translateValues: { index: index + 1, threshold: this.maxLengthThreshold },
                });
            }
            if (question.explanation && question.explanation.length > this.explanationLengthThreshold) {
                invalidReasons.push({
                    translateKey: 'artemisApp.quizExercise.invalidReasons.questionExplanationLength',
                    translateValues: { index: index + 1, threshold: this.explanationLengthThreshold },
                });
            }
            if (question.hint && question.hint.length > this.hintLengthThreshold) {
                invalidReasons.push({
                    translateKey: 'artemisApp.quizExercise.invalidReasons.questionHintLength',
                    translateValues: { index: index + 1, threshold: this.hintLengthThreshold },
                });
            }

            if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                const dndQuestion = question as DragAndDropQuestion;
                if (!dndQuestion.correctMappings || dndQuestion.correctMappings.length === 0) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.questionCorrectMapping',
                        translateValues: { index: index + 1 },
                    });
                } else if (this.dragAndDropQuestionUtil.solve(dndQuestion, []).length === 0) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.questionUnsolvable',
                        translateValues: { index: index + 1 },
                    });
                }
                if (!this.dragAndDropQuestionUtil.validateNoMisleadingCorrectMapping(dndQuestion)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.misleadingCorrectMapping',
                        translateValues: { index: index + 1 },
                    });
                }
            }
            if (question.type === QuizQuestionType.SHORT_ANSWER) {
                const shortAnswerQuestion = question as ShortAnswerQuestion;
                if (!shortAnswerQuestion.correctMappings || shortAnswerQuestion.correctMappings.length === 0) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.questionCorrectMapping',
                        translateValues: { index: index + 1 },
                    });
                }
                if (!this.shortAnswerQuestionUtil.validateNoMisleadingShortAnswerMapping(shortAnswerQuestion)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.misleadingCorrectMapping',
                        translateValues: { index: index + 1 },
                    });
                }
                if (!this.shortAnswerQuestionUtil.everySpotHasASolution(shortAnswerQuestion.correctMappings!, shortAnswerQuestion.spots!)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.shortAnswerQuestionEverySpotHasASolution',
                        translateValues: { index: index + 1 },
                    });
                }
                if (!this.shortAnswerQuestionUtil.everyMappedSolutionHasASpot(shortAnswerQuestion.correctMappings!)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.shortAnswerQuestionEveryMappedSolutionHasASpot',
                        translateValues: { index: index + 1 },
                    });
                }
                if (!(shortAnswerQuestion.solutions?.filter((solution) => solution.text!.trim() === '').length === 0)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.shortAnswerQuestionSolutionHasNoValue',
                        translateValues: { index: index + 1 },
                    });
                }
                if (shortAnswerQuestion.solutions?.filter((solution) => solution.text!.trim().length >= this.maxLengthThreshold).length !== 0) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.quizAnswerOptionLength',
                        translateValues: { index: index + 1, threshold: this.maxLengthThreshold },
                    });
                }
                if (this.shortAnswerQuestionUtil.hasMappingDuplicateValues(shortAnswerQuestion.correctMappings!)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.shortAnswerQuestionDuplicateMapping',
                        translateValues: { index: index + 1 },
                    });
                }
                if (!this.shortAnswerQuestionUtil.atLeastAsManySolutionsAsSpots(shortAnswerQuestion)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.shortAnswerQuestionUnsolvable',
                        translateValues: { index: index + 1 },
                    });
                }
                if (!this.shortAnswerQuestionUtil.everyNumberSpotHasValidSolution(shortAnswerQuestion.correctMappings!, shortAnswerQuestion.spots!)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.shortAnswerQuestionEverySpotNumberHasAValidSolution',
                        translateValues: { index: index + 1 },
                    });
                }
            }
        }, this);
        const invalidFlaggedReasons = !this.quizExercise
            ? []
            : this.quizExercise.quizQuestions
                  ?.map((question, index) => {
                      if (this.invalidFlaggedQuestions[question.title!]) {
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

    checkForInvalidFlaggedQuestions(questions: QuizQuestion[] = []) {
        if (!this.quizExercise) {
            return;
        }
        if (questions.length === 0) {
            questions = this.quizExercise.quizQuestions!;
        }
        const invalidQuestions: {
            [questionId: number]: (AnswerOption | ShortAnswerSolution | ShortAnswerMapping | ShortAnswerSpot | DropLocation | DragItem | DragAndDropMapping)[] | undefined;
        } = {};
        questions.forEach((question) => {
            const invalidQuestion = question.invalid;
            const invalidElements: (AnswerOption | ShortAnswerSolution | ShortAnswerMapping | ShortAnswerSpot | DropLocation | DragItem | DragAndDropMapping)[] = [];
            if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                this.pushToInvalidElements((<MultipleChoiceQuestion>question).answerOptions, invalidElements);
            } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                this.pushToInvalidElements((<DragAndDropQuestion>question).dragItems, invalidElements);
                this.pushToInvalidElements((<DragAndDropQuestion>question).correctMappings, invalidElements);
                this.pushToInvalidElements((<DragAndDropQuestion>question).dropLocations, invalidElements);
            } else {
                this.pushToInvalidElements((<ShortAnswerQuestion>question).solutions, invalidElements);
                this.pushToInvalidElements((<ShortAnswerQuestion>question).correctMappings, invalidElements);
                this.pushToInvalidElements((<ShortAnswerQuestion>question).spots, invalidElements);
            }
            if (invalidQuestion || invalidElements.length !== 0) {
                invalidQuestions[question.title!] = invalidElements.length !== 0 ? { invalidElements } : {};
            }
        });
        this.invalidFlaggedQuestions = invalidQuestions;
    }

    /**
     * Helper function in order to prevent code duplication in computeInvalidReasons
     * Iterates over the array and pushes invalid elements to invalidElements
     * @param array the array containing elements that can be invalid
     * @param invalidElements the array all invalid elements are pushed to
     * @private
     */
    private pushToInvalidElements(
        array: CanBecomeInvalid[] | undefined,
        invalidElements: (AnswerOption | ShortAnswerSolution | ShortAnswerMapping | ShortAnswerSpot | DropLocation | DragItem | DragAndDropMapping)[],
    ): void {
        if (array !== undefined) {
            array!.forEach(function (option: CanBecomeInvalid) {
                if (option.invalid) {
                    invalidElements.push(option);
                }
            });
        }
    }
    /**
     * check if Dictionary is empty
     * @param obj the dictionary to be checked
     */
    protected isEmpty(obj: {}) {
        return Object.keys(obj).length === 0;
    }
}
