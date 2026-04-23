import { ChangeDetectionStrategy, Component, ViewEncapsulation, inject, input, output, signal, viewChildren } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { QuizQuestion, QuizQuestionType, ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { QuizQuestionEdit } from 'app/quiz/manage/interfaces/quiz-question-edit.interface';
import { MultipleChoiceQuestionEditComponent } from 'app/quiz/manage/multiple-choice-question/multiple-choice-question-edit.component';
import { DragAndDropQuestionEditComponent } from 'app/quiz/manage/drag-and-drop-question/drag-and-drop-question-edit.component';
import { ShortAnswerQuestionEditComponent } from 'app/quiz/manage/short-answer-question/short-answer-question-edit.component';
import { ApollonDiagramImportDialogComponent } from 'app/quiz/manage/apollon-diagrams/import-dialog/apollon-diagram-import-dialog.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';
import { QuizQuestionListEditExistingComponent } from '../list-edit-existing/quiz-question-list-edit-existing.component';
import { QuizAiQuestionRefinementPanelComponent } from 'app/quiz/manage/quiz-ai-question-refinement-panel/quiz-ai-question-refinement-panel.component';

@Component({
    selector: 'jhi-quiz-question-list-edit',
    templateUrl: './quiz-question-list-edit.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrls: ['./quiz-question-list-edit.component.scss', '../../shared/quiz.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [
        TranslateDirective,
        MultipleChoiceQuestionEditComponent,
        DragAndDropQuestionEditComponent,
        ShortAnswerQuestionEditComponent,
        FaIconComponent,
        NgClass,
        QuizQuestionListEditExistingComponent,
        QuizAiQuestionRefinementPanelComponent,
        FeatureToggleHideDirective,
    ],
})
export class QuizQuestionListEditComponent {
    private modalService = inject(NgbModal);

    courseId = input.required<number>();
    quizQuestions = input<QuizQuestion[]>([]);
    disabled = input(false);
    hyperionEnabled = input(false);

    onQuestionAdded = output<QuizQuestion>();
    onQuestionUpdated = output<void>();
    onQuestionDeleted = output<QuizQuestion>();

    readonly editMultipleChoiceQuestionComponents = viewChildren<MultipleChoiceQuestionEditComponent>('editMultipleChoice');

    readonly editDragAndDropQuestionComponents = viewChildren<DragAndDropQuestionEditComponent>('editDragAndDrop');

    readonly editShortAnswerQuestionComponents = viewChildren<ShortAnswerQuestionEditComponent>('editShortAnswer');

    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;
    readonly ApollonQuizDragAndDrop = FeatureToggle.ApollonQuizDragAndDrop;

    faPlus = faPlus;

    /** Questions whose AI refinement panel is currently open. */
    openRefinementQuestions = signal(new Set<QuizQuestion>());
    /** Questions whose editor card is currently collapsed. */
    collapsedQuestions = signal(new Set<QuizQuestion>());

    showExistingQuestions = false;

    fileMap = new Map<string, { path?: string; file: File }>();

    /**
     * Emit onQuestionUpdated if there is an update of the question.
     */
    handleQuestionUpdated() {
        this.onQuestionUpdated.emit();
    }

    /**
     * Toggle the AI refinement panel for the given question.
     *
     * @param question the question whose refinement panel to toggle
     */
    toggleRefinement(question: QuizQuestion): void {
        const updated = new Set(this.openRefinementQuestions());
        if (updated.has(question)) {
            updated.delete(question);
        } else {
            updated.add(question);
        }
        this.openRefinementQuestions.set(updated);
    }

    /**
     * Track collapsed state of a question so the AI refinement panel hides while collapsed
     * but its open state is preserved for when the question is expanded again.
     *
     * @param question the question whose collapsed state changed
     * @param collapsed whether the question is now collapsed
     */
    handleCollapseChanged(question: QuizQuestion, collapsed: boolean): void {
        const updated = new Set(this.collapsedQuestions());
        if (collapsed) {
            updated.add(question);
        } else {
            updated.delete(question);
        }
        this.collapsedQuestions.set(updated);
    }

    /**
     * Refresh the edit component after the question object was mutated in-place by AI refinement.
     *
     * @param question the refined question object
     * @param _refinedQuestion the refined Question
     */
    handleQuestionRefined(question: QuizQuestion, _refinedQuestion: MultipleChoiceQuestion) {
        // The question object was mutated in-place; find the corresponding MC edit component and reload its editor.
        const index = this.quizQuestions().indexOf(question);
        if (index < 0) {
            return;
        }
        const mcIndex = this.quizQuestions()
            .slice(0, index)
            .filter((q) => q.type === this.MULTIPLE_CHOICE).length;
        const mcComponent = this.editMultipleChoiceQuestionComponents()[mcIndex];
        if (mcComponent) {
            mcComponent.reloadFromQuestion();
            this.onQuestionUpdated.emit();
        }
    }

    /**
     * Remove the given QuizQuestion from the quizQuestions list.
     *
     * @param quizQuestion the QuizQuestion to be deleted
     */
    handleQuestionDeleted(quizQuestion: QuizQuestion) {
        const index = this.quizQuestions().indexOf(quizQuestion);
        if (index < 0) {
            return;
        }
        this.quizQuestions().splice(index, 1);
        const openUpdated = new Set(this.openRefinementQuestions());
        openUpdated.delete(quizQuestion);
        this.openRefinementQuestions.set(openUpdated);
        const collapsedUpdated = new Set(this.collapsedQuestions());
        collapsedUpdated.delete(quizQuestion);
        this.collapsedQuestions.set(collapsedUpdated);
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
     * Add the given file to the fileMap for later upload.
     * @param event the event containing the file and its name. The name provided may be different from the actual file name but has to correspond to the name set in the entity object.
     */
    handleFileAdded(event: { fileName: string; path?: string; file: File }) {
        this.fileMap.set(event.fileName, { file: event.file, path: event.path });
    }

    /**
     * Remove the given file from the fileMap.
     * @param fileName the name of the file to be removed
     */
    handleFileRemoved(fileName: string) {
        this.fileMap.delete(fileName);
    }

    /**
     * Add all files from the given map to the fileMap.
     * @param filesMap the map of files to be added
     */
    handleFilesAdded(filesMap: Map<string, { path: string; file: File }>) {
        filesMap.forEach((value, fileName) => {
            this.fileMap.set(fileName, value);
        });
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

    async importApollonDragAndDropQuestion() {
        const modalRef: NgbModalRef = this.modalService.open(ApollonDiagramImportDialogComponent as Component, { size: 'xl', backdrop: 'static' });

        const courseIdValue = this.courseId();

        const instance = modalRef.componentInstance;
        instance.courseId = signal(courseIdValue);

        const question = await modalRef.result;
        if (question) {
            this.addQuestion(question);
        }
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
            'To define the solution for the input fields you need to create a mapping (multiple mapping also possible):\n\n\n' +
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
            ...this.editMultipleChoiceQuestionComponents(),
            ...this.editDragAndDropQuestionComponents(),
            ...this.editShortAnswerQuestionComponents(),
        ];
        editQuestionComponents.forEach((component) => component.prepareForSave());
    }

    /**
     * Add the given QuizQuestion to the quizQuestions list. Then, emit onQuestionAdded event.
     *
     * @param quizQuestion the QuizQuestion to be added.
     */
    private addQuestion(quizQuestion: QuizQuestion) {
        this.quizQuestions().push(quizQuestion);
        this.onQuestionAdded.emit(quizQuestion);
    }
}
