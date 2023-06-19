import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, QueryList, ViewChildren, ViewEncapsulation } from '@angular/core';
import { QuizQuestion, QuizQuestionType, ScoringType } from 'app/entities/quiz/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { QuizQuestionEdit } from 'app/exercises/quiz/manage/quiz-question-edit.interface';
import { MultipleChoiceQuestionEditComponent } from 'app/exercises/quiz/manage/multiple-choice-question/multiple-choice-question-edit.component';
import { DragAndDropQuestionEditComponent } from 'app/exercises/quiz/manage/drag-and-drop-question/drag-and-drop-question-edit.component';
import { ShortAnswerQuestionEditComponent } from 'app/exercises/quiz/manage/short-answer-question/short-answer-question-edit.component';

@Component({
    selector: 'jhi-quiz-question-list-edit',
    templateUrl: './quiz-question-list-edit.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrls: ['./quiz-question-list-edit.component.scss', '../shared/quiz.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class QuizQuestionListEditComponent {
    @Input() courseId: number;
    @Input() quizQuestions: QuizQuestion[] = [];
    @Input() disabled = false;

    @Output() onQuestionAdded = new EventEmitter<QuizQuestion>();
    @Output() onQuestionUpdated = new EventEmitter();
    @Output() onQuestionDeleted = new EventEmitter<QuizQuestion>();

    @ViewChildren('editMultipleChoice')
    editMultipleChoiceQuestionComponents: QueryList<MultipleChoiceQuestionEditComponent>;

    @ViewChildren('editDragAndDrop')
    editDragAndDropQuestionComponents: QueryList<DragAndDropQuestionEditComponent>;

    @ViewChildren('editShortAnswer')
    editShortAnswerQuestionComponents: QueryList<ShortAnswerQuestionEditComponent>;

    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    faPlus = faPlus;

    showExistingQuestions = false;

    /**
     * Emit onQuestionUpdated if there is an update of the question.
     */
    handleQuestionUpdated() {
        this.onQuestionUpdated.emit();
    }

    /**
     * Remove the QuizQuestion from the quizQuestions list according to the given index.
     *
     * @param index the index of QuizQuestion to be deleted
     */
    handleQuestionDeleted(index: number) {
        const quizQuestion = this.quizQuestions[index];
        this.quizQuestions.splice(index, 1);
        this.onQuestionDeleted.emit(quizQuestion);
    }

    /**
     * Toggle showExistingQuestions flag and add the newly added quiz questions to the quizQuestions list.
     * Then, emit onQuestionAdded.
     *
     * @param quizQuestions the list of newly added QuizQuestions
     */
    handleExistingQuestionsAdded(quizQuestions: Array<QuizQuestion>) {
        this.showExistingQuestions = !this.showExistingQuestions;
        for (const quizQuestion of quizQuestions) {
            this.addQuestion(quizQuestion);
        }
    }

    /**
     * Add an empty multiple choice question to the quiz
     */
    addMultipleChoiceQuestion() {
        const mcQuestion = new MultipleChoiceQuestion();
        mcQuestion.title = '';
        mcQuestion.text = 'Enter your long question if needed';
        mcQuestion.hint = 'Add a hint here (visible during the quiz via ?-Button)';
        mcQuestion.scoringType = ScoringType.ALL_OR_NOTHING; // explicit default value for multiple questions
        mcQuestion.randomizeOrder = true;
        mcQuestion.points = 1;

        const correctSampleAnswerOption = new AnswerOption();
        correctSampleAnswerOption.isCorrect = true;
        correctSampleAnswerOption.text = 'Enter a correct answer option here';
        correctSampleAnswerOption.hint = 'Add a hint here (visible during the quiz via ?-Button)';
        correctSampleAnswerOption.explanation = 'Add an explanation here (only visible in feedback after quiz has ended)';

        const incorrectSampleAnswerOption = new AnswerOption();
        incorrectSampleAnswerOption.isCorrect = false;
        incorrectSampleAnswerOption.text = 'Enter a wrong answer option here';

        mcQuestion.answerOptions = [correctSampleAnswerOption, incorrectSampleAnswerOption];
        this.addQuestion(mcQuestion);
    }

    /**
     * Add an empty drag and drop question to the quiz
     */
    addDragAndDropQuestion() {
        const dndQuestion = new DragAndDropQuestion();
        dndQuestion.title = '';
        dndQuestion.text = 'Enter your long question if needed';
        dndQuestion.hint = 'Add a hint here (visible during the quiz via ?-Button)';
        dndQuestion.scoringType = ScoringType.PROPORTIONAL_WITH_PENALTY; // explicit default value for drag and drop questions
        dndQuestion.randomizeOrder = true;
        dndQuestion.points = 1;
        dndQuestion.dropLocations = [];
        dndQuestion.dragItems = [];
        dndQuestion.correctMappings = [];
        this.addQuestion(dndQuestion);
    }

    /**
     * Add an empty short answer question to the quiz
     */
    addShortAnswerQuestion(): void {
        const shortAnswerQuestion = new ShortAnswerQuestion();
        shortAnswerQuestion.title = '';
        shortAnswerQuestion.text =
            'Enter your long question if needed\n\n' +
            'Select a part of the text and click on Add Spot to automatically create an input field and the corresponding mapping\n\n' +
            'You can define a input field like this: This [-spot 1] an [-spot 2] field.\n\n' +
            'To define the solution for the input fields you need to create a mapping (multiple mapping also possible):\n\n' +
            '[-option 1] is\n' +
            '[-option 2] input\n' +
            '[-option 1,2] correctInBothFields';
        shortAnswerQuestion.scoringType = ScoringType.PROPORTIONAL_WITHOUT_PENALTY; // explicit default value for short answer questions
        shortAnswerQuestion.randomizeOrder = true;
        shortAnswerQuestion.points = 1;
        shortAnswerQuestion.spots = [];
        shortAnswerQuestion.solutions = [];
        shortAnswerQuestion.correctMappings = [];
        this.addQuestion(shortAnswerQuestion);
    }

    /**
     * Toggles existing questions view
     */
    showHideExistingQuestions() {
        this.showExistingQuestions = !this.showExistingQuestions;
    }

    /**
     * triggers the parsing of the editor content in the designated edit component
     */
    parseAllQuestions() {
        const editQuestionComponents: Array<QuizQuestionEdit> = [
            ...this.editMultipleChoiceQuestionComponents.toArray(),
            ...this.editDragAndDropQuestionComponents.toArray(),
            ...this.editShortAnswerQuestionComponents.toArray(),
        ];
        editQuestionComponents.forEach((component) => component.prepareForSave());
    }

    /**
     * Add the given QuizQuestion to the quizQuestions list. Then, emit onQuestionAdded event.
     *
     * @param quizQuestion the QuizQuestion to be added.
     */
    private addQuestion(quizQuestion: QuizQuestion) {
        this.quizQuestions!.push(quizQuestion);
        this.onQuestionAdded.emit(quizQuestion);
    }
}
