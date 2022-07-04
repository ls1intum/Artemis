import { AfterViewInit, Component, Input } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Result } from 'app/entities/result.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { QuizQuestion, QuizQuestionType, ScoringType } from 'app/entities/quiz/quiz-question.model';
import { MultipleChoiceSubmittedAnswer } from 'app/entities/quiz/multiple-choice-submitted-answer.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';

@Component({
    selector: 'jhi-quiz-scoring-infostudent-modal',
    templateUrl: './quiz-scoring-info-student-modal.component.html',
    styleUrls: ['./quiz-scoring-info-student-modal.component.scss'],
})
export class QuizScoringInfoStudentModalComponent implements AfterViewInit {
    QuizQuestionType = QuizQuestionType;
    ScoringType = ScoringType;

    @Input() score: number; // Score of the student that has been achieved
    @Input() questionIndex: number; // Question Index of the question
    @Input() question: QuizQuestion;
    @Input() dragAndDropMapping = new Array<DragAndDropMapping>();
    @Input() incorrectlyMappedDragAndDropItems: number;
    @Input() mappedLocations: number;
    @Input() multipleChoiceMapping = new Array<AnswerOption>();
    @Input() shortAnswerText = new Array<ShortAnswerSubmittedText>();
    @Input() correctlyMappedDragAndDropItems: number; // Amount of correctly mapped drag and drop items
    @Input() multipleChoiceSubmittedResult: Result;
    @Input() submittedQuizExercise: QuizExercise;

    /* Multiple Choice Counting Variables*/
    multipleChoiceCorrectAnswerCorrectlyChosen: number; // Amount of right options chosen by the student
    multipleChoiceWrongAnswerChosen: number; // Amount of wrong options chosen by the student
    correctMultipleChoiceAnswers: number; // Amount of correct options for the question
    forgottenMultipleChoiceRightAnswers: number; // Amount of wrong options for the question
    multipleChoiceAnswerOptions: number; // Amount of all possible options for the question
    inTotalSelectedRightOptions: number; // Amount of correct and wrong options assigned correctly
    inTotalSelectedWrongOptions: number; // Amount of correct and wrong options assigned wrongly
    differenceMultipleChoice: number; // Difference between inTotalSelectedRightOptions and differenceMultipleChoice
    checkForCorrectAnswers = new Array<AnswerOption>();
    checkForWrongAnswers = new Array<AnswerOption>();
    isSingleChoice: boolean;

    /* Drag and Drop Counting Variables*/
    differenceDragAndDrop: number; // Difference between the incorrectlyMappedDragAndDropItems and correctlyMappedDragAndDropItems

    /* Short Answer Counting Variables*/
    shortAnswerSpots: number; // Amount of short answer spots
    shortAnswerCorrectAnswers: number; // A mount of correctly filled out spots
    shortAnswerWrongAnswers: number; // A mount of wrongly filled out spots
    differenceShortAnswer: number; // Difference between shortAnswerCorrectAnswers and shortAnswerWrongAnswers

    /* Plural Variables*/
    questionPoint: string;
    scorePoint: string;
    wrongOption: string;
    rightOption: string;
    rightMap: string;
    wrongMap: string;
    rightGap: string;
    wrongGap: string;

    // Icons
    farQuestionCircle = faQuestionCircle;

    constructor(private modalService: NgbModal, private translateService: TranslateService) {}

    /**
     * Count the variables depending on the quiz question type
     */
    ngAfterViewInit() {
        this.checkForSingleOrPluralPoints();
        switch (this.question.type) {
            case QuizQuestionType.MULTIPLE_CHOICE:
                this.countMultipleChoice();
                break;
            case QuizQuestionType.DRAG_AND_DROP:
                this.countDragAndDrop();
                break;
            case QuizQuestionType.SHORT_ANSWER:
                this.countShortAnswer();
                break;
        }
    }

    /**
     * opens the pop-up for the explanation of the points
     */
    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }

    /**
     * checks for the correct answerOptions based on the submittedAnswer
     */
    private submittedAnswerCorrectValues() {
        let answerOptionsOfQuestion = new Array<AnswerOption>();
        for (const question of this.submittedQuizExercise.quizQuestions || []) {
            const mcQuizQuestion = question as MultipleChoiceQuestion;
            if (mcQuizQuestion.id === this.question.id) {
                answerOptionsOfQuestion = mcQuizQuestion.answerOptions!;
                this.correctMultipleChoiceAnswers = mcQuizQuestion.answerOptions!.filter((option) => option.isCorrect).length;
            }
        }

        if (!this.multipleChoiceSubmittedResult || !this.multipleChoiceSubmittedResult.submission) {
            return;
        }
        const submittedQuizSubmission = this.multipleChoiceSubmittedResult.submission as QuizSubmission;
        const submittedAnswerLength = submittedQuizSubmission.submittedAnswers?.length ?? 0;
        for (let i = 0; i < submittedAnswerLength; i++) {
            if (submittedQuizSubmission.submittedAnswers![i].quizQuestion!.id === this.question.id) {
                const multipleChoiceSubmittedAnswers = submittedQuizSubmission.submittedAnswers![i] as MultipleChoiceSubmittedAnswer;
                if (multipleChoiceSubmittedAnswers.selectedOptions === undefined) {
                    this.checkForCorrectAnswers = [];
                    this.checkForWrongAnswers = [];
                } else {
                    for (const selectedOption of multipleChoiceSubmittedAnswers.selectedOptions) {
                        for (const answerOptionElement of answerOptionsOfQuestion) {
                            if (selectedOption.id === answerOptionElement.id && answerOptionElement.isCorrect) {
                                this.checkForCorrectAnswers.push(selectedOption);
                            } else if (selectedOption.id === answerOptionElement.id && !answerOptionElement.isCorrect) {
                                this.checkForWrongAnswers.push(selectedOption);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * counts the variables for Multiple Choice Questions
     */
    private countMultipleChoice() {
        this.submittedAnswerCorrectValues();
        const translationBasePath = 'artemisApp.quizExercise.explanationText.';
        const mcmQuestion = this.question as MultipleChoiceQuestion;
        this.isSingleChoice = mcmQuestion.singleChoice ?? false;
        this.multipleChoiceAnswerOptions = mcmQuestion.answerOptions!.length;
        this.multipleChoiceCorrectAnswerCorrectlyChosen = this.checkForCorrectAnswers.length;
        this.multipleChoiceWrongAnswerChosen = this.checkForWrongAnswers.length;
        this.forgottenMultipleChoiceRightAnswers = this.correctMultipleChoiceAnswers - this.multipleChoiceCorrectAnswerCorrectlyChosen;
        this.inTotalSelectedRightOptions =
            this.multipleChoiceCorrectAnswerCorrectlyChosen + (this.multipleChoiceAnswerOptions - this.correctMultipleChoiceAnswers - this.multipleChoiceWrongAnswerChosen);
        this.inTotalSelectedWrongOptions = this.multipleChoiceWrongAnswerChosen + this.forgottenMultipleChoiceRightAnswers;
        this.differenceMultipleChoice = this.inTotalSelectedRightOptions - this.inTotalSelectedWrongOptions;

        if (this.inTotalSelectedRightOptions === 1) {
            this.rightOption = this.translateService.instant(translationBasePath + 'option');
        } else {
            this.rightOption = this.translateService.instant(translationBasePath + 'options');
        }

        if (this.inTotalSelectedWrongOptions === 1) {
            this.wrongOption = this.translateService.instant(translationBasePath + 'option');
        } else {
            this.wrongOption = this.translateService.instant(translationBasePath + 'options');
        }
    }

    /**
     * counts the variables for Drag and Drop Questions
     */
    private countDragAndDrop() {
        const translationBasePath = 'artemisApp.quizExercise.explanationText.';
        this.differenceDragAndDrop = this.correctlyMappedDragAndDropItems - this.incorrectlyMappedDragAndDropItems;

        if (this.correctlyMappedDragAndDropItems === 1) {
            this.rightMap = this.translateService.instant(translationBasePath + 'item');
        } else {
            this.rightMap = this.translateService.instant(translationBasePath + 'items');
        }

        if (this.incorrectlyMappedDragAndDropItems === 1) {
            this.wrongMap = this.translateService.instant(translationBasePath + 'item');
        } else {
            this.wrongMap = this.translateService.instant(translationBasePath + 'items');
        }
    }

    /**
     * counts the variables for Short Answer Questions
     */
    private countShortAnswer() {
        const translationBasePath = 'artemisApp.quizExercise.explanationText.';
        const shortAnswer = this.question as ShortAnswerQuestion;
        this.shortAnswerSpots = shortAnswer.spots!.length;
        this.shortAnswerCorrectAnswers = this.shortAnswerText.filter((option) => option.isCorrect).length;
        this.shortAnswerWrongAnswers = this.shortAnswerSpots - this.shortAnswerCorrectAnswers;
        this.differenceShortAnswer = this.shortAnswerCorrectAnswers - this.shortAnswerWrongAnswers;

        if (this.shortAnswerCorrectAnswers === 1) {
            this.rightGap = this.translateService.instant(translationBasePath + 'textgap');
        } else {
            this.rightGap = this.translateService.instant(translationBasePath + 'textgaps');
        }

        if (this.shortAnswerWrongAnswers === 1) {
            this.wrongGap = this.translateService.instant(translationBasePath + 'textgap');
        } else {
            this.wrongGap = this.translateService.instant(translationBasePath + 'textgaps');
        }
    }

    /**
     * Checks the score of the question and the score the student has achieved, depending on that write either point or points
     */
    private checkForSingleOrPluralPoints() {
        const translationBasePath = 'artemisApp.quizExercise.explanationText.';
        if (this.question.points === 1) {
            this.questionPoint = this.translateService.instant(translationBasePath + 'point');
        } else {
            this.questionPoint = this.translateService.instant(translationBasePath + 'points');
        }

        if (this.score === undefined) {
            this.score = 0;
        }

        if (this.score === 1) {
            this.scorePoint = this.translateService.instant(translationBasePath + 'point');
        } else {
            this.scorePoint = this.translateService.instant(translationBasePath + 'points');
        }
    }
}
