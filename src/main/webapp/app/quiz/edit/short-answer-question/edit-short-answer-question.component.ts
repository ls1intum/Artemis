import {
    AfterViewInit,
    ChangeDetectorRef,
    Component,
    ElementRef,
    EventEmitter,
    Input,
    OnChanges,
    OnInit,
    Output,
    SimpleChanges,
    ViewChild,
    ViewEncapsulation,
} from '@angular/core';
import { ShortAnswerQuestion } from 'app/entities/short-answer-question';
import { ShortAnswerSpot } from 'app/entities/short-answer-spot';
import { ShortAnswerSolution } from 'app/entities/short-answer-solution';
import { ShortAnswerMapping } from 'app/entities/short-answer-mapping';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { AceEditorComponent } from 'ng2-ace-editor';
import 'brace/theme/chrome';
import 'brace/mode/markdown';
import { ShortAnswerQuestionUtil } from 'app/components/util/short-answer-question-util.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import * as TempID from 'app/quiz/edit/temp-id';
import { EditQuizQuestion } from 'app/quiz/edit/edit-quiz-question.interface';
import { SafeHtmlPipe } from 'app/shared';
import { SafeHtml } from '@angular/platform-browser';

@Component({
    selector: 'jhi-edit-short-answer-question',
    templateUrl: './edit-short-answer-question.component.html',
    styleUrls: ['./edit-short-answer-question.component.scss', '../edit-quiz-question.scss', '../../../quiz.scss'],
    encapsulation: ViewEncapsulation.None,
    providers: [ArtemisMarkdown],
})
export class EditShortAnswerQuestionComponent implements OnInit, OnChanges, AfterViewInit, EditQuizQuestion {
    @ViewChild('questionEditor', { static: false })
    private questionEditor: AceEditorComponent;
    @ViewChild('clickLayer', { static: false })
    private clickLayer: ElementRef;

    @Input()
    question: ShortAnswerQuestion;
    @Input()
    questionIndex: number;
    @Input()
    reEvaluationInProgress: boolean;

    @Output()
    questionUpdated = new EventEmitter();
    @Output()
    questionDeleted = new EventEmitter();
    /** Question move up and down are used for re-evaluate **/
    @Output()
    questionMoveUp = new EventEmitter();
    @Output()
    questionMoveDown = new EventEmitter();

    /** Ace Editor configuration constants **/
    questionEditorText = '';
    questionEditorMode = 'markdown';
    questionEditorAutoUpdate = true;
    showVisualMode: boolean;

    /** Status boolean for collapse status **/
    isQuestionCollapsed: boolean;

    /** Variables needed for the setup of editorText **/
    // equals the highest spotNr
    numberOfSpot = 1;
    // defines the first gap between text and solutions when
    firstPressed = 1;
    // has all solution options with their mapping (each spotNr)
    optionsWithID: string[] = [];

    /** For visual mode **/
    textParts: (string | null)[][];

    backupQuestion: ShortAnswerQuestion;

    constructor(
        private artemisMarkdown: ArtemisMarkdown,
        public shortAnswerQuestionUtil: ShortAnswerQuestionUtil,
        private modalService: NgbModal,
        private changeDetector: ChangeDetectorRef,
    ) {}

    ngOnInit(): void {
        /** Create question backup for resets. We convert it first to JSON and then back to make sure we get a real copy of the object **/
        this.backupQuestion = JSON.parse(JSON.stringify(this.question));

        /** We create now the structure on how to display the text of the question
         * 1. The question text is split at every new line. The first element of the array would be then the first line of the question text.
         * 2. Now each line of the question text will be divided into each word (we used whitespace as separator).
         * Note: As the spot tag ( e.g. [-spot 1] ) has in between a whitespace we use regex to not take whitespaces as a separator in between
         * these [ ] brackets.
         * **/
        const textForEachLine = this.question.text!.split(/\n+/g);
        this.textParts = textForEachLine.map(t => t.split(/\s+(?![^[]]*])/g));

        /** Assign status booleans and strings **/
        this.showVisualMode = false;
        this.isQuestionCollapsed = false;
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
     * @function ngAfterViewInit
     * @desc Setup the question editor
     */
    ngAfterViewInit(): void {
        if (!this.reEvaluationInProgress) {
            requestAnimationFrame(this.setupQuestionEditor.bind(this));
        }
    }

    /**
     * @function setupQuestionEditor
     * @desc Set up Question text editor
     */
    setupQuestionEditor(): void {
        // Sets the counter to the highest spotNr and generates solution options with their mapping (each spotNr)
        this.numberOfSpot = this.question.spots.length + 1;

        // Default editor settings for inline markup editor
        this.questionEditor.setTheme('chrome');
        this.questionEditor.getEditor().renderer.setShowGutter(false);
        this.questionEditor.getEditor().renderer.setPadding(10);
        this.questionEditor.getEditor().renderer.setScrollMargin(8, 8);
        this.questionEditor.getEditor().setHighlightActiveLine(false);
        this.questionEditor.getEditor().setShowPrintMargin(false);

        // Generate markdown from question and show result in editor
        this.questionEditorText = this.generateMarkdown();
        this.questionEditor.getEditor().clearSelection();

        // Register the onBlur listener
        this.questionEditor.getEditor().on(
            'blur',
            () => {
                // Parse the markdown in the editor and update question accordingly
                this.parseMarkdown(this.questionEditorText);
                this.questionUpdated.emit();
            },
            this,
        );
        this.changeDetector.detectChanges();
    }

    /**
     * @function setOptionsWithID
     * @desc Set up of all solution option with their mapping (spotNr)
     */
    setOptionsWithID() {
        this.optionsWithID = [];
        for (const solution of this.question.solutions) {
            let spotsForSolution: ShortAnswerSpot[] = [];
            let option = '[-option ';
            let firstSolution = true;
            spotsForSolution = this.shortAnswerQuestionUtil.getAllSpotsForSolutions(this.question.correctMappings, solution);

            for (const spotForSolution of spotsForSolution) {
                if (spotForSolution === undefined) {
                    break;
                }
                if (firstSolution) {
                    option += this.question.spots.filter(spot => this.shortAnswerQuestionUtil.isSameSpot(spot, spotForSolution))[0].spotNr;
                    firstSolution = false;
                } else {
                    option += ',' + this.question.spots.filter(spot => this.shortAnswerQuestionUtil.isSameSpot(spot, spotForSolution))[0].spotNr;
                }
            }
            option += ']';
            this.optionsWithID.push(option);
        }
    }

    /**
     * @function generateMarkdown
     * @desc Generate the markdown text for this question
     * 1. First the question text, hint, and explanation are added using ArtemisMarkdown
     * 2. After an empty line, the solutions are added
     * 3. For each solution: text is added using ArtemisMarkdown
     */
    generateMarkdown(): string {
        this.setOptionsWithID();
        const markdownText =
            this.artemisMarkdown.generateTextHintExplanation(this.question) +
            '\n\n\n' +
            this.question.solutions.map((solution, index) => this.optionsWithID[index] + ' ' + solution.text.trim()).join('\n');
        return markdownText;
    }

    /**
     * @function parseMarkdown
     * @param text {string} the markdown text to parse
     * @desc Parse the markdown and apply the result to the question's data
     * The markdown rules are as follows:
     *
     * 1. Text is split at [-option
     *    => The first part (any text before the first [-option ) is the question text
     * 2. The questionText is split further at [-spot to determine all spots and spotNr.
     * 3. The question text is split into text, hint, and explanation using ArtemisMarkdown
     * 4. For every solution (Parts after each "[-option " and "]":
     *    4.a) Same treatment as the question text for text, hint, and explanation
     *    4.b) Is used to create the mappings
     *
     * Note: Existing IDs for solutions and spots are reused in the original order.
     */
    parseMarkdown(text: string): void {
        // First split up by "[-option " tag and seperate first part of the split as text and second part as solutionParts
        const questionParts = text.split(/\[-option /g);
        const questionText = questionParts[0];

        // Split into spots to generated this structure: {"1","2","3"}
        const spotParts = questionText
            .split(/\[-spot/g)
            .map(splitText => splitText.split(/\]/g))
            .slice(1)
            .map(sliceText => sliceText[0]);

        // Split new created Array by "]" to generate this structure: {"1,2", " SolutionText"}
        const solutionParts = questionParts.map(questionPart => questionPart.split(/\]/g)).slice(1);

        // Split question into main text, hint and explanation
        this.artemisMarkdown.parseTextHintExplanation(questionText, this.question);

        // Extract existing solutions IDs
        const existingSolutionIDs = this.question.solutions.filter(solution => solution.id != null).map(solution => solution.id);
        this.question.solutions = [];
        this.question.correctMappings = [];

        // Extract existing spot IDs
        const existingSpotIDs = this.question.spots.filter(spot => spot.id != null).map(spot => spot.id);
        this.question.spots = [];

        // setup spots
        for (const spotID of spotParts) {
            const spot = new ShortAnswerSpot();
            spot.width = 15;

            // Assign existing ID if available
            if (this.question.spots.length < existingSpotIDs.length) {
                spot.id = existingSpotIDs[this.question.spots.length];
            }
            spot.spotNr = +spotID.trim();
            this.question.spots.push(spot);
        }

        // Work on solution
        for (const solutionText of solutionParts) {
            // Find the box (text in-between the parts)
            const solution = new ShortAnswerSolution();
            solution.text = solutionText[1].trim();

            // Assign existing ID if available
            if (this.question.solutions.length < existingSolutionIDs.length) {
                solution.id = existingSolutionIDs[this.question.solutions.length];
            }
            this.question.solutions.push(solution);

            // create mapping according to this structure: {spot(s), solution} -> {"1,2", " SolutionText"}
            this.createMapping(solutionText[0], solution);
        }
    }

    /**
     * This function creates the mapping. It differentiates 2 cases one solution To one spot (case 1) and
     * one solution to many spots.
     */
    private createMapping(spots: string, solution: ShortAnswerSolution) {
        const spotIds = spots.split(',').map(Number);

        for (const id of spotIds) {
            const spotForMapping = this.question.spots.find(spot => spot.spotNr === id)!;
            this.question.correctMappings.push(new ShortAnswerMapping(spotForMapping, solution));
        }
    }

    /**
     * This function opens the modal for the help dialog.
     */
    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }

    /**
     * @function addSpotAtCursor
     * @desc Add the markdown for a spot at the current cursor location and
     * an option connected to the spot below the last visible row
     */
    addSpotAtCursor(): void {
        const editor = this.questionEditor.getEditor();
        const optionText = editor.getCopyText();
        const addedText = '[-spot ' + this.numberOfSpot + ']';
        editor.focus();
        editor.insert(addedText);
        editor.moveCursorTo(editor.getLastVisibleRow() + this.numberOfSpot, Number.POSITIVE_INFINITY);
        this.addOptionToSpot(editor, this.numberOfSpot, optionText, this.firstPressed);

        this.numberOfSpot++;
        this.firstPressed++;
    }

    /**
     * add the markdown for a solution option below the last visible row, which is connected to a spot in the given editor
     *
     * @param editor {object} the editor into which the solution option markdown will be inserted
     */
    addOptionToSpot(editor: any, numberOfSpot: number, optionText: string, firstPressed: number) {
        let addedText: string;
        if (numberOfSpot === 1 && firstPressed === 1) {
            addedText = '\n\n\n[-option ' + numberOfSpot + '] ' + optionText;
        } else {
            addedText = '\n[-option ' + numberOfSpot + '] ' + optionText;
        }
        editor.focus();
        editor.clearSelection();
        editor.insert(addedText);
    }

    /**
     * @function addOption
     * @desc Add the markdown for a solution option below the last visible row
     */
    addOption(): void {
        const editor = this.questionEditor.getEditor();
        let addedText: string;
        if (this.firstPressed === 1) {
            addedText = '\n\n\n[-option #] Please enter here one answer option and do not forget to replace # with a number';
        } else {
            addedText = '\n[-option #] Please enter here one answer option and do not forget to replace # with a number';
        }
        editor.clearSelection();
        editor.moveCursorTo(editor.getLastVisibleRow(), Number.POSITIVE_INFINITY);
        editor.insert(addedText);
        const range = editor.selection.getRange();
        range.setStart(range.start.row, 12);
        editor.selection.setRange(range);

        this.firstPressed++;
    }

    /**
     * For Visual Mode
     */

    /**
     * @function addSpotAtCursorVisualMode
     * @desc Add a input field on the current selected location and add the solution option accordingly
     */
    addSpotAtCursorVisualMode(): void {
        // check if selection is on the correct div
        const wrapperDiv = document.getElementById('test')!;
        const selection = window.getSelection()!;
        const child = selection.anchorNode;

        if (!wrapperDiv.contains(child)) {
            return;
        }

        const editor = this.questionEditor.getEditor();
        // ID 'element-row-column' is divided into array of [row, column]
        const selectedTextRowColumn = selection.focusNode!.parentNode!.parentElement!.id.split('-').slice(1);

        if (selectedTextRowColumn.length === 0) {
            return;
        }

        // get the right range for text with markdown
        const range = selection.getRangeAt(0);
        const preCaretRange = range.cloneRange();
        const element = selection.focusNode!.parentNode!.parentElement!.firstElementChild!;
        preCaretRange.selectNodeContents(element);
        preCaretRange.setEnd(range.endContainer, range.endOffset);

        // We need the innerHTML from the content of preCaretRange to create the overall startOfRage of the selected element
        const container = document.createElement('div');
        container.appendChild(preCaretRange.cloneContents());
        const htmlContent = container.innerHTML;

        const startOfRange = this.artemisMarkdown.markdownForHtml(htmlContent).length - selection.toString().length;
        const endOfRange = startOfRange + selection.toString().length;

        const markedTextHTML = this.textParts[selectedTextRowColumn[0]][selectedTextRowColumn[1]];
        const markedText = this.artemisMarkdown.markdownForHtml(markedTextHTML).substring(startOfRange, endOfRange);

        // split text before first option tag
        const questionText = editor
            .getValue()
            .split(/\[-option /g)[0]
            .trim();
        this.textParts = this.shortAnswerQuestionUtil.divideQuestionTextIntoTextParts(questionText);
        const textOfSelectedRow = this.textParts[selectedTextRowColumn[0]][selectedTextRowColumn[1]];
        this.textParts[selectedTextRowColumn[0]][selectedTextRowColumn[1]] =
            textOfSelectedRow.substring(0, startOfRange) + '[-spot ' + this.numberOfSpot + ']' + textOfSelectedRow.substring(endOfRange);

        // recreation of question text from array and update textParts and parse textParts to html
        this.question.text = this.textParts.map(textPart => textPart.join(' ')).join('\n');
        const textParts = this.shortAnswerQuestionUtil.divideQuestionTextIntoTextParts(this.question.text);
        this.textParts = this.shortAnswerQuestionUtil.transformTextPartsIntoHTML(textParts, this.artemisMarkdown);
        editor.setValue(this.generateMarkdown());
        editor.moveCursorTo(editor.getLastVisibleRow() + this.numberOfSpot, Number.POSITIVE_INFINITY);
        this.addOptionToSpot(editor, this.numberOfSpot, markedText, this.firstPressed);
        this.parseMarkdown(editor.getValue());

        this.numberOfSpot++;
        this.firstPressed++;

        this.questionUpdated.emit();
    }

    /**
     * @function addTextSolution
     * @desc Add an empty Text solution to the question
     */
    addTextSolution(): void {
        // Add solution to question
        if (!this.question.solutions) {
            this.question.solutions = [];
        }
        const solution = new ShortAnswerSolution();
        solution.text = 'Please enter here your text';
        this.question.solutions.push(solution);
        this.questionEditorText = this.generateMarkdown();
        this.questionUpdated.emit();
    }

    /**
     * @function deleteSolution
     * @desc Delete the solution from the question
     * @param solutionToDelete {object} the solution that should be deleted
     */
    deleteSolution(solutionToDelete: ShortAnswerSolution): void {
        this.question.solutions = this.question.solutions.filter(solution => solution !== solutionToDelete);
        this.deleteMappingsForSolution(solutionToDelete);
        this.questionEditorText = this.generateMarkdown();
    }

    /**
     * @function onDragDrop
     * @desc React to a solution being dropped on a spot
     * @param spot {object} the spot involved
     * @param dragEvent {object} the solution involved (may be a copy at this point)
     */
    onDragDrop(spot: ShortAnswerSpot, dragEvent: any): void {
        let dragItem = dragEvent.dragData;
        // Replace dragItem with original (because it may be a copy)
        dragItem = this.question.solutions.find(originalDragItem => (dragItem.id ? originalDragItem.id === dragItem.id : originalDragItem.tempID === dragItem.tempID));

        if (!dragItem) {
            // Drag item was not found in question => do nothing
            return;
        }

        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }

        // Check if this mapping already exists
        if (
            !this.question.correctMappings.some(
                existingMapping =>
                    this.shortAnswerQuestionUtil.isSameSpot(existingMapping.spot, spot) && this.shortAnswerQuestionUtil.isSameSolution(existingMapping.solution, dragItem),
            )
        ) {
            this.deleteMapping(this.getMappingsForSolution(dragItem).filter(mapping => mapping.spot === undefined)[0]);
            // Mapping doesn't exit yet => add this mapping
            const saMapping = new ShortAnswerMapping(spot, dragItem);
            this.question.correctMappings.push(saMapping);

            // Notify parent of changes
            this.questionUpdated.emit();
        }
        this.questionEditorText = this.generateMarkdown();
    }

    /**
     * @function getMappingIndex
     * @desc Get the mapping index for the given mapping
     * @param mapping {object} the mapping we want to get an index for
     * @return {number} the index of the mapping (starting with 1), or 0 if unassigned
     */
    getMappingIndex(mapping: ShortAnswerMapping): number {
        const visitedSpots: ShortAnswerSpot[] = [];
        // Save reference to this due to nested some calls
        const that = this;
        if (
            this.question.correctMappings.some(function(correctMapping) {
                if (
                    !visitedSpots.some((spot: ShortAnswerSpot) => {
                        return that.shortAnswerQuestionUtil.isSameSpot(spot, correctMapping.spot);
                    })
                ) {
                    visitedSpots.push(correctMapping.spot);
                }
                return that.shortAnswerQuestionUtil.isSameSpot(correctMapping.spot, mapping.spot);
            })
        ) {
            return visitedSpots.length;
        } else {
            return 0;
        }
    }

    /**
     * @function getMappingsForSolution
     * @desc Get all mappings that involve the given solution
     * @param solution {object} the solution for which we want to get all mappings
     * @return {Array} all mappings that belong to the given solution
     */
    getMappingsForSolution(solution: ShortAnswerSolution): ShortAnswerMapping[] {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        return (
            this.question.correctMappings
                .filter(mapping => this.shortAnswerQuestionUtil.isSameSolution(mapping.solution, solution))
                /** Moved the sorting from the template to the function call*/
                .sort((m1, m2) => this.getMappingIndex(m1) - this.getMappingIndex(m2))
        );
    }

    /**
     * @function deleteMappingsForSolution
     * @desc Delete all mappings for the given solution
     * @param solution {object} the solution for which we want to delete all mappings
     */
    deleteMappingsForSolution(solution: ShortAnswerSolution): void {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        this.question.correctMappings = this.question.correctMappings.filter(mapping => !this.shortAnswerQuestionUtil.isSameSolution(mapping.solution, solution));
    }

    /**
     * @function deleteMapping
     * @desc Delete the given mapping from the question
     * @param mappingToDelete {object} the mapping to delete
     */
    deleteMapping(mappingToDelete: ShortAnswerMapping): void {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        this.question.correctMappings = this.question.correctMappings.filter(mapping => mapping !== mappingToDelete);
        this.questionEditorText = this.generateMarkdown();
    }

    /**
     * @function deleteQuestion
     * @desc Delete this question from the quiz
     */
    deleteQuestion(): void {
        this.questionDeleted.emit();
    }

    /**
     * @function togglePreview
     * @desc Toggles the preview in the template
     */
    togglePreview(): void {
        this.showVisualMode = !this.showVisualMode;
        const textParts = this.shortAnswerQuestionUtil.divideQuestionTextIntoTextParts(this.question.text!);
        this.textParts = this.shortAnswerQuestionUtil.transformTextPartsIntoHTML(textParts, this.artemisMarkdown);

        this.questionEditor.getEditor().setValue(this.generateMarkdown());
        this.questionEditor.getEditor().clearSelection();
    }

    /**
     * For Re-evaluate
     */

    /**
     * @function moveUp
     * @desc Move this question one position up so that it is visible further up in the UI
     */
    moveUp() {
        this.questionMoveUp.emit();
    }

    /**
     * @function moveDown
     * @desc Move this question one position down so that it is visible further down in the UI
     */
    moveDown() {
        this.questionMoveDown.emit();
    }

    /**
     * @function
     * @desc Resets the question title by using the title of the backupQuestion (which has the original title of the question)
     */
    resetQuestionTitle() {
        this.question.title = this.backupQuestion.title;
    }

    /**
     * @function resetQuestionText
     * @desc Resets the question text by using the text of the backupQuestion (which has the original text of the question)
     */
    resetQuestionText() {
        this.question.text = this.backupQuestion.text;
        this.question.spots = JSON.parse(JSON.stringify(this.backupQuestion.spots));
        // split on every whitespace. !!!only exception: [-spot 1] is not split!!! for more details see description in ngOnInit.
        const textForEachLine = this.question.text!.split(/\n+/g);
        this.textParts = textForEachLine.map(t => t.split(/\s+(?![^[]]*])/g));
        this.question.explanation = this.backupQuestion.explanation;
        this.question.hint = this.backupQuestion.hint;
    }

    /**
     * @function resetQuestion
     * @desc Resets the whole question by using the backupQuestion (which is the original question)
     */
    resetQuestion() {
        this.resetQuestionTitle();
        this.question.invalid = this.backupQuestion.invalid;
        this.question.randomizeOrder = this.backupQuestion.randomizeOrder;
        this.question.scoringType = this.backupQuestion.scoringType;
        this.question.solutions = JSON.parse(JSON.stringify(this.backupQuestion.solutions));
        this.question.correctMappings = JSON.parse(JSON.stringify(this.backupQuestion.correctMappings));
        this.question.spots = JSON.parse(JSON.stringify(this.backupQuestion.spots));
        this.resetQuestionText();
    }

    /**
     * @function resetSpot
     * @desc Resets the spot by using the spot of the backupQuestion (which has the original spot of the question)
     * @param spot {spot} the spot, which will be reset
     */
    resetSpot(spot: ShortAnswerSpot): void {
        // Find matching spot in backupQuestion
        const backupSpot = this.backupQuestion.spots.find(currentSpot => currentSpot.id === spot.id)!;
        // Find current index of our spot
        const spotIndex = this.question.spots.indexOf(spot);
        // Remove current spot at given index and insert the backup at the same position
        this.question.spots.splice(spotIndex, 1);
        this.question.spots.splice(spotIndex, 0, backupSpot);
    }

    /**
     * @function deleteSpot
     * @desc Delete the given spot by filtering every spot except the spot to be delete
     * @param spotToDelete {object} the spot to delete
     */
    deleteSpot(spotToDelete: ShortAnswerSpot): void {
        this.question.spots = this.question.spots.filter(spot => spot !== spotToDelete);
        this.deleteMappingsForSpot(spotToDelete);

        // split on every whitespace. !!!only exception: [-spot 1] is not split!!! for more details see description in ngOnInit.
        const textForEachLine = this.question.text!.split(/\n+/g);
        this.textParts = textForEachLine.map(t => t.split(/\s+(?![^[]]*])/g));

        this.textParts = this.textParts.map(part => part.filter(text => text !== '[-spot ' + spotToDelete.spotNr + ']'));

        this.question.text = this.textParts.map(textPart => textPart.join(' ')).join('\n');
    }

    /**
     * @function deleteMappingsForSpot
     * @desc Delete all mappings for the given spot by filtering all mappings which do not include the spot
     * @param spot {object} the spot for which we want to delete all mappings
     */
    deleteMappingsForSpot(spot: ShortAnswerSpot): void {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        this.question.correctMappings = this.question.correctMappings.filter(mapping => !this.shortAnswerQuestionUtil.isSameSpot(mapping.spot, spot));
    }

    /**
     * @function setQuestionText
     * @desc sets the new text as question.text and updates the UI (through textParts)
     * @param id
     */
    setQuestionText(id: string): void {
        const rowColumn: string[] = id.split('-').slice(1);
        this.textParts[rowColumn[0]][rowColumn[1]] = (<HTMLInputElement>document.getElementById(id)).value;
        this.question.text = this.textParts.map(textPart => textPart.join(' ')).join('\n');
        // split on every whitespace. !!!only exception: [-spot 1] is not split!!! for more details see description in ngOnInit.
        const textForEachLine = this.question.text.split(/\n+/g);
        this.textParts = textForEachLine.map(t => t.split(/\s+(?![^[]]*])/g));
    }

    /**
     * @function prepareForSave
     * @desc reset the question and calls the parsing method of the markdown editor
     */
    prepareForSave(): void {}
}
