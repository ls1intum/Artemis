import {
    AfterViewInit,
    Component,
    ElementRef,
    EventEmitter,
    Input,
    OnInit,
    Output,
    SimpleChanges,
    ViewChild,
    OnChanges
} from '@angular/core';
import { DragAndDropQuestion } from '../../../entities/drag-and-drop-question';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { DragAndDropQuestionUtil } from '../../../components/util/drag-and-drop-question-util.service';
import { FileUploaderService } from '../../../shared/http/file-uploader.service';
import { DropLocation } from '../../../entities/drop-location';
import { DragItem } from '../../../entities/drag-item';
import { DragAndDropMapping } from '../../../entities/drag-and-drop-mapping';
import { Option } from '../../../entities/quiz-exercise/quiz-exercise-interfaces';
import { DragAndDropMouseEvent } from '../../../entities/drag-item/drag-and-drop-mouse-event.class';
import { DragState } from '../../../entities/drag-item/drag-state.enum';
import { AceEditorComponent } from 'ng2-ace-editor';
import * as $ from 'jquery';
import 'brace/theme/chrome';
import 'brace/mode/markdown';

@Component({
    selector: 'jhi-edit-drag-and-drop-question',
    templateUrl: './edit-drag-and-drop-question.component.html',
    providers: [ArtemisMarkdown, DragAndDropQuestionUtil]
})
export class EditDragAndDropQuestionComponent implements OnInit, OnChanges, AfterViewInit {
    @ViewChild('questionEditor')
    private questionEditor: AceEditorComponent;
    @ViewChild('clickLayer')
    private clickLayer: ElementRef;

    @Input()
    question: DragAndDropQuestion;
    @Input()
    questionIndex: number;

    @Output()
    questionUpdated = new EventEmitter<object>();
    @Output()
    questionDeleted = new EventEmitter<object>();
    /** Question move up and down are used for re-evaluate **/
    @Output()
    questionMoveUp = new EventEmitter<object>();
    @Output()
    questionMoveDown = new EventEmitter<object>();

    /** Ace Editor configuration constants **/
    questionEditorText = '';
    questionEditorMode = 'markdown';
    questionEditorAutoUpdate = true;

    backupQuestion: DragAndDropQuestion;

    dragItemPicture: string;
    backgroundFile: Blob | File;
    dragItemFile: Blob | File;

    dropAllowed = false;

    showPreview: boolean;
    isUploadingBackgroundFile: boolean;
    isUploadingDragItemFile: boolean;

    /**
     * Keep track of what the current drag action is doing
     * @type {number}
     */
    draggingState = DragState.NONE;

    /**
     * Keep track of the currently dragged drop location
     */
    currentDropLocation: DropLocation;

    /**
     * Keep track of the current mouse location
     * @type {DragAndDropMouseEvent}
     */
    mouse: DragAndDropMouseEvent;

    scoringTypeOptions: Option[] = [new Option('0', 'All or Nothing'), new Option('1', 'Proportional with Penalty')];

    constructor(
        private artemisMarkdown: ArtemisMarkdown,
        private dragAndDropQuestionUtil: DragAndDropQuestionUtil,
        private fileUploaderService: FileUploaderService
    ) {}

    ngOnInit(): void {
        /** Create question backup for resets **/
        this.backupQuestion = Object.assign({}, this.question);

        /** Assign status booleans **/
        this.showPreview = false;
        this.isUploadingBackgroundFile = false;
        this.isUploadingDragItemFile = false;

        /** Initialize DropLocation and MouseEvent objects **/
        this.currentDropLocation = new DropLocation();
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
    }

    ngAfterViewInit(): void {
        // Setup the editor
        requestAnimationFrame(this.setupQuestionEditor.bind(this));
    }

    /**
     * @function setupQuestionEditor
     * @desc Set up Question text editor
     */
    setupQuestionEditor() {
        this.questionEditor.setTheme('chrome');
        this.questionEditor.getEditor().renderer.setShowGutter(false);
        this.questionEditor.getEditor().renderer.setPadding(10);
        this.questionEditor.getEditor().renderer.setScrollMargin(8, 8);
        this.questionEditor.getEditor().setHighlightActiveLine(false);
        this.questionEditor.getEditor().setShowPrintMargin(false);

        // Generate markdown from question and show result in editor
        this.questionEditorText = this.artemisMarkdown.generateTextHintExplanation(this.question);
        this.questionEditor.getEditor().clearSelection();

        this.questionEditor.getEditor().on(
            'blur',
            () => {
                // Parse the markdown in the editor and update question accordingly
                this.artemisMarkdown.parseTextHintExplanation(this.questionEditorText, this.question);
                this.questionUpdated.emit();
            },
            this
        );
    }

    /**
     * Handles drag-available UI
     */
    drag() {
        this.dropAllowed = true;
    }

    /**
     * Handles drag-available UI
     */
    drop() {
        this.dropAllowed = false;
    }

    /**
     * @function addHintAtCursor
     * @desc Add the markdown for a hint at the current cursor location
     */
    addHintAtCursor() {
        this.artemisMarkdown.addHintAtCursor(this.questionEditor.getEditor());
    }

    /**
     * @function addExplanationAtCursor
     * @desc Add the markdown for an explanation at the current cursor location
     */
    addExplanationAtCursor() {
        this.artemisMarkdown.addExplanationAtCursor(this.questionEditor.getEditor());
    }

    /**
     * @function setBackgroundFile
     * @param $event {object} Event object which contains the uploaded file
     */
    setBackgroundFile($event: any) {
        if ($event.target.files.length) {
            const fileList: FileList = $event.target.files;
            this.backgroundFile = fileList[0];
        }
    }

    /**
     * @function uploadBackground
     * @desc Upload the selected file (from "Upload Background") and use it for the question's backgroundFilePath
     */
    uploadBackground() {
        const file = this.backgroundFile;

        this.isUploadingBackgroundFile = true;
        this.fileUploaderService.uploadFile(file, file['name']).then(
            result => {
                this.question.backgroundFilePath = result.path;
                this.isUploadingBackgroundFile = false;
                this.backgroundFile = null;
            },
            error => {
                console.error('Error during file upload in uploadBackground()', error.message);
                this.isUploadingBackgroundFile = false;
                this.backgroundFile = null;
            }
        );
    }

    /**
     * @function mouseMove
     * @desc React to mousemove events on the entire page to update:
     * - mouse object (always)
     * - current drop location (only while dragging)
     * @param e {object} Mouse move event
     */
    mouseMove(e: any) {
        // Update mouse x and y value
        const event: MouseEvent = e || window.event; // Moz || IE
        const backgroundElement = this.clickLayer.nativeElement;
        const jQueryBackgroundElement = $('.click-layer');
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
                case DragState.RESIZE_BOTH:
                    // Update current drop location's position and size
                    this.currentDropLocation.posX = Math.round((200 * Math.min(this.mouse.x, this.mouse.startX)) / backgroundWidth);
                    this.currentDropLocation.posY = Math.round((200 * Math.min(this.mouse.y, this.mouse.startY)) / backgroundHeight);
                    this.currentDropLocation.width = Math.round((200 * Math.abs(this.mouse.x - this.mouse.startX)) / backgroundWidth);
                    this.currentDropLocation.height = Math.round((200 * Math.abs(this.mouse.y - this.mouse.startY)) / backgroundHeight);
                    break;
                case DragState.MOVE:
                    // update current drop location's position
                    this.currentDropLocation.posX = Math.round(
                        Math.min(
                            Math.max(0, (200 * (this.mouse.x + this.mouse.offsetX)) / backgroundWidth),
                            200 - this.currentDropLocation.width
                        )
                    );
                    this.currentDropLocation.posY = Math.round(
                        Math.min(
                            Math.max(0, (200 * (this.mouse.y + this.mouse.offsetY)) / backgroundHeight),
                            200 - this.currentDropLocation.height
                        )
                    );
                    break;
                case DragState.RESIZE_X:
                    // Update current drop location's position and size (only x-axis)
                    this.currentDropLocation.posX = Math.round((200 * Math.min(this.mouse.x, this.mouse.startX)) / backgroundWidth);
                    this.currentDropLocation.width = Math.round((200 * Math.abs(this.mouse.x - this.mouse.startX)) / backgroundWidth);
                    break;
                case DragState.RESIZE_Y:
                    // update current drop location's position and size (only y-axis)
                    this.currentDropLocation.posY = Math.round((200 * Math.min(this.mouse.y, this.mouse.startY)) / backgroundHeight);
                    this.currentDropLocation.height = Math.round((200 * Math.abs(this.mouse.y - this.mouse.startY)) / backgroundHeight);
                    break;
            }
        }
    }

    /**
     * @function mouseUp
     * @desc React to mouseup events to finish dragging operations
     */
    mouseUp() {
        if (this.draggingState !== DragState.NONE) {
            switch (this.draggingState) {
                case DragState.CREATE:
                    const jQueryBackgroundElement = $('.click-layer');
                    const backgroundWidth = jQueryBackgroundElement.width();
                    const backgroundHeight = jQueryBackgroundElement.height();
                    if (
                        (this.currentDropLocation.width / 200) * backgroundWidth < 14 &&
                        (this.currentDropLocation.height / 200) * backgroundHeight < 14
                    ) {
                        // Remove drop Location if too small (assume it was an accidental click/drag),
                        this.deleteDropLocation(this.currentDropLocation);
                    } else {
                        // Notify parent of new drop location
                        this.questionUpdated.emit();
                    }
                    break;
                case DragState.MOVE:
                case DragState.RESIZE_BOTH:
                case DragState.RESIZE_X:
                case DragState.RESIZE_Y:
                    // Notify parent of changed drop location
                    this.questionUpdated.emit();
                    break;
            }
        }
        // Update state
        this.draggingState = DragState.NONE;
        this.currentDropLocation = null;
    }

    /**
     * @function backgroundMouseDown
     * @desc React to mouse down events on the background to start dragging
     */
    backgroundMouseDown() {
        if (this.question.backgroundFilePath && this.draggingState === DragState.NONE) {
            // Save current mouse position as starting position
            this.mouse.startX = this.mouse.x;
            this.mouse.startY = this.mouse.y;

            // Create new drop location
            this.currentDropLocation = new DropLocation();
            this.currentDropLocation.tempID = this.pseudoRandomLong();
            this.currentDropLocation.posX = this.mouse.x;
            this.currentDropLocation.posY = this.mouse.y;
            this.currentDropLocation.width = 0;
            this.currentDropLocation.height = 0;

            // Add drop location to question
            if (!this.question.dropLocations) {
                this.question.dropLocations = [];
            }
            this.question.dropLocations.push(this.currentDropLocation);

            // Update state
            this.draggingState = DragState.CREATE;
        }
    }

    /**
     * @function dropLocationMouseDown
     * @desc React to mousedown events on a drop location to start moving it
     * @param dropLocation {object} the drop location to move
     */
    dropLocationMouseDown(dropLocation: DropLocation) {
        if (this.draggingState === DragState.NONE) {
            const jQueryBackgroundElement = $('.click-layer');
            const backgroundWidth = jQueryBackgroundElement.width();
            const backgroundHeight = jQueryBackgroundElement.height();

            const dropLocationX = (dropLocation.posX / 200) * backgroundWidth;
            const dropLocationY = (dropLocation.posY / 200) * backgroundHeight;

            // Save offset of mouse in drop location
            this.mouse.offsetX = dropLocationX - this.mouse.x;
            this.mouse.offsetY = dropLocationY - this.mouse.y;

            // Update state
            this.currentDropLocation = dropLocation;
            this.draggingState = DragState.MOVE;
        }
    }

    /**
     * @function deleteDropLocation
     * @desc Delete the given drop location
     * @param dropLocationToDelete {object} the drop location to delete
     */
    deleteDropLocation(dropLocationToDelete: DropLocation) {
        this.question.dropLocations = this.question.dropLocations.filter(dropLocation => dropLocation !== dropLocationToDelete);
        this.deleteMappingsForDropLocation(dropLocationToDelete);
    }

    /**
     * @function duplicateDropLocation
     * @desc Add an identical drop location to the question
     * @param dropLocation {object} the drop location to duplicate
     */
    duplicateDropLocation(dropLocation: DropLocation) {
        const duplicatedDropLocation = new DropLocation();
        duplicatedDropLocation.tempID = this.pseudoRandomLong();
        duplicatedDropLocation.posX =
            dropLocation.posX + dropLocation.width < 197 ? dropLocation.posX + 3 : Math.max(0, dropLocation.posX - 3);
        duplicatedDropLocation.posY =
            dropLocation.posY + dropLocation.height < 197 ? dropLocation.posY + 3 : Math.max(0, dropLocation.posY - 3);
        duplicatedDropLocation.width = dropLocation.width;
        duplicatedDropLocation.height = dropLocation.height;
        this.question.dropLocations.push(duplicatedDropLocation);
    }

    /**
     * @function resizeMouseDown
     * @desc React to mousedown events on the resize handles to start resizing the drop location
     * @param dropLocation {object} the drop location that will be resized
     * @param resizeLocationY {string} 'top', 'middle' or 'bottom'
     * @param resizeLocationX {string} 'left', 'center' or 'right'
     */
    resizeMouseDown(dropLocation: DropLocation, resizeLocationY: string, resizeLocationX: string) {
        if (this.draggingState === DragState.NONE) {
            const backgroundWidth = this.clickLayer.nativeElement.offsetWidth;
            const backgroundHeight = this.clickLayer.nativeElement.offsetHeight;

            // Update state
            this.draggingState = DragState.RESIZE_BOTH; // Default is both, will be overwritten later, if needed
            this.currentDropLocation = dropLocation;

            switch (resizeLocationY) {
                case 'top':
                    // Use opposite end as startY
                    this.mouse.startY = ((dropLocation.posY + dropLocation.height) / 200) * backgroundHeight;
                    break;
                case 'middle':
                    // Limit to x-axis, startY will not be used
                    this.draggingState = DragState.RESIZE_X;
                    break;
                case 'bottom':
                    // Use opposite end as startY
                    this.mouse.startY = (dropLocation.posY / 200) * backgroundHeight;
                    break;
            }

            switch (resizeLocationX) {
                case 'left':
                    // Use opposite end as startX
                    this.mouse.startX = ((dropLocation.posX + dropLocation.width) / 200) * backgroundWidth;
                    break;
                case 'center':
                    // Limit to y-axis, startX will not be used
                    this.draggingState = DragState.RESIZE_Y;
                    break;
                case 'right':
                    // Use opposite end as startX
                    this.mouse.startX = (dropLocation.posX / 200) * backgroundWidth;
                    break;
            }
        }
    }

    /**
     * @function addTextDragItem
     * @desc Add an empty Text Drag Item to the question
     */
    addTextDragItem() {
        // Add drag item to question
        if (!this.question.dragItems) {
            this.question.dragItems = [];
        }
        const dragItem = new DragItem();
        dragItem.tempID = this.pseudoRandomLong();
        dragItem.text = 'Text';
        this.question.dragItems.push(dragItem);
        this.questionUpdated.emit();
    }

    /**
     * @function setDragItemFile
     * @param $event {object} Event object which contains the uploaded file
     */
    setDragItemFile($event: any) {
        if ($event.target.files.length) {
            const fileList: FileList = $event.target.files;
            this.dragItemFile = fileList[0];
        }
    }

    /**
     * @function uploadDragItem
     * @desc Add a Picture Drag Item with the selected file as its picture to the question
     */
    uploadDragItem() {
        const file = this.dragItemFile;

        this.isUploadingDragItemFile = true;
        this.fileUploaderService.uploadFile(file, file['name']).then(
            result => {
                // Add drag item to question
                if (!this.question.dragItems) {
                    this.question.dragItems = [];
                }
                const dragItem = new DragItem();
                dragItem.tempID = this.pseudoRandomLong();
                dragItem.pictureFilePath = result.path;
                this.question.dragItems.push(dragItem);
                this.questionUpdated.emit();
                this.isUploadingDragItemFile = false;
                this.dragItemFile = null;
            },
            error => {
                console.error('Error during file upload in uploadDragItem()', error.message);
                this.isUploadingDragItemFile = false;
                this.dragItemFile = null;
            }
        );
    }

    /**
     * @function uploadPictureForDragItemChange
     * @desc Upload a Picture for Drag Item Change with the selected file as its picture
     */
    uploadPictureForDragItemChange() {
        const file = this.dragItemFile;

        this.isUploadingDragItemFile = true;
        this.fileUploaderService.uploadFile(file, file['name']).then(
            result => {
                this.dragItemPicture = result.path;
                this.questionUpdated.emit();
                this.isUploadingDragItemFile = false;
                this.dragItemFile = null;
            },
            error => {
                console.error('Error during file upload in uploadPictureForDragItemChange()', error.message);
                this.isUploadingDragItemFile = false;
                this.dragItemFile = null;
            }
        );
    }

    /**
     * @function deleteDragItem
     * @desc Delete the drag item from the question
     * @param dragItemToDelete {object} the drag item that should be deleted
     */
    deleteDragItem(dragItemToDelete: DragItem) {
        this.question.dragItems = this.question.dragItems.filter(dragItem => dragItem !== dragItemToDelete);
        this.deleteMappingsForDragItem(dragItemToDelete);
    }

    /**
     * @function onDragDrop
     * @desc React to a drag item being dropped on a drop location
     * @param dropLocation {object} the drop location involved
     * @param dragEvent {object} the drag item involved (may be a copy at this point)
     */
    onDragDrop(dropLocation: DropLocation, dragEvent: any) {
        let dragItem = dragEvent.dragData;
        // Replace dragItem with original (because it may be a copy)
        dragItem = this.question.dragItems.find(
            originalDragItem => (dragItem.id ? originalDragItem.id === dragItem.id : originalDragItem.tempID === dragItem.tempID)
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
                    this.dragAndDropQuestionUtil.isSameDropLocation(existingMapping.dropLocation, dropLocation) &&
                    this.dragAndDropQuestionUtil.isSameDragItem(existingMapping.dragItem, dragItem)
            )
        ) {
            // Mapping doesn't exit yet => add this mapping
            const dndMapping = new DragAndDropMapping();
            dndMapping.dropLocation = dropLocation;
            dndMapping.dragItem = dragItem;
            this.question.correctMappings.push(dndMapping);

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
    getMappingIndex(mapping: DragAndDropMapping) {
        const visitedDropLocations: DropLocation[] = [];
        // Save reference to this due to nested some calls
        const that = this;
        if (
            this.question.correctMappings.some(function(correctMapping) {
                if (
                    !visitedDropLocations.some((dropLocation: DropLocation) => {
                        return that.dragAndDropQuestionUtil.isSameDropLocation(dropLocation, correctMapping.dropLocation);
                    })
                ) {
                    visitedDropLocations.push(correctMapping.dropLocation);
                }
                return that.dragAndDropQuestionUtil.isSameDropLocation(correctMapping.dropLocation, mapping.dropLocation);
            })
        ) {
            return visitedDropLocations.length;
        } else {
            return 0;
        }
    }

    /**
     * @function getMappingsForDropLocation
     * @desc Get all mappings that involve the given drop location
     * @param dropLocation {object} the drop location for which we want to get all mappings
     * @return {Array} all mappings that belong to the given drop location
     */
    getMappingsForDropLocation(dropLocation: DropLocation) {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        return this.question.correctMappings.filter(mapping =>
            this.dragAndDropQuestionUtil.isSameDropLocation(mapping.dropLocation, dropLocation)
        );
    }

    /**
     * @function getMappingsForDragItem
     * @desc Get all mappings that involve the given drag item
     * @param dragItem {object} the drag item for which we want to get all mappings
     * @return {Array} all mappings that belong to the given drag item
     */
    getMappingsForDragItem(dragItem: DragItem) {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        return (
            this.question.correctMappings
                .filter(mapping => this.dragAndDropQuestionUtil.isSameDragItem(mapping.dragItem, dragItem))
                /** Moved the sorting from the template to the function call **/
                .sort((m1, m2) => this.getMappingIndex(m1) - this.getMappingIndex(m2))
        );
    }

    /**
     * @function deleteMappingsForDropLocation
     * @desc Delete all mappings for the given drop location
     * @param dropLocation {object} the drop location for which we want to delete all mappings
     */
    deleteMappingsForDropLocation(dropLocation: DropLocation) {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        this.question.correctMappings = this.question.correctMappings.filter(
            mapping => !this.dragAndDropQuestionUtil.isSameDropLocation(mapping.dropLocation, dropLocation)
        );
    }

    /**
     * @function deleteMappingsForDragItem
     * @desc Delete all mappings for the given drag item
     * @param dragItem {object} the drag item for which we want to delete all mappings
     */
    deleteMappingsForDragItem(dragItem: DragItem) {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        this.question.correctMappings = this.question.correctMappings.filter(
            mapping => !this.dragAndDropQuestionUtil.isSameDragItem(mapping.dragItem, dragItem)
        );
    }

    /**
     * @function deleteMapping
     * @desc Delete the given mapping from the question
     * @param mappingToDelete {object} the mapping to delete
     */
    deleteMapping(mappingToDelete: DragAndDropMapping) {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        this.question.correctMappings = this.question.correctMappings.filter(mapping => mapping !== mappingToDelete);
    }

    /**
     * @function moveUpQuestion
     * @desc Move this question one position up
     */
    moveUpQuestion() {
        this.questionMoveUp.emit();
    }

    /**
     * @function moveDownQuestion
     * @desc Move this question one position down
     */
    moveDownQuestion() {
        this.questionMoveDown.emit();
    }

    /**
     * @function deleteQuestion
     * @desc Delete this question from the quiz
     */
    deleteQuestion() {
        this.questionDeleted.emit();
    }

    /**
     * @function changeToTextDragItem
     * @desc Change Picture-Drag-Item to Text-Drag-Item with text: 'Text'
     * @param dragItem {dragItem} the dragItem, which will be changed
     */
    changeToTextDragItem(dragItem: DragItem) {
        dragItem.pictureFilePath = null;
        dragItem.text = 'Text';
    }

    /**
     * @function changeToPictureDragItem
     * @desc Change Text-Drag-Item to Picture-Drag-Item with PictureFile: this.dragItemFile
     * @param dragItem {dragItem} the dragItem, which will be changed
     */
    changeToPictureDragItem(dragItem: DragItem) {
        const file = this.dragItemFile;

        this.isUploadingDragItemFile = true;
        this.fileUploaderService.uploadFile(file, file['name']).then(
            result => {
                this.dragItemPicture = result.path;
                this.questionUpdated.emit();
                this.isUploadingDragItemFile = false;
                if (this.dragItemPicture != null) {
                    dragItem.text = null;
                    dragItem.pictureFilePath = this.dragItemPicture;
                }
            },
            error => {
                console.error('Error during file upload in changeToPictureDragItem()', error.message);
                this.isUploadingDragItemFile = false;
                this.dragItemFile = null;
            }
        );
    }

    /**
     * @function resetQuestionTitle
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
        this.resetBackground();
        this.question.dropLocations = this.backupQuestion.dropLocations;
        this.question.dragItems = this.backupQuestion.dragItems;
        this.question.correctMappings = this.backupQuestion.correctMappings;
        this.resetQuestionText();
    }

    /**
     * @function resetBackground
     * @desc Resets background-picture
     */
    resetBackground() {
        this.question.backgroundFilePath = this.backupQuestion.backgroundFilePath;
        this.backgroundFile = null;
        this.isUploadingBackgroundFile = false;
    }

    /**
     * @function resetDropLocation
     * @desc Resets the dropLocation
     * @param dropLocation {dropLocation} the dropLocation, which will be reset
     */
    resetDropLocation(dropLocation: DropLocation) {
        for (const backupDropLocation of this.backupQuestion.dropLocations) {
            if (backupDropLocation.id === dropLocation.id) {
                // Find correct answer if they have another order
                this.question.dropLocations[this.question.dropLocations.indexOf(dropLocation)] = backupDropLocation;
                dropLocation = backupDropLocation;
            }
        }
    }

    /**
     * @function resetDragItem
     * @desc Resets the dragItem
     * @param dragItem {dragItem} the dragItem, which will be reset
     */
    resetDragItem(dragItem: DragItem) {
        for (const backupDragItem of this.backupQuestion.dragItems) {
            if (backupDragItem.id === dragItem.id) {
                // Find correct answer if they have another order
                this.question.dragItems[this.question.dragItems.indexOf(dragItem)] = backupDragItem;
                dragItem = backupDragItem;
            }
        }
    }

    pseudoRandomLong() {
        return Math.floor(Math.random() * Number.MAX_SAFE_INTEGER);
    }

    /**
     * @function togglePreview
     * @desc Toggles the preview in the template
     */
    togglePreview() {
        this.showPreview = !this.showPreview;
    }
}
