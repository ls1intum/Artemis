import { Component, Input, Output, OnInit, AfterViewInit, EventEmitter, ViewChild, OnChanges, SimpleChanges } from '@angular/core';
import { MultipleChoiceQuestion } from '../../../entities/multiple-choice-question';
import { AnswerOption } from '../../../entities/answer-option';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { AceEditorComponent } from 'ng2-ace-editor';
import 'brace/theme/chrome';
import 'brace/mode/markdown';
import { MarkdownEditorComponent } from 'app/markdown-editor';
import { BDelegate } from 'app/markdown-editor';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-edit-multiple-choice-question',
    templateUrl: './edit-multiple-choice-question.component.html',
    providers: [ArtemisMarkdown]
})
export class EditMultipleChoiceQuestionComponent implements OnInit, OnChanges, BDelegate {
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
        /**
        // First split by [], [ ], [x] and [X]
        const questionParts = text.split(/\[\]|\[ \]|\[x\]|\[X\]/g);
        const questionText = questionParts[0];
         */

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
        this.markdownEditor.searchForTheParsingCommand();
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
