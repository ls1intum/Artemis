import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, ViewChild } from '@angular/core';
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
import {Command} from 'app/markdown-editor/commands/command';
import {BoldCommand} from 'app/markdown-editor/commands/bold.command';
import {ItalicCommand} from 'app/markdown-editor/commands/italic.command';
import {UnderlineCommand} from 'app/markdown-editor/commands/underline.command';
import {SpecialCommand} from 'app/markdown-editor/specialCommands/specialCommand';

@Component({
    selector: 'jhi-edit-multiple-choice-question',
    templateUrl: './edit-multiple-choice-question.component.html',
    providers: [ArtemisMarkdown]
})
export class EditMultipleChoiceQuestionComponent implements OnInit, OnChanges {
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

    showPreview: boolean;

    hintCommand = new HintCommand();
    correctCommand = new CorrectOptionCommand();
    incorrectCommand = new IncorrectOptionCommand();
    explanationCommand = new ExplanationCommand();

    commandMultipleChoiceQuestions: SpecialCommand[] = [this.correctCommand, this.incorrectCommand, this.explanationCommand, this.hintCommand];

    constructor(private artemisMarkdown: ArtemisMarkdown, private modalService: NgbModal) {}

    ngOnInit(): void {
        this.showPreview = false;
        this.setupQuestionEditor();
    }

    /**
     * @function ngOnChanges
     * @desc Watch for any changes to the question model and notify listener
     * @param changes {SimpleChanges}
     */
    ngOnChanges(changes: SimpleChanges): void {
        /** Check if previousValue wasn't null to avoid firing at component initialization **/
        if (changes.question && changes.question.previousValue != null) {
            this.questionUpdated.emit();
        }
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
     * @function parseMarkdown
     * @param text {string} the markdown text to parse
     * @desc Parse the markdown and apply the result to the question's data
     * The markdown rules are as follows:
     *
     * 1. Text is split at [x] and [ ] (also accepts [X] and [])
     *    => The first part (any text before the first [x] or [ ]) is the question text
     * 2. The question text is split into text, hint, and explanation using ArtemisMarkdown
     * 3. For every answer option (Parts after each [x] or [ ]):
     *    3.a) Same treatment as the question text for text, hint, and explanation
     *    3.b) Answer options are marked as isCorrect depending on [ ] or [x]
     *
     * Note: Existing IDs for answer options are reused in the original order.
     */
    parseMarkdown(text: string): void {
        //this.markdownEditor.searchForTheParsingCommand();

            // First split by [], [ ], [x] and [X]
        const questionParts = text.split(/\[\]|\[ \]|\[x\]|\[X\]/g);
        const questionText = questionParts[0];

        // Split question into main text, hint and explanation
        this.artemisMarkdown.parseTextHintExplanation(questionText, this.question);

        // Extract existing answer option IDs
        const existingAnswerOptionIDs = this.question.answerOptions
            .filter(questionAnswerOption => questionAnswerOption.id != null)
            .map(questionAnswerOption => questionAnswerOption.id);
        this.question.answerOptions = [];

        let endOfPreviousPart = text.indexOf(questionText) + questionText.length;
        /**
         * Work on answer options
         * We slice the first questionPart since that's our question text and no real answer option
         */
        for (const answerOptionText of questionParts.slice(1)) {
            // Find the box (text in-between the parts)
            const answerOption = new AnswerOption();
            const startOfThisPart = text.indexOf(answerOptionText, endOfPreviousPart);
            const box = text.substring(endOfPreviousPart, startOfThisPart);
            // Check if box says this answer option is correct or not
            answerOption.isCorrect = box === '[x]' || box === '[X]';
            // Update endOfPreviousPart for next loop
            endOfPreviousPart = startOfThisPart + answerOptionText.length;

            // Parse this answerOption
            this.artemisMarkdown.parseTextHintExplanation(answerOptionText, answerOption);

            // Assign existing ID if available
            if (this.question.answerOptions.length < existingAnswerOptionIDs.length) {
                answerOption.id = existingAnswerOptionIDs[this.question.answerOptions.length];
            }
            this.question.answerOptions.push(answerOption);
        }
    }

    /**
     * This function opens the modal for the help dialog.
     */
    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }

    /**
     * @function togglePreview
     * @desc Toggles the preview in the template
     */
    togglePreview(): void {
        this.showPreview = !this.showPreview;
        console.log('inform MarkdownEditor about command', this.markdownEditor);
        this.markdownEditor.parse();
        // TODO: reset the current question
    }

    currentAnswerOption: AnswerOption;

    specialCommmandFound(textLine: string, specialCommand: SpecialCommand) {
        if (specialCommand instanceof CorrectOptionCommand) {
            this.currentAnswerOption = new AnswerOption();
            this.currentAnswerOption.isCorrect = true;
            this.currentAnswerOption.text = textLine;
            this.question.answerOptions.push(this.currentAnswerOption)
        }
        else if (specialCommand instanceof IncorrectOptionCommand) {
            this.currentAnswerOption = new AnswerOption();
            this.currentAnswerOption.isCorrect = false;
            this.currentAnswerOption.text = textLine;
            this.question.answerOptions.push(this.currentAnswerOption)
        }
        else if (specialCommand instanceof ExplanationCommand) {
            if (this.currentAnswerOption != null) {
                this.currentAnswerOption.explanation = textLine;
            }
            else {
                this.question.explanation = textLine;
            }
        }
        else if (specialCommand instanceof HintCommand) {
            if (this.currentAnswerOption != null) {
                this.currentAnswerOption.hint = textLine;
            }
            else {
                this.question.hint = textLine;
            }
        }
    }

    handleResponse(response: any) {
        console.log('Client recevied', response);
    }

    /**
     * @function deleteQuestion
     * @desc Delete this question from the quiz
     */
    deleteQuestion(): void {
        this.questionDeleted.emit();
    }
}
