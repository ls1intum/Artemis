import { Component, Input, Output, OnInit, AfterViewInit, EventEmitter, ViewChild } from '@angular/core';
import { MultipleChoiceQuestion } from '../../../entities/multiple-choice-question';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';

@Component({
    selector: 'jhi-edit-multiple-choice-question',
    templateUrl: './edit-multiple-choice-question.component.html'
})
export class EditMultipleChoiceQuestionComponent implements OnInit, AfterViewInit {

    @ViewChild('editor') editor;

    /**
     question: '=',
     onDelete: '&',
     onUpdated: '&',
     questionIndex: '<'
     **/
    @Input() question: MultipleChoiceQuestion;
    @Input() questionIndex: number;

    @Output() questionUpdated = new EventEmitter<object>();
    @Output() questionDeleted = new EventEmitter<object>();

    showPreview: boolean;
    scoringTypeOptions = [
        {
            key: 'ALL_OR_NOTHING',
            label: 'All or Nothing'
        },
        {
            key: 'PROPORTIONAL_WITH_PENALTY',
            label: 'Proportional with Penalty'
        }
    ];

    // $translatePartialLoader.addPart('quizExercise');
    // $translatePartialLoader.addPart('question');
    // $translatePartialLoader.addPart('multipleChoiceQuestion');
    // $translate.refresh();

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    ngOnInit(): void {
        this.showPreview = false;
    }

    ngAfterViewInit(): void {
        /** Setup editor **/
        const random = Math.random();
        // var editor;
        // requestAnimationFrame(function () {
        //     editor = ace.edit("question-content-editor-" + vm.random);
        //     editor.setTheme("ace/theme/chrome");
        //     editor.getSession().setMode("ace/mode/markdown");
        //     editor.renderer.setShowGutter(false);
        //     editor.renderer.setPadding(10);
        //     editor.renderer.setScrollMargin(8, 8);
        //     editor.setHighlightActiveLine(false);
        //     editor.setShowPrintMargin(false);
        //
        //     generateMarkdown();
        //
        //     editor.on("blur", function () {
        //         parseMarkdown(editor.getValue());
        //         vm.onUpdated();
        //         $scope.$apply();
        //     });
        // });
    }

    /**
     * @function generateMarkdown
     * @desc Generate the markdown text for this question
     * 1. First the question text, hint, and explanation are added using ArtemisMarkdown
     * 2. After an empty line, the answer options are added
     * 3. For each answer option: text, hint and explanation are added using ArtemisMarkdown
     */
    generateMarkdown() {
        const markdownText = (
            this.artemisMarkdown.generateTextHintExplanation(this.question) +
            '\n\n' +
            this.question.answerOptions.map(
                answerOption => (answerOption.isCorrect ? '[x]' : '[ ]') + ' ' + this.artemisMarkdown.generateTextHintExplanation(answerOption)).join('\n')
        );
        this.editor.setValue(markdownText);
        this.editor.clearSelection();
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
     parseMarkdown(text: string) {
        // First split by [], [ ], [x] and [X]
        const questionParts = text.split(/\[\]|\[ \]|\[x\]|\[X\]/g);
        const questionText = questionParts[0];

        // Split question into main text, hint and explanation
        this.artemisMarkdown.parseTextHintExplanation(questionText, this.question);

        // Extract existing answer option IDs
        const existingAnswerOptionIDs = this.question.answerOptions.filter(questionAnswerOption =>
            questionAnswerOption.id != null).map(questionAnswerOption => questionAnswerOption.id);
        this.question.answerOptions = [];

        // Work on answer options
        let endOfPreviousPart = text.indexOf(questionText) + questionText.length;
        for (const answerOptionText of questionParts) {
            // Find the box (text in-between the parts)
            const answerOption = {};
            const startOfThisPart = text.indexOf(answerOptionText, endOfPreviousPart);
            const box = text.substring(endOfPreviousPart, startOfThisPart);
            // Check if box says this answer option is correct or not
            answerOption['isCorrect'] = (box === '[x]' || box === '[X]');
            // Update endOfPreviousPart for next loop
            endOfPreviousPart = startOfThisPart + answerOptionText.length;

            // Parse this answerOption
            this.artemisMarkdown.parseTextHintExplanation(answerOptionText, answerOption);

            // Assign existing ID if available
            if (this.question.answerOptions.length < existingAnswerOptionIDs.length) {
                answerOption['id'] = existingAnswerOptionIDs[this.question.answerOptions.length];
            }
            this.question.answerOptions.push(answerOption);
        }
     }

    /**
     * @function addAnswerOptionTextToEditor
     * @desc Adds the markdown for a correct or incorrect answerOption at the end of the current markdown text
     * @param mode {boolean} mode true sets the text for an correct answerOption, false for an incorrect one
     */
    addAnswerOptionTextToEditor(mode: boolean) {
        const textToAdd = mode ? '\n[x] Enter a correct answer option here' : '\n[ ] Enter an incorrect answer option here';
        let currentText = this.editor.getValue();
        currentText += textToAdd;
        this.editor.setValue(currentText);
        this.editor.focus();
        const lines = currentText.split('\n').length;
        const range = this.editor.selection.getRange();
        range.setStart(lines - 1, 4);
        range.setEnd(lines - 1, textToAdd.length - 1);
        this.editor.selection.setRange(range);
     }

    /**
     * @function addHintAtCursor
     * @desc Adds the markdown for a hint at the current cursor location
     */
    addHintAtCursor() {
        this.artemisMarkdown.addHintAtCursor(this.editor);
    }

    /**
     * @function addExplanationAtCursor
     * @desc Adds the markdown for an explanation at the current cursor location
     */
    addExplanationAtCursor() {
        this.artemisMarkdown.addExplanationAtCursor(this.editor);
    }

    /**
     * @function togglePreview
     * @desc Toggles the preview in the template
     */
    togglePreview() {
        this.showPreview = !this.showPreview;
    }

    /**
     * watch for any changes to the question model and notify listener
     *
     * (use 'initializing' boolean to prevent $watch from firing immediately)
     */
    // var initializing = true;
    // $scope.$watchCollection('vm.question', function () {
    //     if (initializing) {
    //         initializing = false;
    //         return;
    //     }
    //     vm.onUpdated();
    // });

    /**
     * @function deleteQuestion
     * @desc Delete this question from the quiz
     */
    deleteQuestion() {
        this.questionDeleted.emit();
    }
}
