import { Component, Input, Output, OnInit, AfterViewInit, EventEmitter, ViewChild, OnChanges, SimpleChanges } from '@angular/core';
import { ShortAnswerQuestion } from '../../../entities/short-answer-question';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { AceEditorComponent } from 'ng2-ace-editor';
import 'brace/theme/chrome';
import 'brace/mode/markdown';
import { MultipleChoiceQuestion } from 'app/entities/multiple-choice-question';
import { AnswerOption } from 'app/entities/answer-option';

@Component({
    selector: 'jhi-edit-short-answer-question',
    templateUrl: './edit-short-answer-question.component.html',
    providers: [ArtemisMarkdown]
})
export class EditShortAnswerQuestionComponent implements OnInit, OnChanges, AfterViewInit {
    @ViewChild('questionEditor')
    private questionEditor: AceEditorComponent;

    @Input()
    question: ShortAnswerQuestion;
    @Input()
    questionIndex: number;

    @Output()
    questionUpdated = new EventEmitter();
    @Output()
    questionDeleted = new EventEmitter();

    //output move up and down für re-evaluate?

    /** Ace Editor configuration constants **/
    questionEditorText = '';
    questionEditorMode = 'markdown';
    questionEditorAutoUpdate = true;

    /** Status boolean for collapse status **/
    isQuestionCollapsed: boolean;

    showPreview: boolean;

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    ngOnInit(): void {
        this.showPreview = false;
        this.isQuestionCollapsed = false; //auch in MC hinzufügen?
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

    ngAfterViewInit(): void {
        /** Setup editor **/
        requestAnimationFrame(this.setupQuestionEditor.bind(this));
    }

    /**
     * @function setupQuestionEditor
     * @desc Initializes the ace editor for the mc question
     */
    setupQuestionEditor(): void {
        this.questionEditor.setTheme('chrome');
        this.questionEditor.getEditor().renderer.setShowGutter(false);
        this.questionEditor.getEditor().renderer.setPadding(10);
        this.questionEditor.getEditor().renderer.setScrollMargin(8, 8);
        this.questionEditor.getEditor().setHighlightActiveLine(false);
        this.questionEditor.getEditor().setShowPrintMargin(false);

        // Generate markdown from question and show result in editor
        this.questionEditorText = this.artemisMarkdown.generateTextHintExplanation(this.question);

        //oder selbst markdown selbst generieren hier oder in markdown.service.ts
        //this.questionEditorText = this.generateMarkdown();

        this.questionEditor.getEditor().clearSelection();

        this.questionEditor.getEditor().on(
            'blur',
            () => {
                this.parseMarkdown(this.questionEditorText);
                this.questionUpdated.emit();
            },
            this
        );
    }

    /**
     * @function generateMarkdown
     * @desc Generate the markdown text for this question
     * 1. First the question text, hint, and explanation are added using ArtemisMarkdown
     * 2. After an empty line, the answer options are added
     * 3. For each answer option: text, hint and explanation are added using ArtemisMarkdown
     */

    //hier oder in markdown.service.ts
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

    //create here or in markdown.service.ts for SA questions
    parseMarkdown(text: string): void {
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
     * @function addHintAtCursor
     * @desc Add the markdown for a hint at the current cursor location
     */
    addHintAtCursor(): void {
        this.artemisMarkdown.addHintAtCursor(this.questionEditor.getEditor());
    }

    /**
     * @function addExplanationAtCursor
     * @desc Add the markdown for an explanation at the current cursor location
     */
    addExplanationAtCursor(): void {
        this.artemisMarkdown.addExplanationAtCursor(this.questionEditor.getEditor());
    }

    /**
     * @function togglePreview
     * @desc Toggles the preview in the template
     */
    togglePreview(): void {
        this.showPreview = !this.showPreview;
    }

    /**
     * @function deleteQuestion
     * @desc Delete this question from the quiz
     */
    deleteQuestion(): void {
        this.questionDeleted.emit();
    }
}
