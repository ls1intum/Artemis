import { AfterViewInit, Component, inject, input, model } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { QuizQuestion, QuizQuestionType, ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { MultipleChoiceSubmittedAnswer } from 'app/quiz/shared/entities/multiple-choice-submitted-answer.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-quiz-scoring-infostudent-modal',
    templateUrl: './quiz-scoring-info-student-modal.component.html',
    styleUrls: ['./quiz-scoring-info-student-modal.component.scss'],
    imports: [TranslateDirective, FaIconComponent, NgClass],
})
export class QuizScoringInfoStudentModalComponent implements AfterViewInit {
    private modalService = inject(NgbModal);
    private translateService = inject(TranslateService);

    QuizQuestionType = QuizQuestionType;
    ScoringType = ScoringType;

    readonly score = model<number | undefined>(undefined!);
    readonly questionIndex = input<number | undefined>(undefined!);
    readonly question = input.required<QuizQuestion>();
    readonly dragAndDropMapping = input(new Array<DragAndDropMapping>());
    readonly incorrectlyMappedDragAndDropItems = input<number>(0);
    readonly mappedLocations = input<number>(undefined!);
    readonly multipleChoiceMapping = input(new Array<AnswerOption>());
    readonly shortAnswerText = input(new Array<ShortAnswerSubmittedText>());
    readonly correctlyMappedDragAndDropItems = input<number>(undefined!);
    readonly multipleChoiceSubmittedResult = input<Result>(undefined!);
    readonly quizQuestions = input<QuizQuestion[]>();

    /* Multiple Choice Counting Variables*/
    multipleChoiceCorrectAnswerCorrectlyChosen: number;
    multipleChoiceWrongAnswerChosen: number;
    correctMultipleChoiceAnswers: number;
    forgottenMultipleChoiceRightAnswers: number;
    multipleChoiceAnswerOptions: number;
    inTotalSelectedRightOptions: number;
    inTotalSelectedWrongOptions: number;
    differenceMultipleChoice: number;
    checkForCorrectAnswers = new Array<AnswerOption>();
    checkForWrongAnswers = new Array<AnswerOption>();
    isSingleChoice: boolean;

    /* Drag and Drop Counting Variables*/
    differenceDragAndDrop: number;

    /* Short Answer Counting Variables*/
    shortAnswerSpots: number;
    shortAnswerCorrectAnswers: number;
    shortAnswerWrongAnswers: number;
    differenceShortAnswer: number;

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

    /**
     * Count the variables depending on the quiz question type
     */
    ngAfterViewInit() {
        this.checkForSingleOrPluralPoints();
        switch (this.question().type) {
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
        for (const question of this.quizQuestions() || []) {
            const mcQuizQuestion = question as MultipleChoiceQuestion;
            if (mcQuizQuestion.id === this.question().id) {
                answerOptionsOfQuestion = mcQuizQuestion.answerOptions!;
                this.correctMultipleChoiceAnswers = mcQuizQuestion.answerOptions!.filter((option) => option.isCorrect).length;
            }
        }

        const multipleChoiceSubmittedResult = this.multipleChoiceSubmittedResult();
        if (!multipleChoiceSubmittedResult || !multipleChoiceSubmittedResult.submission) {
            return;
        }
        const submittedQuizSubmission = multipleChoiceSubmittedResult.submission as QuizSubmission;
        const submittedAnswerLength = submittedQuizSubmission.submittedAnswers?.length ?? 0;
        for (let i = 0; i < submittedAnswerLength; i++) {
            if (submittedQuizSubmission.submittedAnswers![i].quizQuestion!.id === this.question().id) {
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
        const mcmQuestion = this.question() as MultipleChoiceQuestion;
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
        this.differenceDragAndDrop = this.correctlyMappedDragAndDropItems() - this.incorrectlyMappedDragAndDropItems();

        if (this.correctlyMappedDragAndDropItems() === 1) {
            this.rightMap = this.translateService.instant(translationBasePath + 'item');
        } else {
            this.rightMap = this.translateService.instant(translationBasePath + 'items');
        }

        if (this.incorrectlyMappedDragAndDropItems() === 1) {
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
        const shortAnswer = this.question() as ShortAnswerQuestion;
        this.shortAnswerSpots = shortAnswer.spots!.length;
        this.shortAnswerCorrectAnswers = this.shortAnswerText().filter((option) => option.isCorrect).length;
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
        if (this.question().points === 1) {
            this.questionPoint = this.translateService.instant(translationBasePath + 'point');
        } else {
            this.questionPoint = this.translateService.instant(translationBasePath + 'points');
        }

        if (this.score() === undefined) {
            this.score.set(0);
        }

        if (this.score() === 1) {
            this.scorePoint = this.translateService.instant(translationBasePath + 'point');
        } else {
            this.scorePoint = this.translateService.instant(translationBasePath + 'points');
        }
    }
}
