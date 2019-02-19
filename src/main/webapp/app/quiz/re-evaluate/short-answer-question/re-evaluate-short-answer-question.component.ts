import {
    Component,
    Input,
    Output,
    EventEmitter,
    OnInit,
    OnChanges,
    SimpleChanges,
    AfterViewInit,
    ViewChild,
    ViewChildren,
    QueryList
} from '@angular/core';
import { ShortAnswerQuestion } from '../../../entities/short-answer-question';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { AceEditorComponent } from 'ng2-ace-editor';
import {ShortAnswerSolution} from "app/entities/short-answer-solution";
import {ShortAnswerSpot} from "app/entities/short-answer-spot";
import {ShortAnswerQuestionUtil} from "app/components/util/short-answer-question-util.service";

@Component({
    selector: 'jhi-re-evaluate-short-answer-question',
    templateUrl: './re-evaluate-short-answer-question.component.html',
    providers: [ArtemisMarkdown]
})
export class ReEvaluateShortAnswerQuestionComponent implements OnInit, AfterViewInit, OnChanges {
    @ViewChild('questionEditor')
    private questionEditor: AceEditorComponent;

    @ViewChildren(AceEditorComponent)
    aceEditorComponents: QueryList<AceEditorComponent>;

    @Input()
    question: ShortAnswerQuestion;
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
    editorMode = 'markdown';
    editorAutoUpdate = true;

    // Create Backup Question for resets
    backupQuestion: ShortAnswerQuestion;

    // has all solution options with their mapping (each spotNr)
    optionsWithID: string [] = [];

    constructor(private artemisMarkdown: ArtemisMarkdown,
                private shortAnswerQuestionUtil: ShortAnswerQuestionUtil) {}

    ngOnInit(): void {}

    ngAfterViewInit(): void {
        /** Setup editor **/
        requestAnimationFrame(this.setupQuestionEditor.bind(this));
        this.setupSolutionsEditors();
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
        // Default editor settings for inline markup editor
        this.questionEditor.setTheme('chrome');
        this.questionEditor.getEditor().renderer.setShowGutter(false);
        this.questionEditor.getEditor().renderer.setPadding(10);
        this.questionEditor.getEditor().renderer.setScrollMargin(8, 8);
        this.questionEditor.getEditor().setHighlightActiveLine(false);
        this.questionEditor.getEditor().setShowPrintMargin(false);
        this.questionEditorText = this.artemisMarkdown.generateTextHintExplanation(this.question);
        this.questionEditor.getEditor().clearSelection();

        // Register the onBlur listener
        this.questionEditor.getEditor().on(
            'blur',
            () => {
                // Parse the markdown in the editor and update question accordingly
                this.parseQuestionMarkdown(this.questionEditorText);
                this.questionUpdated.emit();
            },
            this
        );
    }

    /**
     * @function
     * @desc Setup answerOption editors
     */
    setupSolutionsEditors() {
        /** Array with all answer option Ace Editors
         *  Note: we filter out the question Editor (identified by his width)
         **/
        const solutionEditors: AceEditorComponent[] = this.aceEditorComponents
            .toArray()
            .filter(editor => editor.style.indexOf('width:90%') === -1);

        this.question.solutions.forEach((solution, index) => {
            requestAnimationFrame(
                function() {
                    solutionEditors[index].setTheme('chrome');
                    solutionEditors[index].getEditor().renderer.setShowGutter(false);
                    solutionEditors[index].getEditor().renderer.setPadding(10);
                    solutionEditors[index].getEditor().renderer.setScrollMargin(8, 8);
                    solutionEditors[index].getEditor().setHighlightActiveLine(false);
                    solutionEditors[index].getEditor().setShowPrintMargin(false);
                    solutionEditors[index].getEditor().setOptions({
                        autoScrollEditorIntoView: true
                    });
                    solutionEditors[index].getEditor().setValue(this.generateSolutionMarkdown(solution, index));
                    solutionEditors[index].getEditor().clearSelection();

                    solutionEditors[index].getEditor().on(
                        'blur',
                        () => {
                            this.parseSolutionMarkdown(solutionEditors[index].getEditor().getValue(), solution);
                            this.questionUpdated.emit();
                        },
                        this
                    );
                }.bind(this)
            );
        });
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
    generateSolutionMarkdown(solution: ShortAnswerSolution, index: number): string {
        this.setOptionsWithID();
        //TODO FDE: add hint and/or explanation
        return this.optionsWithID[index]+ ' ' + solution.text.trim();
    }

    /**
     * @function setOptionsWithID
     * @desc Set up of all solution option with their mapping (spotNr)
     */
    setOptionsWithID() {
        for (const solution of this.question.solutions) {
            let spotsForSolution: ShortAnswerSpot[] = [];
            let option = '[-option ';
            let firstSolution = true;
            spotsForSolution = this.shortAnswerQuestionUtil.getAllSpotsForSolutions(this.question.correctMappings, solution);

            for (const spotForSolution of spotsForSolution) {
                if (firstSolution) {
                    option += this.question.spots.filter(spot => spot.id === spotForSolution.id)[0].spotNr;
                    firstSolution = false;
                } else {
                    option += ',' + this.question.spots.filter(spot => spot.id === spotForSolution.id)[0].spotNr;
                }
            }
            option += ']';
            this.optionsWithID.push(option);
        }
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
        const questionParts = text.split(/\[-option /g);
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
    parseSolutionMarkdown(text: string, solution: ShortAnswerSolution) {
        text = text.trim();
        // First split up by "[-option " tag and seperate first part of the split as text and second part as solutionParts
        const questionParts = text.split(/\[-option /g);

        // Split new created Array by "]" to generate this structure: {"1,2", " SolutionText"}
        const solutionParts = questionParts.map(questionPart => questionPart.split(/\]/g)).slice(1);

        // Work on answer options
        // Find the box (text in-between the parts)
        const solutionText = solutionParts[1][0];
        const startOfThisPart = text.indexOf(solutionText);
        const spotsMapping = text.substring(0, startOfThisPart);


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
        this.resetQuestionTitle();
        this.question.invalid = this.backupQuestion.invalid;
        this.question.randomizeOrder = this.backupQuestion.randomizeOrder;
        this.question.scoringType = this.backupQuestion.scoringType;
        this.question.solutions = JSON.parse(JSON.stringify(this.backupQuestion.solutions));
        // Reset answer editors
        this.setupSolutionsEditors();
        this.resetQuestionText();
    }

    /**
     * @function resetSolution
     * @desc Resets the whole solution
     * @param solution {ShortAnswerSolution} the solution, which will be reset
     */
    resetSolution(solution: ShortAnswerSolution) {
        // Find correct answer if they have another order
        const backupSolution = this.backupQuestion.solutions.find(solutionBackup => solution.id === solutionBackup.id);
        // Find current index of our AnswerOption
        const solutionIndex = this.question.solutions.indexOf(solution);
        // Remove current answerOption at given index and insert the backup at the same position
        this.question.solutions.splice(solutionIndex, 1);
        this.question.solutions.splice(solutionIndex, 0, backupSolution);
    }

    /**
     * @function deleteSolution
     * @desc Delete the solution
     * @param solution {ShortAnswerSolution} the solution which should be deleted
     */
    deleteSolution(solution: ShortAnswerSolution) {
        const index = this.question.solutions.indexOf(solution);
        this.question.solutions.splice(index, 1);
    }

    /**
     * @function setSolutionInvalid
     * @desc Set the solution invalid
     * @param  solution {ShortAnswerSolution} the solution which should be deleted
     */
    setSolutionInvalid(solution: ShortAnswerSolution) {
        this.question.solutions[this.question.solutions.indexOf(solution)].invalid = true;
        this.questionUpdated.emit();
    }

    /**
     * @function isSolutionInvalid
     * @desc Checks if the given solution is invalid
     * @param  solution {ShortAnswerSolution} the solution which should be checked
     * @return {boolean} true if the answer is invalid
     */
    isSolutionInvalid(solution: ShortAnswerSolution) {
        return solution.invalid;
    }
}
