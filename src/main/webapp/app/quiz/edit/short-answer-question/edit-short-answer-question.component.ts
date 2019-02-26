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
// for visual mode
import { DropLocation } from '../../../entities/drop-location';
import { DragAndDropMouseEvent } from '../../../entities/drag-item/drag-and-drop-mouse-event.class';
import { DragState } from '../../../entities/drag-item/drag-state.enum';

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
    backupQuestion: ShortAnswerQuestion;
    showPreview: boolean;

    /** Status boolean for collapse status **/
    isQuestionCollapsed: boolean;

    // equals the highest spotNr
    numberOfSpot = 1;
    // defines the first gap between text and solutions when
    firstPressed = 1;
    // has all solution options with their mapping (each spotNr)
    optionsWithID: string [] = [];
    // contains all spots with their spotNr
    spotsWithID = new Map<string, ShortAnswerSpot>();

    /*
    For visual mode
     */
    questionText: string;
    textWithoutSpots: string[];
    textBeforeSpots: string[];
    textAfterSpots: string[];

    /**
     * Keep track of what the current drag action is doing
     * @type {number}
     */
    draggingState: number = DragState.NONE;

    /**
     * Keep track of the currently dragged ShortAnswerSpot
     * @type {ShortAnswerSpot}
     */
    currentSpot: ShortAnswerSpot;

    /**
     * Keep track of the current mouse location
     * @type {DragAndDropMouseEvent}
     */
    mouse: DragAndDropMouseEvent;

    dropAllowed = false;

    dropLocationsForSpots: [DropLocation];

    constructor(
        private artemisMarkdown: ArtemisMarkdown,
        private shortAnswerQuestionUtil: ShortAnswerQuestionUtil,
        private modalService: NgbModal
    ) {}

    ngOnInit(): void {
        /** Create question backup for resets **/
        this.backupQuestion = JSON.parse(JSON.stringify(this.question));

        /** Assign status booleans and strings **/
        this.showPreview = false;
        this.isQuestionCollapsed = false;

        /** Initialize ShortAnswerSpot and MouseEvent objects **/
        this.currentSpot = new ShortAnswerSpot();
        this.mouse = new DragAndDropMouseEvent();
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
        this.setOptionsWithID();

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
     * @function generateMarkdown
     * @desc Generate the markdown text for this question
     * 1. First the question text, hint, and explanation are added using ArtemisMarkdown
     * 2. After an empty line, the solutions are added
     * 3. For each solution: text is added using ArtemisMarkdown
     */
    generateMarkdown(): string {
        const markdownText =
            this.artemisMarkdown.generateTextHintExplanation(this.question) +
            '\n\n\n\n' +
            this.question.solutions
                .map((solution, index) => this.optionsWithID[index] + ' ' + solution.text.trim())
                .join('\n\n');
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
            spot.tempID = this.pseudoRandomLong();
            spot.width = 15;

            // Assign existing ID if available
            if (this.question.spots.length < existingSpotIDs.length) {
                spot.id = existingSpotIDs[this.question.spots.length];
            }
            spot.spotNr = +spotID.trim();
            this.spotsWithID.set(spotID.trim(), spot);
            this.question.spots.push(spot);
        }

        // Work on solution
        for (const solutionText of solutionParts) {
            // Find the box (text in-between the parts)
            const solution = new ShortAnswerSolution();
            solution.tempID = this.pseudoRandomLong();
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
    addSpotAtCursorVisualMode(): void {

        const markedText = window.getSelection().toString()
/*
        // Add solution to question
        if (!this.question.solutions) {
            this.question.solutions = [];
        }
        const solution = new ShortAnswerSolution();
        solution.tempID = this.pseudoRandomLong();
        solution.text = markedText; */

        const editor = this.questionEditor.getEditor();
        editor.find( markedText ,{
            caseSensitive: true,
            wholeWord: true,
            range: null,
        });

        editor.replace('[-spot ' + this.numberOfSpot + ']');

        editor.moveCursorTo(editor.getLastVisibleRow() + this.numberOfSpot, Number.POSITIVE_INFINITY);
        this.addOptionToSpot(editor, this.numberOfSpot, markedText, this.firstPressed);

        this.numberOfSpot++;
        this.firstPressed++;

        this.textWithoutSpots = this.shortAnswerQuestionUtil.getTextWithoutSpots(editor.getValue().split(/\[-option /g)[0]);

        // separates the text into parts that come before the spot tag
        this.textBeforeSpots = this.textWithoutSpots.slice(0, this.textWithoutSpots.length - 1);

        // the last part that comes after the last spot tag
        this.textAfterSpots = this.textWithoutSpots.slice(this.textWithoutSpots.length - 1);

        this.parseMarkdown(editor.getValue());

        this.questionUpdated.emit();
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
            addedText = '\n\n\n\n[-option ' + numberOfSpot + '] ' + optionText;
        } else {
            addedText = '\n\n[-option ' + numberOfSpot + '] ' + optionText;
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
            addedText = '\n\n\n\n[-option #] Please enter here one answer option and do not forget to replace # with a number';
        } else {
            addedText = '\n\n[-option #] Please enter here one answer option and do not forget to replace # with a number';
        }
        editor.clearSelection();
        editor.moveCursorTo(editor.getLastVisibleRow(), Number.POSITIVE_INFINITY);
        editor.insert(addedText);
        const range = editor.selection.getRange();
        range.setStart(range.start.row, 12);
        editor.selection.setRange(range);

        this.firstPressed++;
    }

    /*
    * For visual mode
    * TODO FDE: visual mode
    * */

    /**
     * Handles drag-available UI
     */
    drag(): void {
        this.dropAllowed = true;
    }
    /**
     * Handles drag-available UI
     */
    drop(): void {
        this.dropAllowed = false;
    }

    /**
     * @function mouseMove
     * @desc React to mousemove events on the entire page to update:
     * - mouse object (always)
     * - current solution spot (only while dragging)
     * @param e {object} Mouse move event
     */
    mouseMove(e: any): void {
        // Update mouse x and y value
        const event: MouseEvent = e || window.event; // Moz || IE
        const jQueryBackgroundElement = $('.click-layer-question-' + this.questionIndex);
        const jQueryBackgroundOffset = jQueryBackgroundElement.offset();
        const backgroundWidth = jQueryBackgroundElement.width();
        const backgroundHeight = jQueryBackgroundElement.height();
        if (event.pageX) {
            // Moz
            this.mouse.x = event.pageX - jQueryBackgroundOffset.left;
            this.mouse.y = event.pageY - jQueryBackgroundOffset.top;
        } else if (event.clientX) {
            // IE
            this.mouse.x = event.clientX - jQueryBackgroundOffset.left;
            this.mouse.y = event.clientY - jQueryBackgroundOffset.top;
        }
        this.mouse.x = Math.min(Math.max(0, this.mouse.x), backgroundWidth);
        this.mouse.y = Math.min(Math.max(0, this.mouse.y), backgroundHeight);

        if (this.draggingState !== DragState.NONE) {
            switch (this.draggingState) {
                case DragState.CREATE:
                case DragState.RESIZE_X:
                    // Update current drop location's position and size (only x-axis)
                    this.currentSpot.posX = Math.round((200 * Math.min(this.mouse.x, this.mouse.startX)) / backgroundWidth);
                    this.currentSpot.width = Math.round((200 * Math.abs(this.mouse.x - this.mouse.startX)) / backgroundWidth);
                    break;
                case DragState.MOVE:
                    // update current drop location's position
                    this.currentSpot.posX = Math.round(
                        Math.min(Math.max(0, (200 * (this.mouse.x + this.mouse.offsetX)) / backgroundWidth), 200 - this.currentSpot.width)
                    );
                    this.currentSpot.posY = Math.round(
                        Math.min(
                            Math.max(0, (200 * (this.mouse.y + this.mouse.offsetY)) / backgroundHeight),
                            200 - /*this.spot.height*/ 50 //TODO: Change to actual spot height of SaSolutionsSpots (fix value)
                        )
                    );
                    break;
            }
        }
    }

    /**
     * @function mouseUp
     * @desc React to mouseup events to finish dragging operations
     */
    mouseUp(): void {
        if (this.draggingState !== DragState.NONE) {
            switch (this.draggingState) {
                case DragState.CREATE:
                    const jQueryBackgroundElement = $('.click-layer-question-' + this.questionIndex);
                    const backgroundWidth = jQueryBackgroundElement.width();
                    const backgroundHeight = jQueryBackgroundElement.height();
                    if ((this.currentSpot.width / 200) * backgroundWidth < 14) {
                        // Remove drop Location if too small (assume it was an accidental click/drag),
                        this.deleteSpot(this.currentSpot);
                    } else {
                        // Notify parent of new drop location
                        this.questionUpdated.emit();
                    }
                    break;
                case DragState.MOVE:
                case DragState.RESIZE_X:
                    // Notify parent of changed drop location
                    this.questionUpdated.emit();
                    break;
            }
        }
        // Update state
        this.draggingState = DragState.NONE;
        this.currentSpot = null;
    }

    // FDE: Maybe create DropLocation and and afterwards create Spot
    /**
     * @function backgroundMouseDown
     * @desc React to mouse down events on the background to start dragging
     */
    backgroundMouseDown(): void {
        let number = 0;
        for (let spot of this.question.spots) {
            let dropLocation = new DropLocation();
            dropLocation.height = 50;
            dropLocation.posX = 125;
            dropLocation.posY = 125;
            dropLocation.width = 50;
            dropLocation.id = number;
            this.dropLocationsForSpots.push(dropLocation);
            number++;
        }
    }

    /**
     * @function spotMouseDown
     * @desc React to mousedown events on a spot to start moving it
     * @param spot {object} the spot to move
     */
    spotMouseDown(spot: ShortAnswerSpot): void {
        if (this.draggingState === DragState.NONE) {
            const jQueryBackgroundElement = $('.click-layer-question-' + this.questionIndex);
            const backgroundWidth = jQueryBackgroundElement.width();
            const backgroundHeight = jQueryBackgroundElement.height();

            const dropLocationX = (spot.posX / 200) * backgroundWidth;
            const dropLocationY = (spot.posY / 200) * backgroundHeight;

            // Save offset of mouse in drop location
            this.mouse.offsetX = dropLocationX - this.mouse.x;
            this.mouse.offsetY = dropLocationY - this.mouse.y;

            // Update state
            this.currentSpot = spot;
            this.draggingState = DragState.MOVE;
        }
    }

    /**
     * @function deleteSpot
     * @desc Delete the given spot
     * @param spotToDelete {object} the spot to delete
     */
    deleteSpot(spotToDelete: ShortAnswerSpot): void {
        this.question.spots = this.question.spots.filter(spot => spot !== spotToDelete);
        this.deleteMappingsForSpot(spotToDelete);
    }

    /**
     * @function resizeMouseDown
     * @desc React to mousedown events on the resize handles to start resizing the spot
     * @param spot {object} the spot that will be resized
     * @param resizeLocationY {string} 'top', 'middle' or 'bottom'
     * @param resizeLocationX {string} 'left', 'center' or 'right'
     */
    resizeMouseDown(spot: ShortAnswerSpot, resizeLocationX: string): void {
        if (this.draggingState === DragState.NONE) {
            const backgroundWidth = this.clickLayer.nativeElement.offsetWidth;
            const backgroundHeight = this.clickLayer.nativeElement.offsetHeight;

            // Update state
            this.draggingState = DragState.RESIZE_X; // Default is both, will be overwritten later, if needed
            this.currentSpot = spot;

            switch (resizeLocationX) {
                case 'left':
                    // Use opposite end as startX
                    this.mouse.startX = ((spot.posX + spot.width) / 200) * backgroundWidth;
                    break;
                case 'center':
                    // Limit to y-axis, startX will not be used
                    this.draggingState = DragState.RESIZE_Y; // Why resize_y and not resize_x
                    break;
                case 'right':
                    // Use opposite end as startX
                    this.mouse.startX = (spot.posX / 200) * backgroundWidth;
                    break;
            }
        }
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
        solution.tempID = this.pseudoRandomLong();
        solution.text = 'Please enter here your text';
        this.question.solutions.push(solution);
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
            // Mapping doesn't exit yet => add this mapping
            const saMapping = new ShortAnswerMapping(spot, dragItem);
            this.question.correctMappings.push(saMapping);

            // Notify parent of changes
            this.questionUpdated.emit();
        }
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
     * @function getMappingsForSpot
     * @desc Get all mappings that involve the given spot
     * @param spot {object} the spot for which we want to get all mappings
     * @return {Array} all mappings that belong to the given spot
     */
    getMappingsForSpot(spot: ShortAnswerSpot): ShortAnswerMapping[] {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        return this.question.correctMappings.filter(mapping => this.shortAnswerQuestionUtil.isSameSpot(mapping.spot, spot));
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
     * @function deleteMappingsForSpot
     * @desc Delete all mappings for the given spot
     * @param spot {object} the spot for which we want to delete all mappings
     */
    deleteMappingsForSpot(spot: ShortAnswerSpot): void {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        this.question.correctMappings = this.question.correctMappings.filter(
            mapping => !this.shortAnswerQuestionUtil.isSameSpot(mapping.spot, spot)
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
    }

    /**
     * @function deleteQuestion
     * @desc Delete this question from the quiz
     */
    deleteQuestion(): void {
        this.questionDeleted.emit();
    }

    /**
     * @function pseudoRandomLong
     * @desc Creates a random long number value
     * @return {number} The generated long number value
     */
    pseudoRandomLong(): number {
        return Math.floor(Math.random() * Number.MAX_SAFE_INTEGER);
    }

    /**
     * @function togglePreview
     * @desc Toggles the preview in the template
     */
    togglePreview(): void {
       this.showPreview = !this.showPreview;

        // is either '' or the question in the first line
        this.questionText = this.shortAnswerQuestionUtil.firstLineOfQuestion(this.question.text);
        this.textWithoutSpots = this.shortAnswerQuestionUtil.getTextWithoutSpots(this.question.text);

        // separates the text into parts that come before the spot tag
        this.textBeforeSpots = this.textWithoutSpots.slice(0, this.textWithoutSpots.length - 1);

        // the last part that comes after the last spot tag
        this.textAfterSpots = this.textWithoutSpots.slice(this.textWithoutSpots.length - 1);
    }
}
