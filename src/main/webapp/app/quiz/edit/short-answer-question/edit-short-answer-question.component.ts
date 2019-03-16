import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, ViewChild } from '@angular/core';
import { ShortAnswerQuestion } from '../../../entities/short-answer-question';
import { ShortAnswerSpot } from '../../../entities/short-answer-spot';
import { ShortAnswerSolution } from '../../../entities/short-answer-solution';
import { ShortAnswerMapping } from '../../../entities/short-answer-mapping';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { AceEditorComponent } from 'ng2-ace-editor';
import 'brace/theme/chrome';
import 'brace/mode/markdown';
import { ShortAnswerQuestionUtil } from 'app/components/util/short-answer-question-util.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import * as TempID from 'app/quiz/edit/temp-id';

@Component({
    selector: 'jhi-edit-short-answer-question',
    templateUrl: './edit-short-answer-question.component.html',
    providers: [ArtemisMarkdown]
})
export class EditShortAnswerQuestionComponent implements OnInit, OnChanges, AfterViewInit {
    @ViewChild('questionEditor')
    private questionEditor: AceEditorComponent;
    @ViewChild('clickLayer')
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
    showPreview: boolean;

    /** Status boolean for collapse status **/
    isQuestionCollapsed: boolean;

    /** Variables needed for the setup of editorText **/
    // equals the highest spotNr
    numberOfSpot = 1;
    // defines the first gap between text and solutions when
    firstPressed = 1;
    // has all solution options with their mapping (each spotNr)
    optionsWithID: string [] = [];

    /** For visual mode **/
    textParts: String [][];

    backupQuestion: ShortAnswerQuestion;

    constructor(
        private artemisMarkdown: ArtemisMarkdown,
        private shortAnswerQuestionUtil: ShortAnswerQuestionUtil,
        private modalService: NgbModal
    ) {}

    ngOnInit(): void {
        /** Create question backup for resets **/
        this.backupQuestion = JSON.parse(JSON.stringify(this.question));
        this.textParts = this.question.text.split(/\n+/g).map(t => t.split(/\s+(?![^[]]*])/g));

        /** Assign status booleans and strings **/
        this.showPreview = false;
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
        /** Update backupQuestion if the question changed **/
        if (changes.question && changes.question.currentValue != null) {
            this.backupQuestion = JSON.parse(JSON.stringify(this.question));
        }
    }

    /**
     * @function ngAfterViewInit
     * @desc Setup the question editor
     */
    ngAfterViewInit(): void {
        requestAnimationFrame(this.setupQuestionEditor.bind(this));
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
            this
        );
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
            this.question.solutions
                .map((solution, index) => this.optionsWithID[index] + ' ' + solution.text.trim())
                .join('\n');
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
            spot.tempID = TempID.generate();
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
            solution.tempID = TempID.generate();
            solution.text = solutionText[1].trim();

            // Assign existing ID if available
            if (this.question.solutions.length < existingSolutionIDs.length) {
                solution.id = existingSolutionIDs[this.question.solutions.length];
            }
            this.question.solutions.push(solution);

            // create mapping according to this structure: {spot(s), solution} -> {"1,2", " SolutionText"}
            this.createMapping(solutionText, solution);
        }
    }

    /**
     * This function creates the mapping. It differentiates 2 cases one solution To one spot (case 1) and
     * one solution to many spots (default)
     */
    createMapping(solutionText: string[], solution: ShortAnswerSolution) {
        switch (solutionText[0].trim().length) {
            case 1: {
                const spotForMapping = this.question.spots.filter(spot => spot.spotNr === +solutionText[0])[0];
                this.question.correctMappings.push(new ShortAnswerMapping(spotForMapping, solution));
                break;
            }
            default: {
                const spotsID = solutionText[0].split(',');
                for (const spotID of spotsID) {
                    const spotForMapping = this.question.spots.filter(spot => spot.spotNr === +spotID[0])[0];
                    this.question.correctMappings.push(new ShortAnswerMapping(spotForMapping, solution));
                }
                break;
            }
        }
    }

    /**
     * This function opens the modal for the help dialog.
     */
    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
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
        const wrapperDiv = document.getElementById('test');
        const child = window.getSelection().baseNode;

        if (!wrapperDiv.contains(child)) {
            return;
        }

        const editor = this.questionEditor.getEditor();
        // ID 'element-row-column' is divided into array of [row, column]
        const selectedTextRowColumn = window.getSelection().focusNode.parentElement.id.split('-').slice(1);
        const markedText = this.textParts[selectedTextRowColumn[0]][selectedTextRowColumn[1]];

        // split text before first option tag
        const questionText = editor.getValue().split(/\[-option /g)[0].trim();
        // split on every whitespace. !!!only exception: [-spot 1] is not split!!!
        this.textParts = questionText.split(/\n+/g).map((t: String) => t.split(/\s+(?![^[]]*])/g));
        this.textParts[selectedTextRowColumn[0]][selectedTextRowColumn[1]] = '[-spot ' + this.numberOfSpot + ']';
        // recreation of text from array
        this.question.text = this.textParts.map(textPart => textPart.join(' ')).join('\n');
        editor.setValue(this.generateMarkdown());

        editor.moveCursorTo(editor.getLastVisibleRow() + this.numberOfSpot, Number.POSITIVE_INFINITY);
        this.addOptionToSpot(editor, this.numberOfSpot, markedText, this.firstPressed);
        this.parseMarkdown(editor.getValue());

        this.numberOfSpot++;
        this.firstPressed++;

        this.questionUpdated.emit();
    }

    /**
     * checks if text is an input field (check for spot tag)
     * @param text
     */
    isInputField(text: string): boolean {
        return !(text.search(/\[-spot/g) === -1);
    }

    /**
     * gets just the spot number
     * @param text
     */
    getSpotNr(text: string): number {
        return +text.split(/\[-spot/g).join('').split(']').join('').trim();
    }

    /**
     * gets the spot for a specific spotNr
     * @param spotNr
     */
    getSpot(spotNr: number): ShortAnswerSpot  {
        return this.question.spots.filter(spot => spot.spotNr === spotNr)[0];
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
        solution.tempID = TempID.generate();
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
        dragItem = this.question.solutions.find(originalDragItem =>
            dragItem.id ? originalDragItem.id === dragItem.id : originalDragItem.tempID === dragItem.tempID
        );

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
                    this.shortAnswerQuestionUtil.isSameSpot(existingMapping.spot, spot) &&
                    this.shortAnswerQuestionUtil.isSameSolution(existingMapping.solution, dragItem)
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
        this.question.correctMappings = this.question.correctMappings.filter(
            mapping => !this.shortAnswerQuestionUtil.isSameSolution(mapping.solution, solution)
        );
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
       this.showPreview = !this.showPreview;
       this.textParts = this.question.text.split(/\n+/g).map(t => t.split(/\s+(?![^[]]*])/g));
       this.questionEditor.getEditor().setValue(this.generateMarkdown());
       this.questionEditor.getEditor().clearSelection();
    }

    /**
     * For Re-evaluate
     */

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
        this.question.correctMappings = JSON.parse(JSON.stringify(this.backupQuestion.correctMappings));
        this.question.spots = JSON.parse(JSON.stringify(this.backupQuestion.spots));
        this.resetQuestionText();
    }

    /**
     * @function resetDropLocation
     * @desc Resets the dropLocation
     * @param dropLocation {dropLocation} the dropLocation, which will be reset
     */
    resetSpot(spot: ShortAnswerSpot): void {
        // Find matching DropLocation in backupQuestion
        const backupSpot = this.backupQuestion.spots.find(currentSpot => currentSpot.id === spot.id);
        // Find current index of our DropLocation
        const spotIndex = this.question.spots.indexOf(spot);
        // Remove current DropLocation at given index and insert the backup at the same position
        this.question.spots.splice(spotIndex, 1);
        this.question.spots.splice(spotIndex, 0, backupSpot);
    }

    /**
     * @function resetSolution
     * @desc Resets the whole solution
     * @param solution {ShortAnswerSolution} the solution, which will be reset
     */
    resetSolution(solution: ShortAnswerSolution) {
        // Find matching solution in backupQuestion
        const backupSolution = this.backupQuestion.solutions.find(solutionBackup => solution.id === solutionBackup.id);
        // Find current index of our solution
        const solutionIndex = this.question.solutions.indexOf(solution);
        // Remove current solution at given index and insert the backup at the same position
        this.question.solutions.splice(solutionIndex, 1);
        this.question.solutions.splice(solutionIndex, 0, backupSolution);
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

    /**
     * @function deleteDropLocation
     * @desc Delete the given drop location
     * @param dropLocationToDelete {object} the drop location to delete
     */
    deleteSpot(spotToDelete: ShortAnswerSpot): void {
        this.question.spots = this.question.spots.filter(spot => spot !== spotToDelete);
        this.deleteMappingsForSpot(spotToDelete);
    }

    /**
     * @function deleteMappingsForDropLocation
     * @desc Delete all mappings for the given drop location
     * @param dropLocation {object} the drop location for which we want to delete all mappings
     */
    deleteMappingsForSpot(spot: ShortAnswerSpot): void {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        this.question.correctMappings = this.question.correctMappings.filter(
            mapping => !this.shortAnswerQuestionUtil.isSameSpot(mapping.spot, spot)
        );
    }
}
