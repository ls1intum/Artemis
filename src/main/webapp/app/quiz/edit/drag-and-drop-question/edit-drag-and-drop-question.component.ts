import { ChangeDetectorRef, Component, ElementRef, EventEmitter, HostListener, Input, OnChanges, OnInit, Output, SimpleChanges, ViewChild, ViewEncapsulation } from '@angular/core';
import { DragAndDropQuestion } from 'app/entities/drag-and-drop-question';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { DragAndDropQuestionUtil } from 'app/components/util/drag-and-drop-question-util.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { DropLocation } from 'app/entities/drop-location';
import { DragItem } from 'app/entities/drag-item';
import { DragAndDropMapping } from 'app/entities/drag-and-drop-mapping';
import { DragAndDropMouseEvent } from 'app/entities/drag-item/drag-and-drop-mouse-event.class';
import { DragState } from 'app/entities/drag-item/drag-state.enum';
import * as $ from 'jquery';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import * as TempID from 'app/quiz/edit/temp-id';
import { DomainCommand, ExplanationCommand, HintCommand } from 'app/markdown-editor/domainCommands';
import { MarkdownEditorComponent } from 'app/markdown-editor';
import { EditQuizQuestion } from 'app/quiz/edit/edit-quiz-question.interface';
import { resizeImage } from 'app/utils/drag-and-drop.utils';

@Component({
    selector: 'jhi-edit-drag-and-drop-question',
    templateUrl: './edit-drag-and-drop-question.component.html',
    providers: [ArtemisMarkdown, DragAndDropQuestionUtil],
    styleUrls: ['./edit-drag-and-drop-question.component.scss', '../edit-quiz-question.scss', '../../../quiz.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class EditDragAndDropQuestionComponent implements OnInit, OnChanges, EditQuizQuestion {
    @ViewChild('clickLayer', { static: false })
    private clickLayer: ElementRef;
    @ViewChild('markdownEditor', { static: false })
    private markdownEditor: MarkdownEditorComponent;

    @Input()
    question: DragAndDropQuestion;
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

    backupQuestion: DragAndDropQuestion;

    dragItemPicture: string;
    backgroundFile: Blob | File | null;
    backgroundFileName: string;
    dragItemFile: Blob | File | null;
    dragItemFileName: string;

    dropAllowed = false;

    showPreview: boolean;
    isUploadingBackgroundFile: boolean;
    isUploadingDragItemFile: boolean;

    /** Status boolean for collapse status **/
    isQuestionCollapsed: boolean;

    /**
     * Keep track of what the current drag action is doing
     * @type {number}
     */
    draggingState: number = DragState.NONE;

    /**
     * Keep track of the currently dragged drop location
     * @type {DropLocation}
     */
    currentDropLocation: DropLocation | null;

    /**
     * Keep track of the current mouse location
     * @type {DragAndDropMouseEvent}
     */
    mouse: DragAndDropMouseEvent;

    hintCommand = new HintCommand();
    explanationCommand = new ExplanationCommand();

    /** {array} with domainCommands that are needed for a drag and drop question **/
    dragAndDropQuestionDomainCommands: DomainCommand[] = [this.explanationCommand, this.hintCommand];

    resizeImage = resizeImage();

    constructor(
        private artemisMarkdown: ArtemisMarkdown,
        private dragAndDropQuestionUtil: DragAndDropQuestionUtil,
        private modalService: NgbModal,
        private fileUploaderService: FileUploaderService,
        private changeDetector: ChangeDetectorRef,
    ) {}

    ngOnInit(): void {
        /** Create question backup for resets **/
        this.backupQuestion = JSON.parse(JSON.stringify(this.question));

        /** Assign status booleans and strings **/
        this.showPreview = false;
        this.isUploadingBackgroundFile = false;
        this.backgroundFileName = '';
        this.isUploadingDragItemFile = false;
        this.dragItemFileName = '';
        this.isQuestionCollapsed = false;

        /** Initialize DropLocation and MouseEvent objects **/
        this.currentDropLocation = new DropLocation();
        this.mouse = new DragAndDropMouseEvent();
        this.questionEditorText = this.artemisMarkdown.generateTextHintExplanation(this.question);
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
            // this.changeDetector.detectChanges();
        }
        /** Update backupQuestion if the question changed **/
        if (changes.question && changes.question.currentValue != null) {
            this.backupQuestion = JSON.parse(JSON.stringify(this.question));
        }
    }

    @HostListener('window:resize') onResize() {
        resizeImage();
    }

    /**
     * This function opens the modal for the help dialog.
     */
    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }

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
     * @function setBackgroundFile
     * @param $event {object} Event object which contains the uploaded file
     */
    setBackgroundFile($event: any): void {
        if ($event.target.files.length) {
            const fileList: FileList = $event.target.files;
            this.backgroundFile = fileList[0];
            this.backgroundFileName = this.backgroundFile['name'];
        }
    }

    /**
     * @function uploadBackground
     * @desc Upload the selected file (from "Upload Background") and use it for the question's backgroundFilePath
     */
    uploadBackground(): void {
        const file = this.backgroundFile!;

        this.isUploadingBackgroundFile = true;
        this.fileUploaderService.uploadFile(file, file['name']).then(
            result => {
                this.question.backgroundFilePath = result.path;
                this.isUploadingBackgroundFile = false;
                this.backgroundFile = null;
                this.backgroundFileName = '';
            },
            error => {
                console.error('Error during file upload in uploadBackground()', error.message);
                this.isUploadingBackgroundFile = false;
                this.backgroundFile = null;
                this.backgroundFileName = '';
            },
        );
    }

    /**
     * @function mouseMove
     * @desc React to mousemove events on the entire page to update:
     * - mouse object (always)
     * - current drop location (only while dragging)
     * @param e {object} Mouse move event
     */
    mouseMove(e: any): void {
        // Update mouse x and y value
        const event: MouseEvent = e || window.event; // Moz || IE
        const jQueryBackgroundElement = $('.click-layer-question-' + this.questionIndex);
        const jQueryBackgroundOffset = jQueryBackgroundElement.offset()!;
        const backgroundWidth = jQueryBackgroundElement.width()!;
        const backgroundHeight = jQueryBackgroundElement.height()!;
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
                    this.currentDropLocation!.posX = Math.round((200 * Math.min(this.mouse.x, this.mouse.startX)) / backgroundWidth);
                    this.currentDropLocation!.posY = Math.round((200 * Math.min(this.mouse.y, this.mouse.startY)) / backgroundHeight);
                    this.currentDropLocation!.width = Math.round((200 * Math.abs(this.mouse.x - this.mouse.startX)) / backgroundWidth);
                    this.currentDropLocation!.height = Math.round((200 * Math.abs(this.mouse.y - this.mouse.startY)) / backgroundHeight);
                    break;
                case DragState.MOVE:
                    // update current drop location's position
                    this.currentDropLocation!.posX = Math.round(
                        Math.min(Math.max(0, (200 * (this.mouse.x + this.mouse.offsetX)) / backgroundWidth), 200 - this.currentDropLocation!.width),
                    );
                    this.currentDropLocation!.posY = Math.round(
                        Math.min(Math.max(0, (200 * (this.mouse.y + this.mouse.offsetY)) / backgroundHeight), 200 - this.currentDropLocation!.height),
                    );
                    break;
                case DragState.RESIZE_X:
                    // Update current drop location's position and size (only x-axis)
                    this.currentDropLocation!.posX = Math.round((200 * Math.min(this.mouse.x, this.mouse.startX)) / backgroundWidth);
                    this.currentDropLocation!.width = Math.round((200 * Math.abs(this.mouse.x - this.mouse.startX)) / backgroundWidth);
                    break;
                case DragState.RESIZE_Y:
                    // update current drop location's position and size (only y-axis)
                    this.currentDropLocation!.posY = Math.round((200 * Math.min(this.mouse.y, this.mouse.startY)) / backgroundHeight);
                    this.currentDropLocation!.height = Math.round((200 * Math.abs(this.mouse.y - this.mouse.startY)) / backgroundHeight);
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
                    const backgroundWidth = jQueryBackgroundElement.width()!;
                    const backgroundHeight = jQueryBackgroundElement.height()!;
                    if ((this.currentDropLocation!.width / 200) * backgroundWidth < 14 && (this.currentDropLocation!.height / 200) * backgroundHeight < 14) {
                        // Remove drop Location if too small (assume it was an accidental click/drag),
                        this.deleteDropLocation(this.currentDropLocation!);
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
    backgroundMouseDown(): void {
        if (this.question.backgroundFilePath && this.draggingState === DragState.NONE) {
            // Save current mouse position as starting position
            this.mouse.startX = this.mouse.x;
            this.mouse.startY = this.mouse.y;

            // Create new drop location
            this.currentDropLocation = new DropLocation();
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
    dropLocationMouseDown(dropLocation: DropLocation): void {
        if (this.draggingState === DragState.NONE) {
            const jQueryBackgroundElement = $('.click-layer-question-' + this.questionIndex);
            const backgroundWidth = jQueryBackgroundElement.width()!;
            const backgroundHeight = jQueryBackgroundElement.height()!;

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
    deleteDropLocation(dropLocationToDelete: DropLocation): void {
        this.question.dropLocations = this.question.dropLocations.filter(dropLocation => dropLocation !== dropLocationToDelete);
        this.deleteMappingsForDropLocation(dropLocationToDelete);
    }

    /**
     * @function duplicateDropLocation
     * @desc Add an identical drop location to the question
     * @param dropLocation {object} the drop location to duplicate
     */
    duplicateDropLocation(dropLocation: DropLocation): void {
        const duplicatedDropLocation = new DropLocation();
        duplicatedDropLocation.posX = dropLocation.posX + dropLocation.width < 197 ? dropLocation.posX + 3 : Math.max(0, dropLocation.posX - 3);
        duplicatedDropLocation.posY = dropLocation.posY + dropLocation.height < 197 ? dropLocation.posY + 3 : Math.max(0, dropLocation.posY - 3);
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
    resizeMouseDown(dropLocation: DropLocation, resizeLocationY: string, resizeLocationX: string): void {
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
    addTextDragItem(): void {
        // Add drag item to question
        if (!this.question.dragItems) {
            this.question.dragItems = [];
        }
        const dragItem = new DragItem();
        dragItem.text = 'Text';
        this.question.dragItems.push(dragItem);
        this.questionUpdated.emit();
    }

    /**
     * @function setDragItemFile
     * @param $event {object} Event object which contains the uploaded file
     */
    setDragItemFile($event: any): void {
        if ($event.target.files.length) {
            const fileList: FileList = $event.target.files;
            this.dragItemFile = fileList[0];
            this.dragItemFileName = this.dragItemFile['name'];
        }
    }

    /**
     * @function uploadDragItem
     * @desc Add a Picture Drag Item with the selected file as its picture to the question
     */
    uploadDragItem(): void {
        const file = this.dragItemFile!;

        this.isUploadingDragItemFile = true;
        this.fileUploaderService.uploadFile(file, file['name']).then(
            result => {
                // Add drag item to question
                if (!this.question.dragItems) {
                    this.question.dragItems = [];
                }
                const dragItem = new DragItem();
                dragItem.pictureFilePath = result.path;
                this.question.dragItems.push(dragItem);
                this.questionUpdated.emit();
                this.isUploadingDragItemFile = false;
                this.dragItemFile = null;
                this.dragItemFileName = '';
            },
            error => {
                console.error('Error during file upload in uploadDragItem()', error.message);
                this.isUploadingDragItemFile = false;
                this.dragItemFile = null;
                this.dragItemFileName = '';
            },
        );
    }

    /**
     * @function uploadPictureForDragItemChange
     * @desc Upload a Picture for Drag Item Change with the selected file as its picture
     */
    uploadPictureForDragItemChange(): void {
        const file = this.dragItemFile!;

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
            },
        );
    }

    /**
     * @function deleteDragItem
     * @desc Delete the drag item from the question
     * @param dragItemToDelete {object} the drag item that should be deleted
     */
    deleteDragItem(dragItemToDelete: DragItem): void {
        this.question.dragItems = this.question.dragItems.filter(dragItem => dragItem !== dragItemToDelete);
        this.deleteMappingsForDragItem(dragItemToDelete);
    }

    /**
     * @function onDragDrop
     * @desc React to a drag item being dropped on a drop location
     * @param dropLocation {object} the drop location involved
     * @param dragEvent {object} the drag item involved (may be a copy at this point)
     */
    onDragDrop(dropLocation: DropLocation, dragEvent: any): void {
        let dragItem = dragEvent.dragData;
        // Replace dragItem with original (because it may be a copy)
        dragItem = this.question.dragItems.find(originalDragItem => (dragItem.id ? originalDragItem.id === dragItem.id : originalDragItem.tempID === dragItem.tempID));

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
                    this.dragAndDropQuestionUtil.isSameDropLocation(existingMapping.dropLocation!, dropLocation) &&
                    this.dragAndDropQuestionUtil.isSameDragItem(existingMapping.dragItem!, dragItem),
            )
        ) {
            // Mapping doesn't exit yet => add this mapping
            const dndMapping = new DragAndDropMapping(dragItem, dropLocation);
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
    getMappingIndex(mapping: DragAndDropMapping): number {
        const visitedDropLocations: DropLocation[] = [];
        // Save reference to this due to nested some calls
        const that = this;
        if (
            this.question.correctMappings.some(function(correctMapping) {
                if (
                    !visitedDropLocations.some((dropLocation: DropLocation) => {
                        return that.dragAndDropQuestionUtil.isSameDropLocation(dropLocation, correctMapping.dropLocation!);
                    })
                ) {
                    visitedDropLocations.push(correctMapping.dropLocation!);
                }
                return that.dragAndDropQuestionUtil.isSameDropLocation(correctMapping.dropLocation!, mapping.dropLocation!);
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
    getMappingsForDropLocation(dropLocation: DropLocation): DragAndDropMapping[] {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        return this.question.correctMappings.filter(mapping => this.dragAndDropQuestionUtil.isSameDropLocation(mapping.dropLocation!, dropLocation));
    }

    /**
     * @function getMappingsForDragItem
     * @desc Get all mappings that involve the given drag item
     * @param dragItem {object} the drag item for which we want to get all mappings
     * @return {Array} all mappings that belong to the given drag item
     */
    getMappingsForDragItem(dragItem: DragItem): DragAndDropMapping[] {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        return (
            this.question.correctMappings
                .filter(mapping => this.dragAndDropQuestionUtil.isSameDragItem(mapping.dragItem!, dragItem))
                /** Moved the sorting from the template to the function call **/
                .sort((m1, m2) => this.getMappingIndex(m1) - this.getMappingIndex(m2))
        );
    }

    /**
     * @function deleteMappingsForDropLocation
     * @desc Delete all mappings for the given drop location
     * @param dropLocation {object} the drop location for which we want to delete all mappings
     */
    deleteMappingsForDropLocation(dropLocation: DropLocation): void {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        this.question.correctMappings = this.question.correctMappings.filter(mapping => !this.dragAndDropQuestionUtil.isSameDropLocation(mapping.dropLocation!, dropLocation));
    }

    /**
     * @function deleteMappingsForDragItem
     * @desc Delete all mappings for the given drag item
     * @param dragItem {object} the drag item for which we want to delete all mappings
     */
    deleteMappingsForDragItem(dragItem: DragItem): void {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        this.question.correctMappings = this.question.correctMappings.filter(mapping => !this.dragAndDropQuestionUtil.isSameDragItem(mapping.dragItem!, dragItem));
    }

    /**
     * @function deleteMapping
     * @desc Delete the given mapping from the question
     * @param mappingToDelete {object} the mapping to delete
     */
    deleteMapping(mappingToDelete: DragAndDropMapping): void {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        this.question.correctMappings = this.question.correctMappings.filter(mapping => mapping !== mappingToDelete);
    }

    /**
     * @function moveUpQuestion
     * @desc Move this question one position up
     */
    moveUpQuestion(): void {
        this.questionMoveUp.emit();
    }

    /**
     * @function moveDownQuestion
     * @desc Move this question one position down
     */
    moveDownQuestion(): void {
        this.questionMoveDown.emit();
    }

    /**
     * @function deleteQuestion
     * @desc Delete this question from the quiz
     */
    deleteQuestion(): void {
        this.questionDeleted.emit();
    }

    /**
     * @function changeToTextDragItem
     * @desc Change Picture-Drag-Item to Text-Drag-Item with text: 'Text'
     * @param dragItem {dragItem} the dragItem, which will be changed
     */
    changeToTextDragItem(dragItem: DragItem): void {
        dragItem.pictureFilePath = null;
        dragItem.text = 'Text';
    }

    /**
     * @function changeToPictureDragItem
     * @desc Change Text-Drag-Item to Picture-Drag-Item with PictureFile: this.dragItemFile
     * @param dragItem {dragItem} the dragItem, which will be changed
     */
    changeToPictureDragItem(dragItem: DragItem): void {
        const file = this.dragItemFile!;

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
            },
        );
    }

    /**
     * @function resetQuestionTitle
     * @desc Resets the question title
     */
    resetQuestionTitle(): void {
        this.question.title = this.backupQuestion.title;
    }

    /**
     * @function resetQuestionText
     * @desc Resets the question text
     */
    resetQuestionText(): void {
        this.question.text = this.backupQuestion.text;
        this.question.explanation = this.backupQuestion.explanation;
        this.question.hint = this.backupQuestion.hint;
        this.questionEditorText = this.artemisMarkdown.generateTextHintExplanation(this.question);
    }

    /**
     * @function resetQuestion
     * @desc Resets the whole question
     */
    resetQuestion(): void {
        this.question.title = this.backupQuestion.title;
        this.question.invalid = this.backupQuestion.invalid;
        this.question.randomizeOrder = this.backupQuestion.randomizeOrder;
        this.question.scoringType = this.backupQuestion.scoringType;
        this.resetBackground();
        this.question.dropLocations = JSON.parse(JSON.stringify(this.backupQuestion.dropLocations));
        this.question.dragItems = JSON.parse(JSON.stringify(this.backupQuestion.dragItems));
        this.question.correctMappings = JSON.parse(JSON.stringify(this.backupQuestion.correctMappings));
        this.resetQuestionText();
    }

    /**
     * @function resetBackground
     * @desc Resets background-picture
     */
    resetBackground(): void {
        this.question.backgroundFilePath = this.backupQuestion.backgroundFilePath;
        this.backgroundFile = null;
        this.isUploadingBackgroundFile = false;
    }

    /**
     * @function resetDropLocation
     * @desc Resets the dropLocation
     * @param dropLocation {dropLocation} the dropLocation, which will be reset
     */
    resetDropLocation(dropLocation: DropLocation): void {
        // Find matching DropLocation in backupQuestion
        const backupDropLocation = this.backupQuestion.dropLocations.find(currentDL => currentDL.id === dropLocation.id)!;
        // Find current index of our DropLocation
        const dropLocationIndex = this.question.dropLocations.indexOf(dropLocation);
        // Remove current DropLocation at given index and insert the backup at the same position
        this.question.dropLocations.splice(dropLocationIndex, 1);
        this.question.dropLocations.splice(dropLocationIndex, 0, backupDropLocation);
    }

    /**
     * @function resetDragItem
     * @desc Resets the dragItem
     * @param dragItem {dragItem} the dragItem, which will be reset
     */
    resetDragItem(dragItem: DragItem): void {
        // Find matching DragItem in backupQuestion
        const backupDragItem = this.backupQuestion.dragItems.find(currentDI => currentDI.id === dragItem.id)!;
        // Find current index of our DragItem
        const dragItemIndex = this.question.dragItems.indexOf(dragItem);
        // Remove current DragItem at given index and insert the backup at the same position
        this.question.dragItems.splice(dragItemIndex, 1);
        this.question.dragItems.splice(dragItemIndex, 0, backupDragItem);
    }

    /**
     * @function togglePreview
     * @desc Toggles the preview in the template
     */
    togglePreview(): void {
        this.showPreview = !this.showPreview;
        resizeImage();
        this.prepareForSave();
    }

    /**
     * @function changesInMarkdown
     * @desc Detect of text changes in the markdown editor
     *      1. Notify the parent component to check the validity of the text
     *      2. Parse the text in the editor to get the newest values
     */
    changesInMarkdown(): void {
        this.questionUpdated.emit();
        this.changeDetector.detectChanges();
        this.prepareForSave();
    }

    /**
     * @function domainCommandsFound
     * @desc 1. Gets the {array} containing the text with the domainCommandIdentifier and creates a new drag and drop problem statement
     *       by assigning the text according to the domainCommandIdentifiers to the drag and drop attributes.
     *       (question text, explanation, hint)
     * @param {array} containing markdownText with the corresponding domainCommand {DomainCommand} identifier
     */
    domainCommandsFound(domainCommands: [string, DomainCommand][]): void {
        this.cleanupQuestion();
        for (const [text, command] of domainCommands) {
            if (command === null && text.length > 0) {
                this.question.text = text;
            }
            if (command instanceof ExplanationCommand) {
                this.question.explanation = text;
            } else if (command instanceof HintCommand) {
                this.question.hint = text;
            }
        }
    }

    /**
     * @function cleanupQuestion
     * @desc Clear the question to avoid double assignments of one attribute
     */
    private cleanupQuestion() {
        this.question.text = null;
        this.question.explanation = null;
        this.question.hint = null;
    }

    /**
     * @function prepareForSave
     * @desc triggers the saving process by cleaning up the question and calling the markdown parse function
     *       to get the newest values in the editor to update the question attributes
     */
    prepareForSave(): void {
        this.cleanupQuestion();
        this.markdownEditor.parse();
    }
}
