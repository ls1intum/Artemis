import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnInit, Output, QueryList, SimpleChanges, ViewChild, ViewChildren } from '@angular/core';
import { AceEditorComponent } from 'ng2-ace-editor';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { CorrectOptionCommand } from 'app/shared/markdown-editor/domainCommands/correctOptionCommand';
import { IncorrectOptionCommand } from 'app/shared/markdown-editor/domainCommands/incorrectOptionCommand';

@Component({
    selector: 'jhi-re-evaluate-multiple-choice-question',
    templateUrl: './re-evaluate-multiple-choice-question.component.html',
    styleUrls: ['./re-evaluate-multiple-choice-question.component.scss', '../../../shared/quiz.scss'],
    providers: [ArtemisMarkdownService],
})
export class ReEvaluateMultipleChoiceQuestionComponent implements OnInit, AfterViewInit {
    @ViewChild('questionEditor', { static: false })
    private questionEditor: AceEditorComponent;

    @ViewChildren(AceEditorComponent)
    aceEditorComponents: QueryList<AceEditorComponent>;

    @Input()
    question: MultipleChoiceQuestion;
    @Input()
    questionIndex: number;

    @Output()
    questionDeleted = new EventEmitter<object>();
    @Output()
    questionUpdated = new EventEmitter<object>();
    @Output()
    questionMoveUp = new EventEmitter<object>();
    @Output()
    questionMoveDown = new EventEmitter<object>();

    /** Ace Editor configuration constants **/
    questionEditorText = '';
    answerEditorText = new Array<string>();
    editorMode = 'markdown';
    editorAutoUpdate = true;

    // Create Backup Question for resets
    backupQuestion: MultipleChoiceQuestion;

    /**
     * Constructs the re-evaluate component.
     * @param artemisMarkdown the ArtemisMarkdownService
     */
    constructor(private artemisMarkdown: ArtemisMarkdownService) {}

    /**
     * Do nothing
     */
    ngOnInit(): void {
        this.setupQuestionEditor();
    }

    /**
     * Setup editor after view init
     */
    ngAfterViewInit(): void {
        /** Setup editor **/
        this.backupQuestion = Object.assign({}, this.question);
        this.setupQuestionEditor();
        this.setQuestionText();
        this.setAnswerTexts();
    }

    setQuestionText(): void {
        this.questionEditorText = this.artemisMarkdown.generateTextHintExplanation(this.question);
    }

    setAnswerTexts(): void {
        this.question.answerOptions?.forEach((answerOption, index) => {
            this.answerEditorText[index] = this.generateAnswerMarkdown(answerOption);
        });
    }

    /**
     * Setup Question text editor
     */
    setupQuestionEditor() {
        this.aceEditorComponents.forEach((editor) => {
            editor.setTheme('chrome');
            editor.getEditor().renderer.setShowGutter(false);
            editor.getEditor().renderer.setPadding(10);
            editor.getEditor().renderer.setScrollMargin(8, 8);
            editor.getEditor().setHighlightActiveLine(false);
            editor.getEditor().setShowPrintMargin(false);
            editor.getEditor().setOptions({
                autoScrollEditorIntoView: true,
            });
            editor.getEditor().clearSelection();

            editor.getEditor().on(
                'blur',
                () => {
                    this.questionUpdated.emit();
                },
                this,
            );
        });
    }

    /**
     * Generate the markdown text for this question
     *
     * The markdown is generated according to these rules:
     *
     * 1. First the answer text is inserted
     * 2. If hint and/or explanation exist,
     *      they are added after the text with a linebreak and tab in front of them
     *
     * @param answer {AnswerOption}  is the AnswerOption, which the Markdown-field presents
     */
    generateAnswerMarkdown(answer: AnswerOption): string {
        return (answer.isCorrect ? CorrectOptionCommand.identifier : IncorrectOptionCommand.identifier) + ' ' + this.artemisMarkdown.generateTextHintExplanation(answer);
    }

    /**
     * Parse the question-markdown and apply the result to the question's data
     *
     * The markdown rules are as follows:
     *
     * 1. Text is split at [correct] and [wrong]
     *    => The first part (any text before the first [correct] or [wrong]) is the question text
     * 2. The question text is parsed with ArtemisMarkdown
     *
     * @param text {string} the markdown text to parse
     */
    parseQuestionMarkdown(text: string) {
        const questionParts = this.splitByCorrectIncorrectTag(text);
        const questionText = questionParts[0];
        this.artemisMarkdown.parseTextHintExplanation(questionText, this.question);
    }

    /**
     * Parse the an answer markdown and apply the result to the question's data
     *
     * The markdown rules are as follows:
     *
     * 1. Text starts with [correct] or [wrong]
     *    => Answer options are marked as isCorrect depending on [wrong] or [correct]
     * 2. The answer text is parsed with ArtemisMarkdown
     *
     * @param text {string} the markdown text to parse
     * @param answer {AnswerOption} the answer, where to save the result
     */
    parseAnswerMarkdown(text: string, answer: AnswerOption) {
        const answerParts = this.splitByCorrectIncorrectTag(text.trim());
        // Work on answer options
        // Find the box (text in-between the parts)
        const answerOptionText = answerParts[1];
        const startOfThisPart = text.indexOf(answerOptionText);
        const box = text.substring(0, startOfThisPart);
        // Check if box says this answer option is correct or not
        answer.isCorrect = box === '[correct]';
        this.artemisMarkdown.parseTextHintExplanation(answerOptionText, answer);
    }

    /**
     * Split text by [correct] and [wrong]
     * @param text
     */
    private splitByCorrectIncorrectTag(text: string): string[] {
        const correctIncorrectRegex = new RegExp(CorrectOptionCommand.identifier + '|' + IncorrectOptionCommand.identifier);
        return text.split(correctIncorrectRegex);
    }

    /**
     * Delete this question from the quiz
     */
    delete() {
        this.questionDeleted.emit();
    }

    /**
     * Move this question one position up
     */
    moveUp() {
        this.questionMoveUp.emit();
    }

    /**
     * Move this question one position down
     */
    moveDown() {
        this.questionMoveDown.emit();
    }

    /**
     * Resets the question title
     */
    resetQuestionTitle() {
        this.question.title = this.backupQuestion.title;
    }

    /**
     * Resets the question text
     */
    resetQuestionText() {
        this.question.text = this.backupQuestion.text;
        this.question.explanation = this.backupQuestion.explanation;
        this.question.hint = this.backupQuestion.hint;
        this.setQuestionText();
    }

    /**
     * Resets the whole question
     */
    resetQuestion() {
        this.resetQuestionTitle();
        this.question.invalid = this.backupQuestion.invalid;
        this.question.randomizeOrder = this.backupQuestion.randomizeOrder;
        this.question.scoringType = this.backupQuestion.scoringType;
        this.question.answerOptions = JSON.parse(JSON.stringify(this.backupQuestion.answerOptions));
        // Reset answer editors
        this.setupQuestionEditor();
        this.resetQuestionText();
        this.setAnswerTexts();
    }

    /**
     * Resets the whole answer
     * @param answer {AnswerOption} the answer, which will be reset
     */
    resetAnswer(answer: AnswerOption) {
        // Find correct answer if they have another order
        const backupAnswer = this.backupQuestion.answerOptions!.find((answerBackup) => answer.id === answerBackup.id)!;
        // Find current index of our AnswerOption
        const answerIndex = this.question.answerOptions!.indexOf(answer);
        // Overwrite current answerOption at given index with the backup
        this.question.answerOptions![answerIndex] = backupAnswer;
        this.setAnswerTexts();
    }

    /**
     * Delete the answer
     * @param answer {AnswerOption} the Answer which should be deleted
     */
    deleteAnswer(answer: AnswerOption) {
        const index = this.question.answerOptions!.indexOf(answer);
        this.question.answerOptions!.splice(index, 1);
    }

    /**
     * Set the answer invalid
     * @param  answer {AnswerOption} the Answer which should be deleted
     */
    setAnswerInvalid(answer: AnswerOption) {
        const answerIndex = this.question.answerOptions!.indexOf(answer);
        this.question.answerOptions![answerIndex].invalid = true;
        this.questionUpdated.emit();
    }

    /**
     * Checks if the given answer is invalid
     * @param  answer {AnswerOption} the Answer which should be checked
     * @return {boolean} true if the answer is invalid
     */
    isAnswerInvalid(answer: AnswerOption) {
        return answer.invalid;
    }
}
