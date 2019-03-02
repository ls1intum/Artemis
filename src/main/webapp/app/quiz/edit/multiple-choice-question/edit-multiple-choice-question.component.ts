import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { MultipleChoiceQuestion } from '../../../entities/multiple-choice-question';
import { AnswerOption } from '../../../entities/answer-option';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { AceEditorComponent } from 'ng2-ace-editor';
import 'brace/theme/chrome';
import 'brace/mode/markdown';
import { MarkdownEditorComponent } from 'app/markdown-editor';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import {HintCommand} from 'app/markdown-editor/specialCommands/hint.command';
import {CorrectOptionCommand} from 'app/markdown-editor/specialCommands/correctOptionCommand';
import {IncorrectOptionCommand} from 'app/markdown-editor/specialCommands/incorrectOptionCommand';
import {ExplanationCommand} from 'app/markdown-editor/specialCommands/explanation.command';
import {SpecialCommand} from 'app/markdown-editor/specialCommands/specialCommand';

@Component({
    selector: 'jhi-edit-multiple-choice-question',
    templateUrl: './edit-multiple-choice-question.component.html',
    providers: [ArtemisMarkdown]
})
export class EditMultipleChoiceQuestionComponent implements OnInit {
    @ViewChild('questionEditor')
    private questionEditor: AceEditorComponent;

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
    questionEditorMode = 'markdown';
    questionEditorAutoUpdate = true;

    /** Status boolean for collapse status **/
    isQuestionCollapsed: boolean;

    currentAnswerOption: AnswerOption;

    showPreview: boolean;

    hintCommand = new HintCommand();
    correctCommand = new CorrectOptionCommand();
    incorrectCommand = new IncorrectOptionCommand();
    explanationCommand = new ExplanationCommand();

    commandMultipleChoiceQuestions: SpecialCommand[] = [this.correctCommand, this.incorrectCommand, this.explanationCommand, this.hintCommand];

    constructor(private artemisMarkdown: ArtemisMarkdown, private modalService: NgbModal) {
    }

    ngOnInit(): void {
        this.showPreview = false;
        this.setupQuestionEditor();
    }

    /**
     * @function setupQuestionEditor
     * @desc Initializes the ace editor for the mc question
     */
    setupQuestionEditor(): void {
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
                        (answerOption.isCorrect ? '[x]' : '[ ]') + ' ' + this.artemisMarkdown.generateTextHintExplanation(answerOption)
                )
                .join('\n');
        return markdownText;
    }

    /**
     * This function opens the modal for the help dialog.
     */
    open(content: any) {
        this.modalService.open(content, {size: 'lg'});
    }

    /**
     * @function togglePreview
     * @desc Toggles the preview in the template
     */
    togglePreview(): void {
        this.question.answerOptions.splice(0);
        this.markdownEditor.parse();
        this.showPreview = !this.showPreview;
    }

    toggleAntiPreview(): void {
        this.showPreview = false;
    }

    specialCommandFound(textLine: string, specialCommand: SpecialCommand) {
        if (specialCommand instanceof CorrectOptionCommand) {
                this.currentAnswerOption = new AnswerOption();
                this.currentAnswerOption.isCorrect = true;
                this.currentAnswerOption.text = textLine;
                this.question.answerOptions.push(this.currentAnswerOption)
            } else if (specialCommand instanceof IncorrectOptionCommand) {
                this.currentAnswerOption = new AnswerOption();
                this.currentAnswerOption.isCorrect = false;
                this.currentAnswerOption.text = textLine;
                this.question.answerOptions.push(this.currentAnswerOption)
            } else if (specialCommand instanceof ExplanationCommand) {
                if (this.currentAnswerOption != null) {
                    this.currentAnswerOption.explanation = textLine;
                } else {
                    this.question.explanation = textLine;
                }
            } else if (specialCommand instanceof HintCommand) {
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
