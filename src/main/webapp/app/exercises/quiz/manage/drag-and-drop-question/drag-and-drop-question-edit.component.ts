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
import { DragAndDropQuestionUtil } from 'app/exercises/quiz/shared/drag-and-drop-question-util.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { DragAndDropMouseEvent } from 'app/exercises/quiz/manage/drag-and-drop-question/drag-and-drop-mouse-event.class';
import { DragState } from 'app/entities/quiz/drag-state.enum';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HintCommand } from 'app/shared/markdown-editor/domainCommands/hint.command';
import { ExplanationCommand } from 'app/shared/markdown-editor/domainCommands/explanation.command';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { DomainCommand } from 'app/shared/markdown-editor/domainCommands/domainCommand';
import { QuizQuestionEdit } from 'app/exercises/quiz/manage/quiz-question-edit.interface';
import { cloneDeep } from 'lodash-es';
import { round } from 'app/shared/util/utils';
import { MAX_SIZE_UNIT } from 'app/exercises/quiz/manage/apollon-diagrams/exercise-generation/quiz-exercise-generator';
import { debounceTime, filter } from 'rxjs/operators';
import { ImageLoadingStatus, SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { generateExerciseHintExplanation } from 'app/shared/util/markdown.util';
import {
    faAngleDown,
    faAngleRight,
    faBan,
    faBars,
    faChevronDown,
    faChevronUp,
    faCopy,
    faEye,
    faFont,
    faPencilAlt,
    faPlus,
    faTrash,
    faUndo,
    faUnlink,
    faUpload,
} from '@fortawesome/free-solid-svg-icons';
import { faFileImage } from '@fortawesome/free-regular-svg-icons';
import { CdkDragDrop } from '@angular/cdk/drag-drop';

@Component({
    selector: 'jhi-drag-and-drop-question-edit',
    templateUrl: './drag-and-drop-question-edit.component.html',
    providers: [DragAndDropQuestionUtil],
    styleUrls: ['./drag-and-drop-question-edit.component.scss', '../quiz-exercise.scss', '../../shared/quiz.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class DragAndDropQuestionEditComponent implements OnInit, OnChanges, AfterViewInit, QuizQuestionEdit {
    @ViewChild('clickLayer', { static: false }) private clickLayer: ElementRef;
    @ViewChild('backgroundImage', { static: false }) private backgroundImage: SecuredImageComponent;
    @ViewChild('markdownEditor', { static: false }) private markdownEditor: MarkdownEditorComponent;

    @Input() question: DragAndDropQuestion;
    @Input() questionIndex: number;
    @Input() reEvaluationInProgress: boolean;

    @Output() questionUpdated = new EventEmitter();
    @Output() questionDeleted = new EventEmitter();
    /** Question move up and down are used for re-evaluate **/
    @Output() questionMoveUp = new EventEmitter();
    @Output() questionMoveDown = new EventEmitter();

    /** Ace Editor configuration constants **/
    questionEditorText = '';

    backupQuestion: DragAndDropQuestion;

    dragItemPicture?: string;
    backgroundFile?: File;
    backgroundFileName: string;
    backgroundFilePath: string;
    dragItemFile?: File;
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
    currentDropLocation?: DropLocation;

    /**
     * Keep track of the current mouse location
     * @type {DragAndDropMouseEvent}
     */
    mouse: DragAndDropMouseEvent;

    hintCommand = new HintCommand();
    explanationCommand = new ExplanationCommand();

    /** {array} with domainCommands that are needed for a drag and drop question **/
    dragAndDropQuestionDomainCommands: DomainCommand[] = [this.explanationCommand, this.hintCommand];

    // Icons
    faBan = faBan;
    faPlus = faPlus;
    faTrash = faTrash;
    faUndo = faUndo;
    faFont = faFont;
    faEye = faEye;
    faChevronUp = faChevronUp;
    faChevronDown = faChevronDown;
    faPencilAlt = faPencilAlt;
    faBars = faBars;
    faUnlink = faUnlink;
    faCopy = faCopy;
    farFileImage = faFileImage;
    faAngleRight = faAngleRight;
    faAngleDown = faAngleDown;
    faUpload = faUpload;

    constructor(
        private artemisMarkdown: ArtemisMarkdownService,
        private dragAndDropQuestionUtil: DragAndDropQuestionUtil,
        private modalService: NgbModal,
        private fileUploaderService: FileUploaderService,
        private changeDetector: ChangeDetectorRef,
    ) {}

    /**
     * Actions when initializing component.
     */
    ngOnInit(): void {
        // create deep copy as backup
        this.backupQuestion = cloneDeep(this.question);

        /** Assign status booleans and strings **/
        this.showPreview = false;
        this.isUploadingBackgroundFile = false;
        this.backgroundFileName = '';
        this.backgroundFilePath = '';
        this.isUploadingDragItemFile = false;
        this.dragItemFileName = '';
        this.isQuestionCollapsed = false;

        /** Initialize DropLocation and MouseEvent objects **/
        this.currentDropLocation = new DropLocation();
        this.mouse = new DragAndDropMouseEvent();
        this.questionEditorText = generateExerciseHintExplanation(this.question);
    }

    /**
     * Watch for any changes to the question model and notify listener
     * @param changes {SimpleChanges}
     */
    ngOnChanges(changes: SimpleChanges): void {
        /** Check if previousValue wasn't null to avoid firing at component initialization **/
        if (changes.question && changes.question.previousValue) {
            this.questionUpdated.emit();
        }
        /** Update backupQuestion if the question changed **/
        if (changes.question && changes.question.currentValue) {
            this.backupQuestion = cloneDeep(this.question);
        }
    }

    ngAfterViewInit(): void {
        if (this.question.backgroundFilePath) {
            this.backgroundFilePath = this.question.backgroundFilePath;
            // Trigger image render with the question background file path in order to adjust the click layer.
            setTimeout(() => {
                this.changeDetector.detectChanges();
            }, 0);
        }

        this.backgroundImage.endLoadingProcess
            .pipe(
                filter((loadingStatus) => loadingStatus === ImageLoadingStatus.SUCCESS),
                // Some time until image render. Need to wait until image width is computed.
                debounceTime(300),
            )
            .subscribe(() => this.adjustClickLayerWidth());

        // Trigger click layer width adjustment upon window resize.
        window.onresize = () => this.adjustClickLayerWidth();
    }

    /**
     * Adjusts the click-layer width to equal the background image width.
     */
    adjustClickLayerWidth() {
        // Make the background image visible upon successful image load. Initially it is set to hidden and not
        // conditionally loaded via '*ngIf' because otherwise the reference would be undefined and hence we
        // wouldn't be able to subscribe to the loading process updates.
        this.backgroundImage.element.nativeElement.style.visibility = 'visible';

        // Adjust the click layer to correspond to the area of the background image.
        this.clickLayer.nativeElement.style.width = `${this.backgroundImage.element.nativeElement.offsetWidth}px`;
        this.clickLayer.nativeElement.style.left = `${this.backgroundImage.element.nativeElement.offsetLeft}px`;
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
     * event {object} Event object which contains the uploaded file
     */
    setBackgroundFile(event: any): void {
        if (event.target.files.length) {
            const fileList: FileList = event.target.files;
            this.backgroundFile = fileList[0];
            this.backgroundFileName = this.backgroundFile.name;
        }
    }

    /**
     * Upload the selected file (from "Upload Background") and use it for the question's backgroundFilePath
     */
    uploadBackground(): void {
        const file = this.backgroundFile!;

        this.isUploadingBackgroundFile = true;
        this.fileUploaderService.uploadFile(file, file.name).then(
            (result) => {
                this.question.backgroundFilePath = result.path;
                this.isUploadingBackgroundFile = false;
                this.backgroundFile = undefined;
                this.backgroundFileName = '';
                this.backgroundFilePath = result.path!;

                // Trigger image reload.
                this.changeDetector.detectChanges();
            },
            (error) => {
                console.error('Error during file upload in uploadBackground()', error.message);
                this.isUploadingBackgroundFile = false;
                this.backgroundFile = undefined;
                this.backgroundFileName = '';
            },
        );
    }

    /**
     * React to mousemove events on the entire page to update:
     * - mouse object (always)
     * - current drop location (only while dragging)
     * @param event {object} Mouse move event
     */
    mouseMove(event: MouseEvent): void {
        // Update mouse x and y value
        const backgroundElement = this.clickLayer.nativeElement;
        const backgroundOffsetLeft = backgroundElement.getBoundingClientRect().x + window.scrollX;
        const backgroundOffsetTop = backgroundElement.getBoundingClientRect().y + window.scrollY;
        const backgroundWidth = backgroundElement.offsetWidth;
        const backgroundHeight = backgroundElement.offsetHeight;
        this.mouseMoveAction(event, backgroundOffsetLeft, backgroundOffsetTop, backgroundWidth, backgroundHeight);
    }

    private mouseMoveAction(event: MouseEvent, backgroundOffsetLeft: number, backgroundOffsetTop: number, backgroundWidth: number, backgroundHeight: number) {
        if (event.pageX) {
            this.mouse.x = event.pageX - backgroundOffsetLeft;
            this.mouse.y = event.pageY - backgroundOffsetTop;
        } else if (event.clientX) {
            this.mouse.x = event.clientX - backgroundOffsetLeft;
            this.mouse.y = event.clientY - backgroundOffsetTop;
        }
        this.mouse.x = Math.min(Math.max(0, this.mouse.x), backgroundWidth);
        this.mouse.y = Math.min(Math.max(0, this.mouse.y), backgroundHeight);

        if (this.draggingState !== DragState.NONE) {
            switch (this.draggingState) {
                case DragState.CREATE:
                case DragState.RESIZE_BOTH:
                    // Update current drop location's position and size
                    this.currentDropLocation!.posX = round((MAX_SIZE_UNIT * Math.min(this.mouse.x, this.mouse.startX)) / backgroundWidth);
                    this.currentDropLocation!.posY = round((MAX_SIZE_UNIT * Math.min(this.mouse.y, this.mouse.startY)) / backgroundHeight);
                    this.currentDropLocation!.width = round((MAX_SIZE_UNIT * Math.abs(this.mouse.x - this.mouse.startX)) / backgroundWidth);
                    this.currentDropLocation!.height = round((MAX_SIZE_UNIT * Math.abs(this.mouse.y - this.mouse.startY)) / backgroundHeight);
                    break;
                case DragState.MOVE:
                    // update current drop location's position
                    this.currentDropLocation!.posX = round(
                        Math.min(Math.max(0, (MAX_SIZE_UNIT * (this.mouse.x + this.mouse.offsetX)) / backgroundWidth), MAX_SIZE_UNIT - this.currentDropLocation!.width!),
                    );
                    this.currentDropLocation!.posY = round(
                        Math.min(Math.max(0, (MAX_SIZE_UNIT * (this.mouse.y + this.mouse.offsetY)) / backgroundHeight), MAX_SIZE_UNIT - this.currentDropLocation!.height!),
                    );
                    break;
                case DragState.RESIZE_X:
                    // Update current drop location's position and size (only x-axis)
                    this.currentDropLocation!.posX = round((MAX_SIZE_UNIT * Math.min(this.mouse.x, this.mouse.startX)) / backgroundWidth);
                    this.currentDropLocation!.width = round((MAX_SIZE_UNIT * Math.abs(this.mouse.x - this.mouse.startX)) / backgroundWidth);
                    break;
                case DragState.RESIZE_Y:
                    // update current drop location's position and size (only y-axis)
                    this.currentDropLocation!.posY = round((MAX_SIZE_UNIT * Math.min(this.mouse.y, this.mouse.startY)) / backgroundHeight);
                    this.currentDropLocation!.height = round((MAX_SIZE_UNIT * Math.abs(this.mouse.y - this.mouse.startY)) / backgroundHeight);
                    break;
            }
        }
    }

    /**
     * React to mouseup events to finish dragging operations
     */
    mouseUp(): void {
        if (this.draggingState !== DragState.NONE) {
            switch (this.draggingState) {
                case DragState.CREATE:
                    const backgroundElement = this.clickLayer.nativeElement;
                    const backgroundWidth = backgroundElement.offsetWidth;
                    const backgroundHeight = backgroundElement.offsetHeight;
                    if ((this.currentDropLocation!.width! / MAX_SIZE_UNIT) * backgroundWidth < 14 && (this.currentDropLocation!.height! / MAX_SIZE_UNIT) * backgroundHeight < 14) {
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
        this.currentDropLocation = undefined;
    }

    /**
     * React to mouse down events on the background to start dragging
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
     * React to mousedown events on a drop location to start moving it
     * @param dropLocation {object} the drop location to move
     */
    dropLocationMouseDown(dropLocation: DropLocation): void {
        if (this.draggingState === DragState.NONE) {
            const backgroundElement = this.clickLayer.nativeElement;
            const backgroundWidth = backgroundElement.offsetWidth;
            const backgroundHeight = backgroundElement.offsetHeight;

            const dropLocationX = (dropLocation.posX! / MAX_SIZE_UNIT) * backgroundWidth;
            const dropLocationY = (dropLocation.posY! / MAX_SIZE_UNIT) * backgroundHeight;

            // Save offset of mouse in drop location
            this.mouse.offsetX = dropLocationX - this.mouse.x;
            this.mouse.offsetY = dropLocationY - this.mouse.y;

            // Update state
            this.currentDropLocation = dropLocation;
            this.draggingState = DragState.MOVE;
        }
    }

    /**
     * Delete the given drop location
     * @param dropLocationToDelete {object} the drop location to delete
     */
    deleteDropLocation(dropLocationToDelete: DropLocation): void {
        this.question.dropLocations = this.question.dropLocations!.filter((dropLocation) => dropLocation !== dropLocationToDelete);
        this.deleteMappingsForDropLocation(dropLocationToDelete);
    }

    /**
     * Add an identical drop location to the question
     * @param dropLocation {object} the drop location to duplicate
     */
    duplicateDropLocation(dropLocation: DropLocation): void {
        const duplicatedDropLocation = new DropLocation();
        duplicatedDropLocation.posX = dropLocation.posX! + dropLocation.width! < 197 ? dropLocation.posX! + 3 : Math.max(0, dropLocation.posX! - 3);
        duplicatedDropLocation.posY = dropLocation.posY! + dropLocation.height! < 197 ? dropLocation.posY! + 3 : Math.max(0, dropLocation.posY! - 3);
        duplicatedDropLocation.width = dropLocation.width;
        duplicatedDropLocation.height = dropLocation.height;
        this.question.dropLocations!.push(duplicatedDropLocation);
    }

    /**
     * React to mousedown events on the resize handles to start resizing the drop location
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
                    this.mouse.startY = ((dropLocation.posY! + dropLocation.height!) / MAX_SIZE_UNIT) * backgroundHeight;
                    break;
                case 'middle':
                    // Limit to x-axis, startY will not be used
                    this.draggingState = DragState.RESIZE_X;
                    break;
                case 'bottom':
                    // Use opposite end as startY
                    this.mouse.startY = (dropLocation.posY! / MAX_SIZE_UNIT) * backgroundHeight;
                    break;
            }

            switch (resizeLocationX) {
                case 'left':
                    // Use opposite end as startX
                    this.mouse.startX = ((dropLocation.posX! + dropLocation.width!) / MAX_SIZE_UNIT) * backgroundWidth;
                    break;
                case 'center':
                    // Limit to y-axis, startX will not be used
                    this.draggingState = DragState.RESIZE_Y;
                    break;
                case 'right':
                    // Use opposite end as startX
                    this.mouse.startX = (dropLocation.posX! / MAX_SIZE_UNIT) * backgroundWidth;
                    break;
            }
        }
    }

    /**
     * Add an empty Text Drag Item to the question
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
     * Sets drag item file.
     * @param event {object} Event object which contains the uploaded file
     */
    setDragItemFile(event: any): void {
        if (event.target.files.length) {
            const fileList: FileList = event.target.files;
            this.dragItemFile = fileList[0];
            this.dragItemFileName = this.dragItemFile.name;
        }
    }

    /**
     * Add a Picture Drag Item with the selected file as its picture to the question
     */
    uploadDragItem(): void {
        const file = this.dragItemFile!;

        this.isUploadingDragItemFile = true;
        this.fileUploaderService.uploadFile(file, file.name).then(
            (result) => {
                // Add drag item to question
                if (!this.question.dragItems) {
                    this.question.dragItems = [];
                }
                const dragItem = new DragItem();
                dragItem.pictureFilePath = result.path;
                this.question.dragItems.push(dragItem);
                this.questionUpdated.emit();
                this.isUploadingDragItemFile = false;
                this.dragItemFile = undefined;
                this.dragItemFileName = '';
            },
            (error) => {
                console.error('Error during file upload in uploadDragItem()', error.message);
                this.isUploadingDragItemFile = false;
                this.dragItemFile = undefined;
                this.dragItemFileName = '';
            },
        );
    }

    /**
     * Upload a Picture for Drag Item Change with the selected file as its picture
     */
    uploadPictureForDragItemChange(): void {
        const file = this.dragItemFile!;

        this.isUploadingDragItemFile = true;
        this.fileUploaderService.uploadFile(file, file.name).then(
            (result) => {
                this.dragItemPicture = result.path;
                this.questionUpdated.emit();
                this.isUploadingDragItemFile = false;
                this.dragItemFile = undefined;
            },
            (error) => {
                console.error('Error during file upload in uploadPictureForDragItemChange()', error.message);
                this.isUploadingDragItemFile = false;
                this.dragItemFile = undefined;
            },
        );
    }

    /**
     * Delete the drag item from the question
     * @param dragItemToDelete {object} the drag item that should be deleted
     */
    deleteDragItem(dragItemToDelete: DragItem): void {
        this.question.dragItems = this.question.dragItems!.filter((dragItem) => dragItem !== dragItemToDelete);
        this.deleteMappingsForDragItem(dragItemToDelete);
    }

    /**
     * React to a drag item being dropped on a drop location
     * @param dropLocation {object} the drop location involved
     * @param dropEvent {object} an event containing the drag item involved (can be a copy at this point)
     */
    onDragDrop(dropLocation: DropLocation, dropEvent: CdkDragDrop<DragItem, DragItem>): void {
        const dragItem = dropEvent.item.data as DragItem;
        // Replace dragItem with original (because it may be a copy)
        const questionDragItem = this.question.dragItems!.find((originalDragItem) =>
            dragItem.id ? originalDragItem.id === dragItem.id : originalDragItem.tempID === dragItem.tempID,
        );

        if (!questionDragItem) {
            // Drag item was not found in question => do nothing
            return;
        }

        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }

        // Check if this mapping already exists
        if (
            !this.question.correctMappings.some(
                (existingMapping) =>
                    this.dragAndDropQuestionUtil.isSameEntityWithTempId(existingMapping.dropLocation, dropLocation) &&
                    this.dragAndDropQuestionUtil.isSameEntityWithTempId(existingMapping.dragItem, questionDragItem),
            )
        ) {
            // Mapping doesn't exit yet => add this mapping
            const dndMapping = new DragAndDropMapping(questionDragItem, dropLocation);
            this.question.correctMappings.push(dndMapping);

            // Notify parent of changes
            this.questionUpdated.emit();
        }
    }

    /**
     * Get the mapping index for the given mapping
     * @param mapping {object} the mapping we want to get an index for
     * @return {number} the index of the mapping (starting with 1), or 0 if unassigned
     */
    getMappingIndex(mapping: DragAndDropMapping): number {
        const visitedDropLocations: DropLocation[] = [];
        // Save reference to this due nested some calls
        const that = this;
        if (
            this.question.correctMappings!.some(function (correctMapping) {
                if (
                    !visitedDropLocations.some((dropLocation: DropLocation) => {
                        return that.dragAndDropQuestionUtil.isSameEntityWithTempId(dropLocation, correctMapping.dropLocation);
                    })
                ) {
                    visitedDropLocations.push(correctMapping.dropLocation!);
                }
                return that.dragAndDropQuestionUtil.isSameEntityWithTempId(correctMapping.dropLocation, mapping.dropLocation);
            })
        ) {
            return visitedDropLocations.length;
        } else {
            return 0;
        }
    }

    /**
     * Get all mappings that involve the given drop location
     * @param dropLocation {object} the drop location for which we want to get all mappings
     * @return {Array} all mappings that belong to the given drop location
     */
    getMappingsForDropLocation(dropLocation: DropLocation): DragAndDropMapping[] {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        return this.question.correctMappings.filter((mapping) => this.dragAndDropQuestionUtil.isSameEntityWithTempId(mapping.dropLocation, dropLocation));
    }

    /**
     * Get all mappings that involve the given drag item
     * @param dragItem {object} the drag item for which we want to get all mappings
     * @return {Array} all mappings that belong to the given drag item
     */
    getMappingsForDragItem(dragItem: DragItem): DragAndDropMapping[] {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        return (
            this.question.correctMappings
                .filter((mapping) => this.dragAndDropQuestionUtil.isSameEntityWithTempId(mapping.dragItem, dragItem))
                /** Moved the sorting from the template to the function call **/
                .sort((m1, m2) => this.getMappingIndex(m1) - this.getMappingIndex(m2))
        );
    }

    /**
     * Delete all mappings for the given drop location
     * @param dropLocation {object} the drop location for which we want to delete all mappings
     */
    deleteMappingsForDropLocation(dropLocation: DropLocation): void {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        this.question.correctMappings = this.question.correctMappings.filter((mapping) => !this.dragAndDropQuestionUtil.isSameEntityWithTempId(mapping.dropLocation, dropLocation));
        // Notify parent of changes
        this.questionUpdated.emit();
    }

    /**
     * Delete all mappings for the given drag item
     * @param dragItem {object} the drag item for which we want to delete all mappings
     */
    deleteMappingsForDragItem(dragItem: DragItem): void {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        this.question.correctMappings = this.question.correctMappings.filter((mapping) => !this.dragAndDropQuestionUtil.isSameEntityWithTempId(mapping.dragItem, dragItem));
        // Notify parent of changes
        this.questionUpdated.emit();
    }

    /**
     * Delete the given mapping from the question
     * @param mappingToDelete {object} the mapping to delete
     */
    deleteMapping(mappingToDelete: DragAndDropMapping): void {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        this.question.correctMappings = this.question.correctMappings.filter((mapping) => mapping !== mappingToDelete);
        // Notify parent of changes
        this.questionUpdated.emit();
    }

    /**
     * Move this question one position up
     */
    moveUpQuestion(): void {
        this.questionMoveUp.emit();
    }

    /**
     * Move this question one position down
     */
    moveDownQuestion(): void {
        this.questionMoveDown.emit();
    }

    /**
     * Delete this question from the quiz
     */
    deleteQuestion(): void {
        this.questionDeleted.emit();
    }

    /**
     * Change Picture-Drag-Item to Text-Drag-Item with text: 'Text'
     * @param dragItem {dragItem} the dragItem, which will be changed
     */
    changeToTextDragItem(dragItem: DragItem): void {
        dragItem.pictureFilePath = undefined;
        dragItem.text = 'Text';
    }

    /**
     * Change Text-Drag-Item to Picture-Drag-Item with PictureFile: this.dragItemFile
     * @param dragItem {dragItem} the dragItem, which will be changed
     */
    changeToPictureDragItem(dragItem: DragItem): void {
        const file = this.dragItemFile!;

        this.isUploadingDragItemFile = true;
        this.fileUploaderService.uploadFile(file, file.name).then(
            (response) => {
                this.dragItemPicture = response.path;
                this.questionUpdated.emit();
                this.isUploadingDragItemFile = false;
                if (this.dragItemPicture) {
                    dragItem.text = undefined;
                    dragItem.pictureFilePath = this.dragItemPicture;
                }
            },
            (error) => {
                console.error('Error during file upload in changeToPictureDragItem()', error.message);
                this.isUploadingDragItemFile = false;
                this.dragItemFile = undefined;
            },
        );
    }

    /**
     * Resets the question title
     */
    resetQuestionTitle(): void {
        this.question.title = this.backupQuestion.title;
    }

    /**
     * Resets the question text
     */
    resetQuestionText(): void {
        this.question.text = this.backupQuestion.text;
        this.question.explanation = this.backupQuestion.explanation;
        this.question.hint = this.backupQuestion.hint;
        this.questionEditorText = generateExerciseHintExplanation(this.question);
    }

    /**
     * Resets the whole question
     */
    resetQuestion(): void {
        this.question.title = this.backupQuestion.title;
        this.question.invalid = this.backupQuestion.invalid;
        this.question.randomizeOrder = this.backupQuestion.randomizeOrder;
        this.question.scoringType = this.backupQuestion.scoringType;
        this.resetBackground();
        this.question.dropLocations = cloneDeep(this.backupQuestion.dropLocations);
        this.question.dragItems = cloneDeep(this.backupQuestion.dragItems);
        this.question.correctMappings = cloneDeep(this.backupQuestion.correctMappings);
        this.resetQuestionText();
    }

    /**
     * Resets background-picture
     */
    resetBackground(): void {
        this.question.backgroundFilePath = this.backupQuestion.backgroundFilePath;
        this.backgroundFile = undefined;
        this.isUploadingBackgroundFile = false;
    }

    /**
     * Resets the dropLocation
     * @param dropLocation {dropLocation} the dropLocation, which will be reset
     */
    resetDropLocation(dropLocation: DropLocation): void {
        // Find matching DropLocation in backupQuestion
        const backupDropLocation = this.backupQuestion.dropLocations!.find((currentDL) => currentDL.id === dropLocation.id)!;
        // Find current index of our DropLocation
        const dropLocationIndex = this.question.dropLocations!.indexOf(dropLocation);
        // Remove current DropLocation at given index and insert the backup at the same position
        this.question.dropLocations!.splice(dropLocationIndex, 1);
        this.question.dropLocations!.splice(dropLocationIndex, 0, backupDropLocation);
    }

    /**
     * Resets the dragItem
     * @param dragItem {dragItem} the dragItem, which will be reset
     */
    resetDragItem(dragItem: DragItem): void {
        // Find matching DragItem in backupQuestion
        const backupDragItem = this.backupQuestion.dragItems!.find((currentDI) => currentDI.id === dragItem.id)!;
        // Find current index of our DragItem
        const dragItemIndex = this.question.dragItems!.indexOf(dragItem);
        // Remove current DragItem at given index and insert the backup at the same position
        this.question.dragItems!.splice(dragItemIndex, 1);
        this.question.dragItems!.splice(dragItemIndex, 0, backupDragItem);
    }

    /**
     * Toggles the preview in the template
     */
    togglePreview(): void {
        this.showPreview = !this.showPreview;
        this.prepareForSave();
    }

    /**
     * Detect of text changes in the markdown editor
     * 1. Parse the text in the editor to get the newest values
     * 2. Notify the parent component to check the validity of the text
     */
    changesInMarkdown(): void {
        this.prepareForSave();
        this.questionUpdated.emit();
        this.changeDetector.detectChanges();
    }

    /**
     * 1. Gets the {array} containing the text with the domainCommandIdentifier and creates a new drag and drop problem statement
     * by assigning the text according to the domainCommandIdentifiers to the drag and drop attributes.
     * (question text, explanation, hint)
     * @param domainCommands - containing markdownText with the corresponding domainCommand {DomainCommand} identifier
     */
    domainCommandsFound(domainCommands: [string, DomainCommand | null][]): void {
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
        this.question.text = undefined;
        this.question.explanation = undefined;
        this.question.hint = undefined;
    }

    /**
     * Triggers the saving process by cleaning up the question and calling the markdown parse function
     * to get the newest values in the editor to update the question attributes
     */
    prepareForSave(): void {
        this.cleanupQuestion();
        this.markdownEditor.parse();
    }
}
