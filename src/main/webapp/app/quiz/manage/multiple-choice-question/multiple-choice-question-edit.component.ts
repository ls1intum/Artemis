import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ViewEncapsulation, inject, output, viewChild } from '@angular/core';
import { NgbCollapse, NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { QuizQuestionEdit } from 'app/quiz/manage/interfaces/quiz-question-edit.interface';
import { MultipleChoiceQuestionComponent } from 'app/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { generateExerciseHintExplanation } from 'app/shared/util/markdown.util';
import { faAngleDown, faAngleRight, faQuestionCircle, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { MAX_QUIZ_QUESTION_POINTS } from 'app/shared/constants/input.constants';
import { QuizHintAction } from 'app/shared/monaco-editor/model/actions/quiz/quiz-hint.action';
import { WrongMultipleChoiceAnswerAction } from 'app/shared/monaco-editor/model/actions/quiz/wrong-multiple-choice-answer.action';
import { CorrectMultipleChoiceAnswerAction } from 'app/shared/monaco-editor/model/actions/quiz/correct-multiple-choice-answer.action';
import { QuizExplanationAction } from 'app/shared/monaco-editor/model/actions/quiz/quiz-explanation.action';
import { MarkdownEditorMonacoComponent, TextWithDomainAction } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { MultipleChoiceVisualQuestionComponent } from 'app/quiz/shared/questions/multiple-choice-question/visual-question/multiple-choice-visual-question.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { QuizScoringInfoModalComponent } from '../quiz-scoring-info-modal/quiz-scoring-info-modal.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { OnInit, input } from '@angular/core';

@Component({
    selector: 'jhi-multiple-choice-question-edit',
    templateUrl: './multiple-choice-question-edit.component.html',
    styleUrls: ['../exercise/quiz-exercise.scss', '../../../quiz/shared/quiz.scss'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FaIconComponent,
        FormsModule,
        TranslateDirective,
        NgbCollapse,
        QuizScoringInfoModalComponent,
        NgbTooltip,
        MarkdownEditorMonacoComponent,
        MultipleChoiceQuestionComponent,
        MultipleChoiceVisualQuestionComponent,
        ArtemisTranslatePipe,
    ],
})
export class MultipleChoiceQuestionEditComponent implements QuizQuestionEdit, OnInit {
    private modalService = inject(NgbModal);
    private changeDetector = inject(ChangeDetectorRef);

    readonly markdownEditor = viewChild.required<MarkdownEditorMonacoComponent>('markdownEditor');

    readonly visualChild = viewChild.required<MultipleChoiceVisualQuestionComponent>('visual');

    question = input.required<MultipleChoiceQuestion>();
    questionIndex = input.required<number>();

    questionUpdated = output();
    questionDeleted = output();

    questionEditorText = '';
    isQuestionCollapsed: boolean;

    /** Set default preview of the markdown editor as preview for the multiple choice question **/
    get showPreview(): boolean {
        return this.markdownEditor()?.inPreviewMode;
    }
    showMultipleChoiceQuestionPreview = true;
    showMultipleChoiceQuestionVisual = true;

    correctAction = new CorrectMultipleChoiceAnswerAction();
    wrongAction = new WrongMultipleChoiceAnswerAction();
    explanationAction = new QuizExplanationAction();
    hintAction = new QuizHintAction();

    multipleChoiceActions = [this.correctAction, this.wrongAction, this.explanationAction, this.hintAction];

    // Icons
    faTrash = faTrash;
    faAngleRight = faAngleRight;
    faAngleDown = faAngleDown;
    faQuestionCircle = faQuestionCircle;

    readonly MAX_POINTS = MAX_QUIZ_QUESTION_POINTS;

    /**
     * Init the question editor text by parsing the markdown.
     */
    ngOnInit(): void {
        this.questionEditorText = this.generateMarkdown();
    }

    /**
     * Generate the markdown text for this question
     * 1. First the question text, hint, and explanation are added using ArtemisMarkdown
     * 2. After an empty line, the answer options are added
     * 3. For each answer option: text, hint and explanation are added using ArtemisMarkdown
     */
    generateMarkdown(): string {
        const markdownText =
            generateExerciseHintExplanation(this.question()) +
            '\n\n' +
            this.question()
                .answerOptions!.map((answerOption) => (answerOption.isCorrect ? '[correct]' : '[wrong]') + ' ' + generateExerciseHintExplanation(answerOption))
                .join('\n');
        return markdownText;
    }

    onSingleChoiceChanged(): void {
        if (this.question().singleChoice) {
            this.question().scoringType = ScoringType.ALL_OR_NOTHING;
        }
    }

    /**
     * open the modal for the help dialog
     * @param content
     */
    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }

    /**
     * Detect of text changes in the markdown editor
     * 1. Parse the text in the editor to get the newest values
     * 2. Notify the parent component to check the validity of the text
     */
    changesInMarkdown(): void {
        this.prepareForSave();
        this.questionUpdated.emit();
        this.changeDetector.detectChanges();
    }

    /**
     * Detect of changes in the visual editor
     * 1. Parse the text in the editor to get the newest values
     * 2. Notify the parent component to check the validity of the text
     */
    changesInVisualMode(): void {
        this.questionUpdated.emit();
        this.changeDetector.detectChanges();
    }

    /**
     * Triggers the saving process by cleaning up the question and calling the markdown parse function
     * to get the newest values in the editor to update the question attributes
     */
    prepareForSave(): void {
        const markdownEditor = this.markdownEditor();
        if (markdownEditor.inVisualMode) {
            /*
             * In the visual mode, the latest question values come from the visual tab, not the markdown editor.
             * We update the markdown editor, which triggers the parsing of the visual tab content.
             */
            markdownEditor.markdown = this.visualChild().parseQuestion();
        } else {
            this.cleanupQuestion();
            markdownEditor.parseMarkdown();
        }
    }

    onLeaveVisualTab(): void {
        this.markdownEditor().markdown = this.visualChild().parseQuestion();
        this.prepareForSave();
    }

    /**
     * @function cleanupQuestion
     * @desc Clear the question to avoid double assignments of one attribute
     */
    private cleanupQuestion() {
        // Reset Question Object
        this.question().answerOptions = [];
        this.question().text = undefined;
        this.question().explanation = undefined;
        this.question().hint = undefined;
        this.question().hasCorrectOption = undefined;
    }

    /**
     * 1. Gets a tuple of text and domain action identifiers and assigns text values according to the domain actions a
     *    multiple choice question the to the multiple choice question attributes.
     *   (question text, explanation, hint, answerOption (correct/wrong)
     * 2. The tuple order is the same as the order of the actions in the markdown text inserted by the user
     * 3. resetMultipleChoicePreview() is triggered to notify the parent component
     *    about the changes within the question and to cacheValidation() since the assigned values have changed
     * @param textWithDomainActions The parsed text segments with their corresponding domain actions.
     */
    domainActionsFound(textWithDomainActions: TextWithDomainAction[]): void {
        this.cleanupQuestion();
        let currentAnswerOption;

        for (const { text, action } of textWithDomainActions) {
            if (action === undefined && text.length > 0) {
                this.question().text = text;
            }
            if (action instanceof CorrectMultipleChoiceAnswerAction || action instanceof WrongMultipleChoiceAnswerAction) {
                currentAnswerOption = new AnswerOption();
                currentAnswerOption.isCorrect = action instanceof CorrectMultipleChoiceAnswerAction;
                currentAnswerOption.text = text;
                this.question().answerOptions!.push(currentAnswerOption);
            } else if (action instanceof QuizExplanationAction) {
                if (currentAnswerOption) {
                    currentAnswerOption.explanation = text;
                } else {
                    this.question().explanation = text;
                }
            } else if (action instanceof QuizHintAction) {
                if (currentAnswerOption) {
                    currentAnswerOption.hint = text;
                } else {
                    this.question().hint = text;
                }
            }
        }
        this.resetMultipleChoicePreview();
        this.resetMultipleChoiceVisual();
    }

    /**
     * @function resetMultipleChoicePreview
     * @desc  Reset the preview function of the multiple choice question in order to cause a change
     *        so the parent component is notified
     *        and the check for the question validity is triggered
     */
    private resetMultipleChoicePreview() {
        this.showMultipleChoiceQuestionPreview = false;
        this.changeDetector.detectChanges();
        this.showMultipleChoiceQuestionPreview = true;
        this.changeDetector.detectChanges();
    }

    private resetMultipleChoiceVisual() {
        this.showMultipleChoiceQuestionVisual = false;
        this.changeDetector.detectChanges();
        this.showMultipleChoiceQuestionVisual = true;
        this.changeDetector.detectChanges();
    }

    /**
     * Delete this question from the quiz
     */
    deleteQuestion(): void {
        this.questionDeleted.emit();
    }
}
