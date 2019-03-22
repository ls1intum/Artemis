import { Component, EventEmitter, Input, OnInit, Output, ViewChild, ChangeDetectorRef } from '@angular/core';
import { MultipleChoiceQuestion } from 'app/entities/multiple-choice-question';
import { AnswerOption } from 'app/entities/answer-option';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { MarkdownEditorComponent } from 'app/markdown-editor';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import {
    CorrectOptionCommand,
    ExplanationCommand,
    IncorrectOptionCommand,
    DomainCommand,
    HintCommand,
} from 'app/markdown-editor/domainCommands';
import { EditQuizQuestion } from 'app/quiz/edit/edit-quiz-question.interface';

@Component({
    selector: 'jhi-edit-multiple-choice-question',
    templateUrl: './edit-multiple-choice-question.component.html',
    providers: [ArtemisMarkdown]
})
export class EditMultipleChoiceQuestionComponent implements OnInit, EditQuizQuestion {

    @ViewChild('markdownEditor')
    private markdownEditor: MarkdownEditorComponent;

    @Input()
    question: MultipleChoiceQuestion;
    @Input()
    questionIndex: number;

    @Output()
    questionUpdated = new EventEmitter();
    @Output()
    questionDeleted = new EventEmitter();

    /** Ace Editor configuration constants **/
    questionEditorText = '';

    /** Status boolean for collapse status **/
    isQuestionCollapsed: boolean;

    currentAnswerOption: AnswerOption;

    /** Set default preview of the markdown editor as preview for the multiple choice question **/
    get showPreview(): boolean { return this.markdownEditor.previewMode; }
    showMultipleChoiceQuestionPreview = true;

    hintCommand = new HintCommand();
    correctCommand = new CorrectOptionCommand();
    incorrectCommand = new IncorrectOptionCommand();
    explanationCommand = new ExplanationCommand();

    /** DomainCommands for the multiple choice question **/
    commandMultipleChoiceQuestions: DomainCommand[] = [this.correctCommand, this.incorrectCommand, this.explanationCommand, this.hintCommand];

    constructor(private artemisMarkdown: ArtemisMarkdown, private modalService: NgbModal, private changeDetector: ChangeDetectorRef) {}

    ngOnInit(): void {
        this.questionEditorText = this.generateMarkdown();
    }

    /**
     * @function generateMarkdown
     * @desc Generate the markdown text for this question
     * 1. First the question text, hint, and explanation are added using ArtemisMarkdown
     * 2. After an empty line, the answer options are added
     * 3. For each answer option: text, hint and explanation are added using ArtemisMarkdown
     */
    generateMarkdown(): string {
        const markdownText =
            this.artemisMarkdown.generateTextHintExplanation(this.question) +
            '\n\n' +
            this.question.answerOptions
                .map(
                    answerOption =>
                        (answerOption.isCorrect ? '[-x]' : '[- ]') + ' ' + this.artemisMarkdown.generateTextHintExplanation(answerOption)
                )
                .join('\n');
        return markdownText;
    }

    /**
     * @function open
     * @desc open the modal for the help dialog
     * @param content
     */
    open(content: any) {
        this.modalService.open(content, {size: 'lg'});
    }

    /**
     * @function prepareForSave
     * @desc 1. Triggers the saving process by cleaning up the question and calling the markdown parse function
     *       to get the newest values in the editor to update the question attributes
     *       2. Notify parent component about changes to check the validity of new values of the question attributes
     */
    prepareForSave(): void {
        this.cleanupQuestion();
        this.markdownEditor.parse();
        this.questionUpdated.emit();
    }

    /**
     * @function cleanupQuestion
     * @desc Clear the question to avoid double assignments of one attribute
     */
    private cleanupQuestion() {
        // Reset Question Object
        this.question.answerOptions = [];
        this.question.text = null;
        this.question.explanation = null;
        this.question.hint = null;
        this.question.hasCorrectOption = null;

        // Remove Current Answer Option
        this.currentAnswerOption = null;

    }

    /**
     * @function domainCommandsFound
     * @desc Get the {array} from the editor and assign its values based on the domainCommands
     *       to the corresponding question attributes one by one
     * @param {array} contains markdownTextLine with the corresponding domainCommand {DomainCommand} identifier
     */
    domainCommandsFound(domainCommands: [string, DomainCommand][]): void {
        this.cleanupQuestion();
        domainCommands.forEach(command => this.domainCommandFoundSave(command[0], command[1]));
        this.resetMultipleChoicePreview();
    }

    /**
     * @function resetMultipleChoicePreview
     * @desc 1. Reset the preview function of the multiple choice question
     *       2. Check for changes and notify the parent component to check for the question validity
     */
    private resetMultipleChoicePreview() {
        this.showMultipleChoiceQuestionPreview = false;
        this.changeDetector.detectChanges();
        this.showMultipleChoiceQuestionPreview = true;
        this.changeDetector.detectChanges();
    }

    /**
     * @function  domainCommandFoundSave
     * @desc Assign the text one by one into the corresponding question attributes
     *       1. Determine the domainCommand based on the command identifier
     *          1a. If no command identifier found assign the text to the question text
     *       2. Assign the textLine based on the domainCommand to the corresponding attribute of the multiple choice question
     * @param textLine {string} with the corresponding domainCommand {DomainCommand}
     */
    private domainCommandFoundSave(textLine: string, domainCommand: DomainCommand) {
        if (domainCommand === null && textLine.length > 0) {
            this.question.text = textLine;
        }

        if (domainCommand instanceof CorrectOptionCommand || domainCommand instanceof IncorrectOptionCommand) {
            this.currentAnswerOption = new AnswerOption();
            if (domainCommand instanceof CorrectOptionCommand) {
                this.currentAnswerOption.isCorrect = true;
            } else {
                this.currentAnswerOption.isCorrect = false;
            }
            this.currentAnswerOption.text = textLine;
            this.question.answerOptions.push(this.currentAnswerOption);
        } else if (domainCommand instanceof ExplanationCommand) {
            if (this.currentAnswerOption != null) {
                this.currentAnswerOption.explanation = textLine;
            } else {
                this.question.explanation = textLine;
            }
        } else if (domainCommand instanceof HintCommand) {
            if (this.currentAnswerOption != null) {
                this.currentAnswerOption.hint = textLine;
            } else {
                this.question.hint = textLine;
            }
        }

    }

    /**
     * @function deleteQuestion
     * @desc Delete this question from the quiz
     */
    deleteQuestion(): void {
        this.questionDeleted.emit();
    }
}
