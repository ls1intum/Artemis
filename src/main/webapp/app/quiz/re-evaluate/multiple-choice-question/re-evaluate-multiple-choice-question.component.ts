import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges, AfterViewInit, ViewChild } from '@angular/core';
import { MultipleChoiceQuestion } from '../../../entities/multiple-choice-question';
import { AnswerOption } from '../../../entities/answer-option';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { AceEditorComponent } from 'ng2-ace-editor';

@Component({
    selector: 'jhi-re-evaluate-multiple-choice-question',
    templateUrl: './re-evaluate-multiple-choice-question.component.html',
    providers: [ArtemisMarkdown]
})
export class ReEvaluateMultipleChoiceQuestionComponent implements OnInit, AfterViewInit, OnChanges {
    @ViewChild('questionEditor')
    private questionEditor: AceEditorComponent;

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
    questionEditorMode = 'markdown';
    questionEditorAutoUpdate = true;

    // Create Backup Question for resets
    backupQuestion: MultipleChoiceQuestion;

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    ngOnInit(): void {}

    ngAfterViewInit(): void {
        /** Setup editor **/
        requestAnimationFrame(this.setupQuestionEditor.bind(this));
        this.question.answerOptions.forEach(answer => {
            // TODO: pass answer as argument
            requestAnimationFrame(this.setupAnswerEditors.bind(this));
        });
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.question && changes.question.currentValue != null) {
            this.backupQuestion = Object.assign({}, this.question);
        }
    }

    /**
     * @function setupQuestionEditor
     * @desc Setup Question text editor
     */
    setupQuestionEditor() {
        this.questionEditor.setTheme('chrome');
        this.questionEditor.getEditor().renderer.setShowGutter(false);
        this.questionEditor.getEditor().renderer.setPadding(10);
        this.questionEditor.getEditor().renderer.setScrollMargin(8, 8);
        this.questionEditor.getEditor().setHighlightActiveLine(false);
        this.questionEditor.getEditor().setShowPrintMargin(false);
        this.questionEditorText = this.artemisMarkdown.generateTextHintExplanation(this.question);
        this.questionEditor.getEditor().clearSelection();

        this.questionEditor.getEditor().on(
            'blur',
            () => {
                this.parseQuestionMarkdown(this.questionEditorText);
                // TODO: consider emitting the updated question here
                this.questionUpdated.emit();
                // TODO: $scope.$apply(); ??
            },
            this
        );
    }

    /**
     * @function
     * @desc Setup answerOption editors
     */
    setupAnswerEditors() {
        // var answerEditor;
        // var i = 0;
        // vm.question.answerOptions.forEach(function (answer) {
        //     requestAnimationFrame(function () {
        //         answerEditor = ace.edit("answer-content-editor-" + answer.id);
        //         answerEditor.setTheme("ace/theme/chrome");
        //         answerEditor.getSession().setMode("ace/mode/markdown");
        //         answerEditor.renderer.setShowGutter(false);
        //         answerEditor.renderer.setPadding(10);
        //         answerEditor.renderer.setScrollMargin(8, 8);
        //         answerEditor.setOptions({
        //             autoScrollEditorIntoView: true
        //         });
        //         answerEditor.setHighlightActiveLine(false);
        //         answerEditor.setShowPrintMargin(false);
        //
        //         this.generateAnswerMarkdown(answer);
        //
        //         answerEditor.on("blur", function () {
        //             var answerOptionEditor = ace.edit("answer-content-editor-" + answer.id);
        //             parseAnswerMarkdown(answerOptionEditor.getValue(), answer);
        //             vm.onUpdated();
        //             $scope.$apply();
        //         });
        //     });
        //     i++;
        //
        // });
    }

    /**
     * @function generateAnswerMarkdown
     * @desc Generate the markdown text for this question
     *
     * The markdown is generated according to these rules:
     *
     * 1. First the answer text is inserted
     * 2. If hint and/or explanation exist,
     *      they are added after the text with a linebreak and tab in front of them
     *
     * @param answer {AnswerOption}  is the AnswerOption, which the Markdown-field presents
     */
    generateAnswerMarkdown(answer: AnswerOption) {
        const answerEditor = ace.edit('answer-content-editor-' + answer.id);
        const markdownText = (answer.isCorrect ? '[x]' : '[ ]') + ' ' + this.artemisMarkdown.generateTextHintExplanation(answer);
        answerEditor.setValue(markdownText);
        answerEditor.clearSelection();
    }

    /**
     * @function parseQuestionMarkdown
     * @desc Parse the question-markdown and apply the result to the question's data
     *
     * The markdown rules are as follows:
     *
     * 1. Text is split at [x] and [ ] (also accepts [X] and [])
     *    => The first part (any text before the first [x] or [ ]) is the question text
     * 2. The question text is parsed with ArtemisMarkdown
     *
     * @param text {string} the markdown text to parse
     */
    parseQuestionMarkdown(text: string) {
        // first split by [], [ ], [x] and [X]
        const questionParts = text.split(/\[\]|\[ \]|\[x\]|\[X\]/g);
        const questionText = questionParts[0];
        this.artemisMarkdown.parseTextHintExplanation(questionText, this.question);
    }

    /**
     * @function
     * @desc Parse the an answer markdown and apply the result to the question's data
     *
     * The markdown rules are as follows:
     *
     * 1. Text starts with [x] or [ ] (also accepts [X] and [])
     *    => Answer options are marked as isCorrect depending on [ ] or [x]
     * 2. The answer text is parsed with ArtemisMarkdown
     *
     * @param text {string} the markdown text to parse
     * @param answer {AnswerOption} the answer, where to save the result
     */
    parseAnswerMarkdown(text: string, answer: AnswerOption) {
        text = text.trim();
        // First split by [], [ ], [x] and [X]
        const answerParts = text.split(/\[\]|\[ \]|\[x\]|\[X\]/g);
        // Work on answer options
        // Find the box (text in-between the parts)
        const answerOption = new AnswerOption();
        const answerOptionText = answerParts[1];
        const startOfThisPart = text.indexOf(answerOptionText);
        const box = text.substring(0, startOfThisPart);
        // Check if box says this answer option is correct or not
        answer.isCorrect = box === '[x]' || box === '[X]';

        this.artemisMarkdown.parseTextHintExplanation(answerOptionText, answer);
    }

    /**
     * @function
     * @desc Delete this question from the quiz
     */
    delete() {
        this.questionDeleted.emit();
    }

    /**
     * @function moveUp
     * @desc Move this question one position up
     */
    moveUp() {
        this.questionMoveUp.emit();
    }

    /**
     * @function moveDown
     * @desc Move this question one position down
     */
    moveDown() {
        this.questionMoveDown.emit();
    }

    /**
     * @function
     * @desc Resets the question title
     */
    resetQuestionTitle() {
        this.question.title = this.backupQuestion.title;
    }

    /**
     * @function resetQuestionText
     * @desc Resets the question text
     */
    resetQuestionText() {
        this.question.text = this.backupQuestion.text;
        this.question.explanation = this.backupQuestion.explanation;
        this.question.hint = this.backupQuestion.hint;
        this.setupQuestionEditor();
    }

    /**
     * @function resetQuestion
     * @desc Resets the whole question
     */
    resetQuestion() {
        this.question.title = this.backupQuestion.title;
        this.question.invalid = this.backupQuestion.invalid;
        this.question.randomizeOrder = this.backupQuestion.randomizeOrder;
        this.question.scoringType = this.backupQuestion.scoringType;
        this.question.answerOptions = this.backupQuestion.answerOptions;
        this.question.answerOptions.forEach(answer => this.resetAnswer(answer));
        this.resetQuestionText();
    }

    /**
     * @function resetAnswer
     * @desc Resets the whole answer
     * @param answer {AnswerOption} the answer, which will be reset
     */
    resetAnswer(answer: AnswerOption) {
        // Find correct answer if they have another order
        const backupAnswer = this.backupQuestion.answerOptions.find(answerBackup => answer.id === answerBackup.id);

        // Reset answer in question
        this.question.answerOptions[this.question.answerOptions.indexOf(answer)] = Object.assign({}, backupAnswer);
        answer = Object.assign({}, backupAnswer);

        // Reset answer editor
        this.setupAnswerEditors();
    }

    /**
     * @function deleteAnswer
     * @desc Delete the answer
     * @param answer {AnswerOption} the Answer which should be deleted
     */
    deleteAnswer(answer: AnswerOption) {
        const index = this.question.answerOptions.indexOf(answer);
        this.question.answerOptions.splice(index, 1);
    }

    /**
     * @function setAnswerInvalid
     * @desc Set the answer invalid
     * @param  answer {AnswerOption} the Answer which should be deleted
     */
    setAnswerInvalid(answer: AnswerOption) {
        this.question.answerOptions[this.question.answerOptions.indexOf(answer)].invalid = true;
        this.questionUpdated.emit();
    }

    /**
     * @function isAnswerInvalid
     * @desc Checks if the given answer is invalid
     * @param  answer {AnswerOption} the Answer which should be checked
     * @return {boolean} true if the answer is invalid
     */
    isAnswerInvalid(answer: AnswerOption) {
        return answer.invalid;
    }
}
