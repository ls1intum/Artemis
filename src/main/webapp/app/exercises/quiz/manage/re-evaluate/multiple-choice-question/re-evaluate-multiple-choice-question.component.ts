import { AfterViewInit, Component, EventEmitter, Input, OnInit, Output, QueryList, ViewChild, ViewChildren } from '@angular/core';
import { AceEditorComponent } from 'ng2-ace-editor';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { CorrectOptionCommand } from 'app/shared/markdown-editor/domainCommands/correctOptionCommand';
import { IncorrectOptionCommand } from 'app/shared/markdown-editor/domainCommands/incorrectOptionCommand';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';

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

    // Create Backup Question for resets
    @Input()
    backupQuestion: MultipleChoiceQuestion;

    /**
     * Constructs the re-evaluate component.
     * @param artemisMarkdown the ArtemisMarkdownService
     */
    constructor(private artemisMarkdown: ArtemisMarkdownService) {}

    /**
     * Setup content
     */
    ngOnInit(): void {
        this.setQuestionText();
        this.setAnswerTexts();
    }

    /**
     * Setup editor after view init
     */
    ngAfterViewInit(): void {
        this.setupQuestionEditor();
    }

    setQuestionText(): void {
        this.questionEditorText = this.artemisMarkdown.generateTextHintExplanation(this.question);
    }

    setAnswerTexts(): void {
        // eslint-disable-next-line chai-friendly/no-unused-expressions
        this.question.answerOptions?.forEach((answerOption, index) => {
            const answerToSet = Object.assign({}, answerOption);
            this.answerEditorText[index] = this.generateAnswerMarkdown(answerToSet);
        });
    }

    /**
     * Setup Question text editor
     */
    setupQuestionEditor() {
        console.log('setup');
        if (this.aceEditorComponents) {
            this.aceEditorComponents.forEach((editor) => {
                editor.setTheme('chrome');
                editor.getEditor().renderer.setShowGutter(false);
                editor.getEditor().renderer.setPadding(10);
                editor.getEditor().renderer.setScrollMargin(8, 8);
                editor.getEditor().setHighlightActiveLine(false);
                editor.getEditor().setShowPrintMargin(false);
                editor.getEditor().setOptions({
                    autoScrollEditorIntoView: true,
                    wrap: true,
                });

                editor.getEditor().on(
                    'blur',
                    () => {
                        console.log('updated');
                        this.questionUpdated.emit();
                    },
                    this,
                );
            });
        }
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
        answer.isCorrect = box === CorrectOptionCommand.identifier;
        this.artemisMarkdown.parseTextHintExplanation(answerOptionText, answer);
    }

    /**
     * Split text by [correct] and [wrong]
     * @param text
     */
    private splitByCorrectIncorrectTag(text: string): string[] {
        const stringForSplit = escapeStringForUseInRegex(`${CorrectOptionCommand.identifier}`) + '|' + escapeStringForUseInRegex(`${IncorrectOptionCommand.identifier}`);
        const splitRegExp = new RegExp(stringForSplit, 'g');
        return text.split(splitRegExp);
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
        this.question = Object.assign(this.question, { text: this.backupQuestion.text, explanation: this.backupQuestion.explanation, hint: this.backupQuestion.hint });
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
        this.question.answerOptions!.forEach((answer, i) => {
            this.resetAnswer(answer, i);
        });
        // Reset answer editors
        this.resetQuestionText();
        this.setAnswerTexts();
    }

    /**
     * Resets the whole answer
     * @param answer {AnswerOption} the answer, which will be reset
     */
    resetAnswer(answer: AnswerOption, index: number) {
        // Find correct answer if they have another order
        const backupAnswer = this.backupQuestion.answerOptions!.find((answerBackup) => answer.id === answerBackup.id)!;
        // Overwrite current answerOption at given index with the backup
        this.question.answerOptions![index] = backupAnswer;
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

    onQuestionChange($event: any): void {
        const updatedQuestion = Object.assign({}, this.question);
        const questionParts = this.splitByCorrectIncorrectTag($event);
        const questionText = questionParts[0];
        this.artemisMarkdown.parseTextHintExplanation(questionText, updatedQuestion);
        this.question = Object.assign(this.question, { text: updatedQuestion.text, explanation: updatedQuestion.explanation, hint: updatedQuestion.hint });
        this.setQuestionText();
    }

    onAnswerChange($event: any, index: number): void {
        // workaround for unwanted copy overwriting of other properties
        const updatedAnswer = Object.assign({}, this.question.answerOptions![index]);
        this.parseAnswerMarkdown($event, updatedAnswer);
        this.question.answerOptions![index] = { ...updatedAnswer };
    }
}
