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
    inject,
} from '@angular/core';
import { DragAndDropQuestionUtil } from 'app/exercises/quiz/shared/drag-and-drop-question-util.service';
import { DragAndDropMouseEvent } from 'app/exercises/quiz/manage/drag-and-drop-question/drag-and-drop-mouse-event.class';
import { DragState } from 'app/entities/quiz/drag-state.enum';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
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
    faScissors,
    faTrash,
    faUndo,
    faUnlink,
    faUpload,
} from '@fortawesome/free-solid-svg-icons';
import { faFileImage } from '@fortawesome/free-regular-svg-icons';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { MAX_QUIZ_QUESTION_POINTS } from 'app/shared/constants/input.constants';
import { FileService } from 'app/shared/http/file.service';
import { QuizHintAction } from 'app/shared/monaco-editor/model/actions/quiz/quiz-hint.action';
import { QuizExplanationAction } from 'app/shared/monaco-editor/model/actions/quiz/quiz-explanation.action';
import { MarkdownEditorMonacoComponent, TextWithDomainAction } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';

@Component({
    selector: 'jhi-drag-and-drop-question-edit',
    templateUrl: './drag-and-drop-question-edit.component.html',
    providers: [DragAndDropQuestionUtil],
    styleUrls: ['./drag-and-drop-question-edit.component.scss', '../quiz-exercise.scss', '../../shared/quiz.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class DragAndDropQuestionEditComponent implements OnInit, OnChanges, AfterViewInit, QuizQuestionEdit {
    private dragAndDropQuestionUtil = inject(DragAndDropQuestionUtil);
    private modalService = inject(NgbModal);
    private changeDetector = inject(ChangeDetectorRef);
    private fileService = inject(FileService);

    @ViewChild('clickLayer', { static: false }) private clickLayer: ElementRef;
    @ViewChild('backgroundImage ', { static: false }) private backgroundImage: SecuredImageComponent;
    @ViewChild('markdownEditor', { static: false }) private markdownEditor: MarkdownEditorMonacoComponent;

    @Input() question: DragAndDropQuestion;
    @Input() questionIndex: number;
    @Input() reEvaluationInProgress: boolean;
    @Input() filePool = new Map<string, { path?: string; file: File }>();

    @Output() questionUpdated = new EventEmitter<void>();
    @Output() questionDeleted = new EventEmitter<void>();
    /** Question move up and down are used for re-evaluate **/
    @Output() questionMoveUp = new EventEmitter<void>();
    @Output() questionMoveDown = new EventEmitter<void>();
    @Output() addNewFile = new EventEmitter<{ fileName: string; path?: string; file: File }>();
    @Output() removeFile = new EventEmitter<string>();

    questionEditorText = '';
    backupQuestion: DragAndDropQuestion;
    filePreviewPaths: Map<string, string> = new Map<string, string>();
    dropAllowed = false;
    showPreview = false;
    readonly CLICK_LAYER_DIMENSION: number = 200;
    /** Status boolean for collapse status **/
    isQuestionCollapsed = false;

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

    hintAction = new QuizHintAction();
    explanationAction = new QuizExplanationAction();

    dragAndDropDomainActions = [this.explanationAction, this.hintAction];

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
    faScissors = faScissors;

    readonly MAX_POINTS = MAX_QUIZ_QUESTION_POINTS;

    /**
     * Actions when initializing component.
     */
    ngOnInit(): void {
        // create deep copy as backup
        this.backupQuestion = cloneDeep(this.question);

        /** Initialize DropLocation and MouseEvent objects **/
        this.currentDropLocation = new DropLocation();
        this.mouse = new DragAndDropMouseEvent();
        this.questionEditorText = generateExerciseHintExplanation(this.question);

        // check if question was generated with an ApollonDiagram
        if (this.question.importedFiles) {
            this.setBackgroundFile({ target: { files: [new File([this.question.importedFiles.get('diagram-background.png')!], 'diagram-background.png')] } });
            for (const dragItem of this.question.dragItems ?? []) {
                if (dragItem.pictureFilePath && this.question.importedFiles.has(dragItem.pictureFilePath)) {
                    this.changeToPictureDragItem(dragItem, {
                        target: { files: [new File([this.question.importedFiles.get(dragItem.pictureFilePath!)!], dragItem.pictureFilePath!)] },
                    });
                }
            }
        }
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

        if (!this.filePool || this.filePool.size == 0) {
            return;
        }

        this.filePool.forEach((value, fileName) => {
            if (value.path && !this.filePreviewPaths.has(fileName)) {
                this.filePreviewPaths.set(fileName, value.path);
            }
        });
    }

    ngAfterViewInit(): void {
        if (this.question.backgroundFilePath && !this.filePreviewPaths.has(this.question.backgroundFilePath)) {
            this.filePreviewPaths.set(this.question.backgroundFilePath, this.question.backgroundFilePath);
            // Trigger image render with the question background file path in order to adjust the click layer.
            setTimeout(() => {
                this.changeDetector.markForCheck();
                this.changeDetector.detectChanges();
            }, 0);
        }

        if (this.question.dragItems) {
            for (const dragItem in this.question.dragItems) {
                const path = this.question.dragItems[dragItem].pictureFilePath;
                if (path && !this.filePreviewPaths.has(path)) {
                    this.filePreviewPaths.set(path, path);
                }
            }
        }

        this.backgroundImage.endLoadingProcess
            .pipe(
                filter((loadingStatus) => loadingStatus === ImageLoadingStatus.SUCCESS),
                // Some time until image render. Need to wait until image width is computed.
                debounceTime(300),
            )
            .subscribe(() => this.adjustClickLayerWidth());
        // render import images on UI immediatly
        this.makeFileMapPreview();
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
     * This method takes the files and creates preview objects so that images
     * are rendered immediately on the UI after importing
     */
    makeFileMapPreview() {
        if (this.filePool) {
            this.filePool.forEach((value, key) => {
                this.filePreviewPaths.set(key, URL.createObjectURL(value.file));
            });
            this.changeDetector.detectChanges();
        }
    }

    /**
     * event {object} Event object which contains the uploaded file
     */
    setBackgroundFile(event: any): void {
        const fileList: FileList = event.target.files as FileList;
        if (fileList.length) {
            const file = fileList[0];
            this.setBackgroundFileFromFile(file);
        }
    }

    setBackgroundFileFromFile(file: File) {
        if (this.question.backgroundFilePath) {
            this.removeFile.emit(this.question.backgroundFilePath);
        }

        const fileName = this.fileService.getUniqueFileName(this.fileService.getExtension(file.name), this.filePool);
        this.question.backgroundFilePath = fileName;
        this.filePreviewPaths.set(fileName, URL.createObjectURL(file));
        this.addNewFile.emit({ fileName, file });
        this.changeDetector.detectChanges();
    }

    /**
     * React to mousemove events on the entire page to update:
     * - mouse object (always)
     * - current drop location (only while dragging)
     * @param event {object} Mouse move event
     */
    mouseMove(event: MouseEvent): void {
        // Update mouse x and y value
        const backgroundElement = this.clickLayer.nativeElement as HTMLElement;
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
                    const backgroundElement = this.clickLayer.nativeElement as HTMLElement;
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
            const backgroundElement = this.clickLayer.nativeElement as HTMLElement;
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
            const backgroundElement = this.clickLayer.nativeElement as HTMLElement;
            const backgroundWidth = backgroundElement.offsetWidth;
            const backgroundHeight = backgroundElement.offsetHeight;

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
     * Add a Picture Drag Item with the selected file as its picture to the question
     */
    createImageDragItem(event: any): DragItem | undefined {
        const dragItemFile = this.getFileFromEvent(event);
        if (!dragItemFile) {
            return undefined;
        }
        return this.createImageDragItemFromFile(dragItemFile);
    }

    createImageDragItemFromFile(dragItemFile: File): DragItem {
        const fileName = this.fileService.getUniqueFileName(this.fileService.getExtension(dragItemFile.name), this.filePool);
        this.addNewFile.emit({ fileName, file: dragItemFile });
        this.filePreviewPaths.set(fileName, URL.createObjectURL(dragItemFile));

        const dragItem = new DragItem();
        dragItem.pictureFilePath = fileName;
        // Add drag item to question
        if (!this.question.dragItems) {
            this.question.dragItems = [];
        }
        this.question.dragItems.push(dragItem);

        this.questionUpdated.emit();
        return dragItem;
    }

    /**
     * Delete the drag item from the question
     * @param dragItemToDelete {object} the drag item that should be deleted
     */
    deleteDragItem(dragItemToDelete: DragItem): void {
        this.question.dragItems = this.question.dragItems!.filter((dragItem) => dragItem !== dragItemToDelete);
        if (dragItemToDelete.pictureFilePath) {
            this.removeFile.emit(dragItemToDelete.pictureFilePath);
            this.filePreviewPaths.delete(dragItemToDelete.pictureFilePath);
        }
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
        if (
            this.question.correctMappings!.some((correctMapping) => {
                if (
                    !visitedDropLocations.some((dropLocation: DropLocation) => {
                        return this.dragAndDropQuestionUtil.isSameEntityWithTempId(dropLocation, correctMapping.dropLocation);
                    })
                ) {
                    visitedDropLocations.push(correctMapping.dropLocation!);
                }
                return this.dragAndDropQuestionUtil.isSameEntityWithTempId(correctMapping.dropLocation, mapping.dropLocation);
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
        this.filePreviewPaths.forEach((_, fileName) => this.removeFile.emit(fileName));
        this.questionDeleted.emit();
    }

    /**
     * Change Picture-Drag-Item to Text-Drag-Item with text: 'Text'
     * @param dragItem {dragItem} the dragItem, which will be changed
     */
    changeToTextDragItem(dragItem: DragItem): void {
        this.removeFile.emit(dragItem.pictureFilePath!);
        this.filePreviewPaths.delete(dragItem.pictureFilePath!);
        dragItem.pictureFilePath = undefined;
        dragItem.text = 'Text';
        this.questionUpdated.emit();
    }

    /**
     * Change Text-Drag-Item to Picture-Drag-Item with PictureFile: this.dragItemFile
     * @param dragItem {dragItem} the dragItem, which will be changed
     * @param event file upload event
     */
    changeToPictureDragItem(dragItem: DragItem, event: any): void {
        const dragItemFile = this.getFileFromEvent(event);
        if (!dragItemFile) {
            return;
        }

        const fileName = this.fileService.getUniqueFileName(this.fileService.getExtension(dragItemFile.name), this.filePool);

        this.addNewFile.emit({ fileName, file: dragItemFile });
        this.filePreviewPaths.set(fileName, URL.createObjectURL(dragItemFile));
        dragItem.text = undefined;
        dragItem.pictureFilePath = fileName;
        this.questionUpdated.emit();
    }

    private getFileFromEvent(event: any): File | undefined {
        const fileList = event.target.files as FileList;
        if (!fileList.length) {
            return undefined;
        }
        return fileList[0];
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
        this.removeFile.emit(this.question.backgroundFilePath!);
        this.question.backgroundFilePath = this.backupQuestion.backgroundFilePath;
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
        if (dragItem.pictureFilePath) {
            this.removeFile.emit(dragItem.pictureFilePath);
            this.filePreviewPaths.delete(dragItem.pictureFilePath);
        }
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
     * @param value the new value of the markdown editor
     */
    changesInMarkdown(value: string): void {
        this.questionEditorText = value;
        this.prepareForSave();
        this.questionUpdated.emit();
        this.changeDetector.detectChanges();
    }

    /**
     * Creates the drag and drop problem statement from the parsed markdown text, assigning the question text, explanation, and hint according to the domain actions found.
     * @param textWithDomainActions The parsed markdown text with the corresponding domain actions.
     */
    domainActionsFound(textWithDomainActions: TextWithDomainAction[]): void {
        this.cleanupQuestion();
        for (const { text, action } of textWithDomainActions) {
            if (action === undefined && text.length > 0) {
                this.question.text = text;
            }
            if (action instanceof QuizExplanationAction) {
                this.question.explanation = text;
            } else if (action instanceof QuizHintAction) {
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
        this.markdownEditor.parseMarkdown();
    }

    /**
     * Create new drag items for each drop location in the background image
     */
    getImagesFromDropLocations() {
        for (const someLocation of this.question.dropLocations!) {
            // only crop if there is not mapping to this drop location
            if (this.getMappingsForDropLocation(someLocation).length == 0) {
                const image = new Image();
                let dataUrl: string = '';
                let bgWidth;
                let bgHeight;
                image.onload = () => {
                    bgHeight = image.height;
                    bgWidth = image.width;

                    const canvas = document.createElement('canvas');
                    const context = canvas.getContext('2d');

                    if (context) {
                        // The click layer is 200x200 so it need to be rescaled to the image
                        const scalarHeight = bgHeight / this.CLICK_LAYER_DIMENSION;
                        const scalarWidth = bgWidth / this.CLICK_LAYER_DIMENSION;
                        canvas.width = someLocation.width! * scalarWidth;
                        canvas.height = someLocation.height! * scalarHeight;
                        context.drawImage(
                            image,
                            someLocation.posX! * scalarWidth,
                            someLocation.posY! * scalarHeight,
                            someLocation.width! * scalarWidth,
                            someLocation.height! * scalarHeight,
                            0,
                            0,
                            someLocation.width! * scalarWidth,
                            someLocation.height! * scalarHeight,
                        );

                        dataUrl = canvas.toDataURL('image/png');
                        const dragItemCreated = this.createImageDragItemFromFile(this.dataUrlToFile(dataUrl, 'placeholder' + someLocation.posX!))!;
                        const dndMapping = new DragAndDropMapping(dragItemCreated, someLocation);
                        this.question.correctMappings!.push(dndMapping);
                    }
                };
                image.src = this.backgroundImage.src;
            }
        }
        this.blankOutBackgroundImage();
    }

    /**
     * Takes all drop locations and replaces their location with a white rectangle on the background image
     */
    blankOutBackgroundImage() {
        const backgroundBlankingCanvas = document.createElement('canvas');
        const backgroundBlankingContext = backgroundBlankingCanvas.getContext('2d');
        const image = new Image();
        let bgWidth;
        let bgHeight;
        image.onload = () => {
            bgHeight = image.height;
            bgWidth = image.width;

            backgroundBlankingCanvas.width = bgWidth;
            backgroundBlankingCanvas.height = bgHeight;
            if (backgroundBlankingContext) {
                const scalarHeight = bgHeight / this.CLICK_LAYER_DIMENSION;
                const scalarWidth = bgWidth / this.CLICK_LAYER_DIMENSION;

                backgroundBlankingContext.drawImage(image, 0, 0);
                backgroundBlankingContext.fillStyle = 'white';

                for (const someLocation of this.question.dropLocations!) {
                    // Draw a white rectangle over the specified box location
                    backgroundBlankingContext.fillRect(
                        someLocation.posX! * scalarWidth,
                        someLocation.posY! * scalarHeight,
                        someLocation.width! * scalarWidth,
                        someLocation.height! * scalarHeight,
                    );
                }
                const dataUrlCanvas = backgroundBlankingCanvas.toDataURL('image/png');
                this.setBackgroundFileFromFile(this.dataUrlToFile(dataUrlCanvas, 'background'));
            }
        };
        image.src = this.backgroundImage.src;
    }

    /**
     * Turns a data url into a blob
     * @param dataUrl the data url string for which the file should be created
     * @returns returns a blob created from the data url
     */
    dataUrlToBlob(dataUrl: string): Blob {
        // Seperate metadata from base64-encoded content
        const byteString = atob(dataUrl.split(',')[1]);
        // Isolate the MIME type (e.g "image/png")
        const mimeString = dataUrl.split(',')[0].split(':')[1].split(';')[0];
        const ab = new ArrayBuffer(byteString.length);
        const ia = new Uint8Array(ab);
        for (let i = 0; i < byteString.length; i++) {
            ia[i] = byteString.charCodeAt(i);
        }
        return new Blob([ab], { type: mimeString });
    }

    /**
     * Creates a File object from  a blob given through a dataUrl
     * @param dataUrl the data url string for which the file should be created
     * @param fileName the name of the file to be created
     * @returns returns a new file created from the data url
     */
    dataUrlToFile(dataUrl: string, fileName: string): File {
        const blob = this.dataUrlToBlob(dataUrl);
        return new File([blob], fileName, { type: blob.type });
    }
}
