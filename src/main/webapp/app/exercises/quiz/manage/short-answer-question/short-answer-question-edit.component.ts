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
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import 'brace/theme/chrome';
import 'brace/mode/markdown';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { QuizQuestionEdit } from 'app/exercises/quiz/manage/quiz-question-edit.interface';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { cloneDeep } from 'lodash-es';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { markdownForHtml } from 'app/shared/util/markdown.conversion.util';
import { generateExerciseHintExplanation, parseExerciseHintExplanation } from 'app/shared/util/markdown.util';
import { faAngleDown, faAngleRight, faBan, faBars, faChevronDown, faChevronUp, faTrash, faUndo, faUnlink } from '@fortawesome/free-solid-svg-icons';
import { MAX_QUIZ_SHORT_ANSWER_TEXT_LENGTH } from 'app/shared/constants/input.constants';

@Component({
    selector: 'jhi-short-answer-question-edit',
    templateUrl: './short-answer-question-edit.component.html',
    styleUrls: ['./short-answer-question-edit.component.scss', '../quiz-exercise.scss', '../../shared/quiz.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class ShortAnswerQuestionEditComponent implements OnInit, OnChanges, AfterViewInit, QuizQuestionEdit {
    @ViewChild('questionEditor', { static: false })
    private questionEditor: AceEditorComponent;
    @ViewChild('clickLayer', { static: false })
    private clickLayer: ElementRef;

    shortAnswerQuestion: ShortAnswerQuestion;

    @Input()
    set question(quizQuestion: QuizQuestion) {
        this.shortAnswerQuestion = quizQuestion as ShortAnswerQuestion;
    }

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

    readonly maxCharacterCount = MAX_QUIZ_SHORT_ANSWER_TEXT_LENGTH;

    /** Ace Editor configuration constants **/
    questionEditorText: any = '';
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
    textParts: (string | undefined)[][];

    backupQuestion: ShortAnswerQuestion;

    // Icons
    faBan = faBan;
    faTrash = faTrash;
    faUndo = faUndo;
    faChevronUp = faChevronUp;
    faChevronDown = faChevronDown;
    faBars = faBars;
    faUnlink = faUnlink;
    faAngleRight = faAngleRight;
    faAngleDown = faAngleDown;

    constructor(
        private artemisMarkdown: ArtemisMarkdownService,
        public shortAnswerQuestionUtil: ShortAnswerQuestionUtil,
        private modalService: NgbModal,
        private changeDetector: ChangeDetectorRef,
    ) {}

    ngOnInit(): void {
        // create deepcopy
        this.backupQuestion = cloneDeep(this.shortAnswerQuestion);

        /** We create now the structure on how to display the text of the question
         * 1. The question text is split at every new line. The first element of the array would be then the first line of the question text.
         * 2. Now each line of the question text will be divided into each word (we use whitespace and the borders of spots as separator, see {@link #regex}).
         */
        this.textParts = this.parseQuestionTextIntoTextBlocks(this.shortAnswerQuestion.text!);

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
        if (changes.question && changes.question.previousValue) {
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
     * Parses the text taken as parameter into text blocks
     * @param text the text which should be parsed
     */
    private parseQuestionTextIntoTextBlocks(text: string): string[][] {
        const returnValue: string[][] = [];
        const lineText = text.split(/\n+/g);
        lineText.forEach((line) => {
            const textParts = this.shortAnswerQuestionUtil.divideQuestionTextIntoTextParts(line)[0];
            let parsedLine: string[] = [];
            textParts.forEach((block) => {
                if (block.includes('[-spot ', 0)) {
                    parsedLine.push(block);
                } else {
                    let blockSplit = block.split(/\s+/g);
                    blockSplit = blockSplit.filter((ele) => ele !== '');
                    if (blockSplit.length > 0) {
                        parsedLine = parsedLine.concat(blockSplit);
                    }
                }
            });
            // add indentation
            if (parsedLine.length > 0) {
                const indentation = this.shortAnswerQuestionUtil.getIndentation(line);
                parsedLine[0] = indentation.concat(parsedLine[0]);
            }
            returnValue.push(parsedLine);
        });
        return returnValue;
    }

    /**
     * @function setupQuestionEditor
     * @desc Set up Question text editor
     */
    setupQuestionEditor(): void {
        // Sets the counter to the highest spotNr and generates solution options with their mapping (each spotNr)
        this.numberOfSpot = this.shortAnswerQuestion.spots!.length + 1;

        // Default editor settings for inline markup editor
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
        this.shortAnswerQuestion.solutions!.forEach((solution) => {
            let option = '[-option ';
            let firstSolution = true;
            const spotsForSolution = this.shortAnswerQuestionUtil.getAllSpotsForSolutions(this.shortAnswerQuestion.correctMappings, solution);
            spotsForSolution!.forEach((spotForSolution) => {
                if (!spotForSolution) {
                    return;
                }
                if (firstSolution) {
                    option += this.shortAnswerQuestion.spots?.filter((spot) => this.shortAnswerQuestionUtil.isSameSpot(spot, spotForSolution))[0].spotNr;
                    firstSolution = false;
                } else {
                    option += ',' + this.shortAnswerQuestion.spots?.filter((spot) => this.shortAnswerQuestionUtil.isSameSpot(spot, spotForSolution))[0].spotNr;
                }
            });
            option += ']';
            this.optionsWithID.push(option);
        });
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
            generateExerciseHintExplanation(this.shortAnswerQuestion) +
            '\n\n\n' +
            this.shortAnswerQuestion.solutions?.map((solution, index) => this.optionsWithID[index] + ' ' + solution.text!.trim()).join('\n');
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
        // First split up by "[-option " tag and separate first part of the split as text and second part as solutionParts
        const questionParts = text.split(/\[-option /g);
        const questionText = questionParts[0];

        // Split into spots to generate this structure: {"1","2","3"}
        const spotParts = questionText
            .split(/\[-spot/g)
            .map((splitText) => splitText.split(/\]/g))
            .slice(1)
            .map((sliceText) => sliceText[0]);

        // Split new created Array by "]" to generate this structure: {"1,2", " SolutionText"}
        const solutionParts = questionParts.map((questionPart) => questionPart.split(/\]/g)).slice(1);

        // Split question into main text, hint and explanation
        parseExerciseHintExplanation(questionText, this.shortAnswerQuestion);

        // Extract existing solutions IDs
        const existingSolutionIDs = this.shortAnswerQuestion.solutions!.filter((solution) => solution.id !== undefined).map((solution) => solution.id);
        this.shortAnswerQuestion.solutions = [];
        this.shortAnswerQuestion.correctMappings = [];

        // Extract existing spot IDs
        const existingSpotIDs = this.shortAnswerQuestion.spots!.filter((spot) => spot.id !== undefined).map((spot) => spot.id);
        this.shortAnswerQuestion.spots = [];

        // setup spots
        for (const spotID of spotParts) {
            const spot = new ShortAnswerSpot();
            spot.width = 15;

            // Assign existing ID if available
            if (this.shortAnswerQuestion.spots.length < existingSpotIDs.length) {
                spot.id = existingSpotIDs[this.shortAnswerQuestion.spots.length];
            }
            spot.spotNr = +spotID.trim();
            this.shortAnswerQuestion.spots.push(spot);
        }

        // Work on solution
        for (const solutionText of solutionParts) {
            // Find the box (text in-between the parts)
            const solution = new ShortAnswerSolution();
            solution.text = solutionText[1].trim();

            // Assign existing ID if available
            if (this.shortAnswerQuestion.solutions.length < existingSolutionIDs.length) {
                solution.id = existingSolutionIDs[this.shortAnswerQuestion.solutions.length];
            }
            this.shortAnswerQuestion.solutions.push(solution);

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
            const spotForMapping = this.shortAnswerQuestion.spots?.find((spot) => spot.spotNr === id)!;
            this.shortAnswerQuestion.correctMappings!.push(new ShortAnswerMapping(spotForMapping, solution));
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
     * @desc Add an input field on the current selected location and add the solution option accordingly
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

        const startOfRange = markdownForHtml(htmlContent).length - selection.toString().length;
        const endOfRange = startOfRange + selection.toString().length;

        const markedTextHTML = this.textParts[selectedTextRowColumn[0]][selectedTextRowColumn[1]];
        const markedText = markdownForHtml(markedTextHTML).substring(startOfRange, endOfRange);

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
        this.shortAnswerQuestion.text = this.textParts.map((textPart) => textPart.join(' ')).join('\n');
        const textParts = this.shortAnswerQuestionUtil.divideQuestionTextIntoTextParts(this.shortAnswerQuestion.text);
        this.textParts = this.shortAnswerQuestionUtil.transformTextPartsIntoHTML(textParts);
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
        if (!this.shortAnswerQuestion.solutions) {
            this.shortAnswerQuestion.solutions = [];
        }
        const solution = new ShortAnswerSolution();
        solution.text = 'Please enter here your text';
        this.shortAnswerQuestion.solutions.push(solution);
        this.questionEditorText = this.generateMarkdown();
        this.questionUpdated.emit();
    }

    /**
     * @function deleteSolution
     * @desc Delete the solution from the question
     * @param solutionToDelete {object} the solution that should be deleted
     */
    deleteSolution(solutionToDelete: ShortAnswerSolution): void {
        this.shortAnswerQuestion.solutions = this.shortAnswerQuestion.solutions?.filter((solution) => solution !== solutionToDelete);
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
        let dragItem = dragEvent.item.data;
        // Replace dragItem with original (because it may be a copy)
        dragItem = this.shortAnswerQuestion.solutions?.find((originalDragItem) =>
            dragItem.id ? originalDragItem.id === dragItem.id : originalDragItem.tempID === dragItem.tempID,
        );

        if (!dragItem) {
            // Drag item was not found in question => do nothing
            return;
        }

        if (!this.shortAnswerQuestion.correctMappings) {
            this.shortAnswerQuestion.correctMappings = [];
        }

        // Check if this mapping already exists
        if (
            !this.shortAnswerQuestion.correctMappings.some(
                (existingMapping) =>
                    this.shortAnswerQuestionUtil.isSameSpot(existingMapping.spot, spot) && this.shortAnswerQuestionUtil.isSameSolution(existingMapping.solution, dragItem),
            )
        ) {
            this.deleteMapping(this.getMappingsForSolution(dragItem).filter((mapping) => mapping.spot === undefined)[0]);
            // Mapping doesn't exit yet => add this mapping
            const saMapping = new ShortAnswerMapping(spot, dragItem);
            this.shortAnswerQuestion.correctMappings.push(saMapping);

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
            this.shortAnswerQuestion.correctMappings?.some((correctMapping) => {
                if (
                    !visitedSpots.some((spot: ShortAnswerSpot) => {
                        return that.shortAnswerQuestionUtil.isSameSpot(spot, correctMapping.spot);
                    })
                ) {
                    visitedSpots.push(correctMapping.spot!);
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
        if (!this.shortAnswerQuestion.correctMappings) {
            this.shortAnswerQuestion.correctMappings = [];
        }
        return (
            this.shortAnswerQuestion.correctMappings
                .filter((mapping) => this.shortAnswerQuestionUtil.isSameSolution(mapping.solution, solution))
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
        if (!this.shortAnswerQuestion.correctMappings) {
            this.shortAnswerQuestion.correctMappings = [];
        }
        this.shortAnswerQuestion.correctMappings = this.shortAnswerQuestion.correctMappings.filter(
            (mapping) => !this.shortAnswerQuestionUtil.isSameSolution(mapping.solution, solution),
        );
    }

    /**
     * @function deleteMapping
     * @desc Delete the given mapping from the question
     * @param mappingToDelete {object} the mapping to delete
     */
    deleteMapping(mappingToDelete: ShortAnswerMapping): void {
        if (!this.shortAnswerQuestion.correctMappings) {
            this.shortAnswerQuestion.correctMappings = [];
        }
        this.shortAnswerQuestion.correctMappings = this.shortAnswerQuestion.correctMappings.filter((mapping) => mapping !== mappingToDelete);
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
        const textParts = this.shortAnswerQuestionUtil.divideQuestionTextIntoTextParts(this.shortAnswerQuestion.text!);
        this.textParts = this.shortAnswerQuestionUtil.transformTextPartsIntoHTML(textParts);

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
        this.shortAnswerQuestion.title = this.backupQuestion.title;
    }

    /**
     * @function resetQuestionText
     * @desc Resets the question text by using the text of the backupQuestion (which has the original text of the question)
     */
    resetQuestionText() {
        this.shortAnswerQuestion.text = this.backupQuestion.text;
        this.shortAnswerQuestion.spots = cloneDeep(this.backupQuestion.spots);
        this.textParts = this.parseQuestionTextIntoTextBlocks(this.shortAnswerQuestion.text!);
        this.shortAnswerQuestion.explanation = this.backupQuestion.explanation;
        this.shortAnswerQuestion.hint = this.backupQuestion.hint;
    }

    /**
     * @function resetQuestion
     * @desc Resets the whole question by using the backupQuestion (which is the original question)
     */
    resetQuestion() {
        this.resetQuestionTitle();
        this.shortAnswerQuestion.invalid = this.backupQuestion.invalid;
        this.shortAnswerQuestion.randomizeOrder = this.backupQuestion.randomizeOrder;
        this.shortAnswerQuestion.scoringType = this.backupQuestion.scoringType;
        this.shortAnswerQuestion.solutions = cloneDeep(this.backupQuestion.solutions);
        this.shortAnswerQuestion.correctMappings = cloneDeep(this.backupQuestion.correctMappings);
        this.shortAnswerQuestion.spots = cloneDeep(this.backupQuestion.spots);
        this.resetQuestionText();
    }

    /**
     * @function resetSpot
     * @desc Resets the spot by using the spot of the backupQuestion (which has the original spot of the question)
     * @param spot {spot} the spot, which will be reset
     */
    resetSpot(spot: ShortAnswerSpot): void {
        // Find matching spot in backupQuestion
        const backupSpot = this.backupQuestion.spots!.find((currentSpot) => currentSpot.id === spot.id)!;
        // Find current index of our spot
        const spotIndex = this.shortAnswerQuestion.spots!.indexOf(spot);
        // Remove current spot at given index and insert the backup at the same position
        this.shortAnswerQuestion.spots!.splice(spotIndex, 1);
        this.shortAnswerQuestion.spots!.splice(spotIndex, 0, backupSpot);
    }

    /**
     * @function deleteSpot
     * @desc Delete the given spot by filtering every spot except the spot to be delete
     * @param spotToDelete {object} the spot to delete
     */
    deleteSpot(spotToDelete: ShortAnswerSpot): void {
        this.shortAnswerQuestion.spots = this.shortAnswerQuestion.spots?.filter((spot) => spot !== spotToDelete);
        this.deleteMappingsForSpot(spotToDelete);

        this.textParts = this.parseQuestionTextIntoTextBlocks(this.shortAnswerQuestion.text!);

        this.textParts = this.textParts.map((part) => part.filter((text) => !text || !text.includes('[-spot ' + spotToDelete.spotNr + ']')));

        this.shortAnswerQuestion.text = this.textParts.map((textPart) => textPart.join(' ')).join('\n');
    }

    /**
     * @function deleteMappingsForSpot
     * @desc Delete all mappings for the given spot by filtering all mappings which do not include the spot
     * @param spot {object} the spot for which we want to delete all mappings
     */
    deleteMappingsForSpot(spot: ShortAnswerSpot): void {
        if (!this.shortAnswerQuestion.correctMappings) {
            this.shortAnswerQuestion.correctMappings = [];
        }
        this.shortAnswerQuestion.correctMappings = this.shortAnswerQuestion.correctMappings.filter((mapping) => !this.shortAnswerQuestionUtil.isSameSpot(mapping.spot, spot));
    }

    /**
     * @function setQuestionText
     * @desc sets the new text as question.text and updates the UI (through textParts)
     * @param textPartId
     */
    setQuestionText(textPartId: string): void {
        const rowColumn: string[] = textPartId.split('-').slice(1);
        this.textParts[rowColumn[0]][rowColumn[1]] = (<HTMLInputElement>document.getElementById(textPartId)).value;
        this.shortAnswerQuestion.text = this.textParts.map((textPart) => textPart.join(' ')).join('\n');
        this.textParts = this.parseQuestionTextIntoTextBlocks(this.shortAnswerQuestion.text);
    }

    /**
     * @function prepareForSave
     * @desc reset the question and calls the parsing method of the markdown editor
     */
    prepareForSave(): void {}

    /**
     * @function toggleExactMatchCheckbox
     * @desc Sets the similarity value to 100 if the checkbox was checked or to 85 if it was unchecked
     * @param checked
     */
    toggleExactMatchCheckbox(checked: boolean): void {
        this.shortAnswerQuestion.similarityValue = checked ? 100 : 85;
        this.questionUpdated.emit();
    }
}
