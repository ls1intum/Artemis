import { Component, Input, Output, EventEmitter, OnInit, AfterViewInit, ViewChild } from '@angular/core';
import { DragAndDropQuestion } from '../../../entities/drag-and-drop-question';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { DragAndDropQuestionUtil } from '../../../components/util/drag-and-drop-question-util.service';
import { FileUploaderService } from '../../../shared/http/file-uploader.service';
import * as $ from 'jquery';

@Component({
    selector: 'jhi-edit-drag-and-drop-question',
    templateUrl: './edit-drag-and-drop-question.component.html'
})
export class EditDragAndDropQuestionComponent implements OnInit, AfterViewInit {

    @ViewChild('editor') editor;

    @Input() question: DragAndDropQuestion;
    @Input() questionIndex: number;

    @Output() questionUpdated = new EventEmitter<object>();
    @Output() questionDeleted = new EventEmitter<object>();
    @Output() questionMoveUp = new EventEmitter<object>();
    @Output() questionMoveDown = new EventEmitter<object>();

    backupQuestion: DragAndDropQuestion;
    random: number;

    dragItemPicture = null;
    backgroundFile = null;
    dragItemFile = null;

    showPreview: boolean;
    isUploadingBackgroundFile: boolean;
    isUploadingDragItemFile: boolean;

    /**
     * enum for the different drag operations
     *
     * @type {{NONE: number, CREATE: number, MOVE: number, RESIZE_BOTH: number, RESIZE_X: number, RESIZE_Y: number}}
     */
    DragState = {
        NONE: 0,
        CREATE: 1,
        MOVE: 2,
        RESIZE_BOTH: 3,
        RESIZE_X: 4,
        RESIZE_Y: 5
    };

    /**
     * Keep track of what the current drag action is doing
     * @type {number}
     */
    draggingState = this.DragState.NONE;

    /**
     * Keep track of the currently dragged drop location
     */
    currentDropLocation = {
        tempID: null,
        posX: null,
        posY: null,
        width: null,
        height: null
    };

    /**
     * Keep track of the current mouse location
     * @type {object}
     */
    mouse = {
        x: null,
        y: null,
        startX: null,
        startY: null,
        offsetX: null,
        offsetY: null
    };

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

    // /**
    //  * Prevent page from jumping back to last clicked/selected element on drop
    //  */
    // $scope.$on('ANGULAR_DRAG_START', function () {
    //     window.getSelection().removeAllRanges();
    // });
    //
    //
    // /**
    //  * bind to mouse events
    //  */
    // $document.bind("mousemove", mouseMove);
    // $document.bind("mouseup", mouseUp);
    //
    // /**
    //  * unbind mouse events when this component is removed
    //  */
    // $scope.$on('$destroy', function () {
    //     $document.unbind("mousemove", mouseMove);
    //     $document.unbind("mouseup", mouseUp);
    // });
    //
    //
    // /**
    //  * watch for any changes to the question model and notify listener
    //  *
    //  * (use 'initializing' boolean to prevent $watch from firing immediately)
    //  */
    // var initializing = true;
    // $scope.$watchCollection('vm.question', function () {
    //     if (initializing) {
    //         initializing = false;
    //         return;
    //     }
    //     vm.onUpdated();
    // });

    constructor(private artemisMarkdown: ArtemisMarkdown,
                private dragAndDropQuestionUtil: DragAndDropQuestionUtil,
                private fileUploaderService: FileUploaderService) {}

    ngOnInit(): void {
        // Create question backup for resets
        this.backupQuestion = Object.assign({}, this.question);

        // Assign status booleans
        this.showPreview = false;
        this.isUploadingBackgroundFile = false;
        this.isUploadingDragItemFile = false;

        /**
         * Bind to mouse events
         */
        // TODO: do this via template event bindings
        // document.bind('mousemove', this.mouseMove);
        // document.bind('mouseup', this.mouseUp);
    }

    ngAfterViewInit(): void {
        this.random = this.pseudoRandomLong();

        // Setup the editor
        this.setupQuestionEditor();
    }

    /**
     * @function setupQuestionEditor
     * @desc Set up Question text editor
     */
    setupQuestionEditor() {
        // requestAnimationFrame(function() {
        //     this.editor = ace.edit("question-content-editor-" + this.random);
        //     editor.setTheme("ace/theme/chrome");
        //     editor.getSession().setMode("ace/mode/markdown");
        //     editor.renderer.setShowGutter(false);
        //     editor.renderer.setPadding(10);
        //     editor.renderer.setScrollMargin(8, 8);
        //     editor.setHighlightActiveLine(false);
        //     editor.setShowPrintMargin(false);
        //
        //     // generate markdown from question and show result in editor
        //     editor.setValue(ArtemisMarkdown.generateTextHintExplanation(vm.question));
        //     editor.clearSelection();
        //
        //     editor.on("blur", function () {
        //         // parse the markdown in the editor and update question accordingly
        //         ArtemisMarkdown.parseTextHintExplanation(editor.getValue(), vm.question);
        //         vm.onUpdated();
        //         $scope.$apply();
        //     });
        // });
    }

    /**
     * add the markdown for a hint at the current cursor location
     */
    addHintAtCursor() {
        this.artemisMarkdown.addHintAtCursor(this.editor);
    }

    /**
     * add the markdown for an explanation at the current cursor location
     */
    addExplanationAtCursor() {
        this.artemisMarkdown.addExplanationAtCursor(this.editor);
    }

    /**
     * @function uploadBackground
     * @desc Upload the selected file (from "Upload Background") and use it for the question's backgroundFilePath
     */
    uploadBackground() {
        const file = this.backgroundFile;

        this.isUploadingBackgroundFile = true;
        this.fileUploaderService.uploadFile(file).then( result => {
            this.question.backgroundFilePath = result.path;
            this.isUploadingBackgroundFile = false;
            this.backgroundFile = null;
        }, error => {
            console.error('Error during file upload in uploadBackground()', error.message);
            this.isUploadingBackgroundFile = false;
            this.backgroundFile = null;
        });
    }

    /**
     * react to mousemove events on the entire page to update:
     * - mouse object (always)
     * - current drop location (only while dragging)
     *
     * @param e {object} the mouse move event
     */
    /**
     * @function
     * @desc
     * @param event
     */
    mouseMove(e) {
        // Update mouse x and y value
        const event = e || window.event; // Moz || IE
        const clickLayer = $('#click-layer-' + this.random);
        const backgroundOffset = clickLayer.offset();
        const backgroundWidth = clickLayer.width();
        const backgroundHeight = clickLayer.height();
        if (event.pageX) { // Moz
            this.mouse.x = event.pageX - backgroundOffset.left;
            this.mouse.y = event.pageY - backgroundOffset.top;
        } else if (event.clientX) { // IE
            this.mouse.x = event.clientX - backgroundOffset.left;
            this.mouse.y = event.clientY - backgroundOffset.top;
        }
        this.mouse.x = Math.min(Math.max(0, this.mouse.x), backgroundWidth);
        this.mouse.y = Math.min(Math.max(0, this.mouse.y), backgroundHeight);

        if (this.draggingState !== this.DragState.NONE) {
            switch (this.draggingState) {
                case this.DragState.CREATE:
                case this.DragState.RESIZE_BOTH:
                    // Update current drop location's position and size
                    this.currentDropLocation.posX = Math.round(200 * Math.min(this.mouse.x, this.mouse.startX) / backgroundWidth);
                    this.currentDropLocation.posY = Math.round(200 * Math.min(this.mouse.y, this.mouse.startY) / backgroundHeight);
                    this.currentDropLocation.width = Math.round(200 * Math.abs(this.mouse.x - this.mouse.startX) / backgroundWidth);
                    this.currentDropLocation.height = Math.round(200 * Math.abs(this.mouse.y - this.mouse.startY) / backgroundHeight);
                    break;
                case this.DragState.MOVE:
                    // update current drop location's position
                    this.currentDropLocation.posX = Math.round(Math.min(Math.max(0, 200 * (this.mouse.x + this.mouse.offsetX) / backgroundWidth), 200 - this.currentDropLocation.width));
                    this.currentDropLocation.posY = Math.round(Math.min(Math.max(0, 200 * (this.mouse.y + this.mouse.offsetY) / backgroundHeight), 200 - this.currentDropLocation.height));
                    break;
                case this.DragState.RESIZE_X:
                    // Update current drop location's position and size (only x-axis)
                    this.currentDropLocation.posX = Math.round(200 * Math.min(this.mouse.x, this.mouse.startX) / backgroundWidth);
                    this.currentDropLocation.width = Math.round(200 * Math.abs(this.mouse.x - this.mouse.startX) / backgroundWidth);
                    break;
                case this.DragState.RESIZE_Y:
                    // update current drop location's position and size (only y-axis)
                    this.currentDropLocation.posY = Math.round(200 * Math.min(this.mouse.y, this.mouse.startY) / backgroundHeight);
                    this.currentDropLocation.height = Math.round(200 * Math.abs(this.mouse.y - this.mouse.startY) / backgroundHeight);
                    break;
            }

            // TODO: how to replace this?
            // update view
            // $scope.$apply();
        }
    }

    /**
     * @function mouseUp
     * @desc React to mouseup events to finish dragging operations
     */
    mouseUp() {
        if (this.draggingState !== this.DragState.NONE) {
            switch (this.draggingState) {
                case this.DragState.CREATE:
                    // TODO: replace this with viewchild ref
                    const clickLayer = $('#click-layer-' + this.random);
                    const backgroundWidth = clickLayer.width();
                    const backgroundHeight = clickLayer.height();
                    if (this.currentDropLocation.width / 200 * backgroundWidth < 14 && this.currentDropLocation.height / 200 * backgroundHeight < 14) {
                        // Remove drop Location if too small (assume it was an accidental click/drag),
                        this.deleteDropLocation(this.currentDropLocation);
                    } else {
                        // Notify parent of new drop location
                        this.questionUpdated.emit();
                    }
                    break;
                case this.DragState.MOVE:
                case this.DragState.RESIZE_BOTH:
                case this.DragState.RESIZE_X:
                case this.DragState.RESIZE_Y:
                    // Notify parent of changed drop location
                    this.questionUpdated.emit();
                    break;
            }

            // TODO: how to replace this?
            // update view
            // $scope.$apply();
        }
        // Update state
        this.draggingState = this.DragState.NONE;
        this.currentDropLocation = null;
    }

    /**
     * @function backgroundMouseDown
     * @desc React to mouse down events on the background to start dragging
     */
    backgroundMouseDown() {
        if (this.question.backgroundFilePath && this.draggingState === this.DragState.NONE) {
            // Save current mouse position as starting position
            this.mouse.startX = this.mouse.x;
            this.mouse.startY = this.mouse.y;

            // Create new drop location
            this.currentDropLocation = {
                tempID: this.pseudoRandomLong(),
                posX: this.mouse.x,
                posY: this.mouse.y,
                width: 0,
                height: 0
            };

            // Add drop location to question
            if (!this.question.dropLocations) {
                this.question.dropLocations = [];
            }
            this.question.dropLocations.push(this.currentDropLocation);

            // Update state
            this.draggingState = this.DragState.CREATE;
        }
    }

    /**
     * @function dropLocationMouseDown
     * @desc React to mousedown events on a drop location to start moving it
     * @param dropLocation {object} the drop location to move
     */
    dropLocationMouseDown(dropLocation) {
        if (this.draggingState === this.DragState.NONE) {
            // TODO: replace this with viewchild ref
            const clickLayer = $('#click-layer-' + this.random);
            const backgroundWidth = clickLayer.width();
            const backgroundHeight = clickLayer.height();

            const dropLocationX = dropLocation.posX / 200 * backgroundWidth;
            const dropLocationY = dropLocation.posY / 200 * backgroundHeight;

            // Save offset of mouse in drop location
            this.mouse.offsetX = dropLocationX - this.mouse.x;
            this.mouse.offsetY = dropLocationY - this.mouse.y;

            // Update state
            this.currentDropLocation = dropLocation;
            this.draggingState = this.DragState.MOVE;
        }
    }

    /**
     * @function deleteDropLocation
     * @desc Delete the given drop location
     * @param dropLocationToDelete {object} the drop location to delete
     */
    deleteDropLocation(dropLocationToDelete) {
        this.question.dropLocations = this.question.dropLocations.filter(dropLocation => dropLocation !== dropLocationToDelete);
        this.deleteMappingsForDropLocation(dropLocationToDelete);
    }

    /**
     * @function duplicateDropLocation
     * @desc Add an identical drop location to the question
     * @param dropLocation {object} the drop location to duplicate
     */
    duplicateDropLocation(dropLocation) {
        this.question.dropLocations.push({
            tempID: this.pseudoRandomLong(),
            posX: dropLocation.posX + dropLocation.width < 197 ? dropLocation.posX + 3 : Math.max(0, dropLocation.posX - 3),
            posY: dropLocation.posY + dropLocation.height < 197 ? dropLocation.posY + 3 : Math.max(0, dropLocation.posY - 3),
            width: dropLocation.width,
            height: dropLocation.height
        });
    }

    /**
     * @function resizeMouseDown
     * @desc React to mousedown events on the resize handles to start resizing the drop location
     * @param dropLocation {object} the drop location that will be resized
     * @param resizeLocationY {string} 'top', 'middle' or 'bottom'
     * @param resizeLocationX {string} 'left', 'center' or 'right'
     */
    resizeMouseDown(dropLocation, resizeLocationY, resizeLocationX) {
        if (this.draggingState === this.DragState.NONE) {
            // TODO: replace this with viewchild ref
            const clickLayer = $('#click-layer-' + this.random);
            const backgroundWidth = clickLayer.width();
            const backgroundHeight = clickLayer.height();

            // Update state
            this.draggingState = this.DragState.RESIZE_BOTH;  // Default is both, will be overwritten later, if needed
            this.currentDropLocation = dropLocation;

            switch (resizeLocationY) {
                case 'top':
                    // Use opposite end as startY
                    this.mouse.startY = (dropLocation.posY + dropLocation.height) / 200 * backgroundHeight;
                    break;
                case 'middle':
                    // Limit to x-axis, startY will not be used
                    this.draggingState = this.DragState.RESIZE_X;
                    break;
                case 'bottom':
                    // Use opposite end as startY
                    this.mouse.startY = dropLocation.posY / 200 * backgroundHeight;
                    break;
            }

            switch (resizeLocationX) {
                case 'left':
                    // Use opposite end as startX
                    this.mouse.startX = (dropLocation.posX + dropLocation.width) / 200 * backgroundWidth;
                    break;
                case 'center':
                    // Limit to y-axis, startX will not be used
                    this.draggingState = this.DragState.RESIZE_Y;
                    break;
                case 'right':
                    // Use opposite end as startX
                    this.mouse.startX = dropLocation.posX / 200 * backgroundWidth;
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
        this.question.dragItems.push({
            tempID: this.pseudoRandomLong(),
            text: 'Text'
        });
        this.questionUpdated.emit();
    }

    /**
     * @function uploadDragItem
     * @desc Add a Picture Drag Item with the selected file as its picture to the question
     */
    uploadDragItem() {
        const file = this.dragItemFile;

        this.isUploadingDragItemFile = true;
        this.fileUploaderService.uploadFile(file).then(result => {
            // Add drag item to question
            if (!this.question.dragItems) {
                this.question.dragItems = [];
            }
            this.question.dragItems.push({
                tempID: this.pseudoRandomLong(),
                pictureFilePath: result.path
            });
            this.questionUpdated.emit();
            this.isUploadingDragItemFile = false;
            this.dragItemFile = null;
        }, error => {
            console.error('Error during file upload in uploadDragItem()', error.message);
            this.isUploadingDragItemFile = false;
            this.dragItemFile = null;
        });
    }

    /**
     * @function uploadPictureForDragItemChange
     * @desc Upload a Picture for Drag Item Change with the selected file as its picture
     */
    uploadPictureForDragItemChange() {
        const file = this.dragItemFile;

        this.isUploadingDragItemFile = true;
        this.fileUploaderService.uploadFile(file).then(result => {
            this.dragItemPicture = result.path;
            this.questionUpdated.emit();
            this.isUploadingDragItemFile = false;
            this.dragItemFile = null;
        }, error => {
            console.error('Error during file upload in uploadPictureForDragItemChange()', error.message);
            this.isUploadingDragItemFile = false;
            this.dragItemFile = null;
        });
    }

    /**
     * @function deleteDragItem
     * @desc Delete the drag item from the question
     * @param dragItemToDelete {object} the drag item that should be deleted
     */
    deleteDragItem(dragItemToDelete) {
        this.question.dragItems = this.question.dragItems.filter(dragItem =>  dragItem !== dragItemToDelete);
        this.deleteMappingsForDragItem(dragItemToDelete);
    }

    /**
     * @function onDragDrop
     * @desc React to a drag item being dropped on a drop location
     * @param dropLocation {object} the drop location involved
     * @param dragItem {object} the drag item involved (may be a copy at this point)
     */
    onDragDrop(dropLocation, dragItem) {
        // Replace dragItem with original (because it may be a copy)
        dragItem = this.question.dragItems.find(originalDragItem => dragItem.id ? originalDragItem.id === dragItem.id : originalDragItem.tempID === dragItem.tempID);

        if (!dragItem) {
            // Drag item was not found in question => do nothing
            return;
        }

        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }

        // Check if this mapping already exists
        if (!this.question.correctMappings.some(existingMapping =>
                this.dragAndDropQuestionUtil.isSameDropLocationOrDragItem(existingMapping.dropLocation, dropLocation)
                &&
                this.dragAndDropQuestionUtil.isSameDropLocationOrDragItem(existingMapping.dragItem, dragItem)
        )) {
            // Mapping doesn't exit yet => add this mapping
            this.question.correctMappings.push({
                dropLocation,
               dragItem
            });

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
    getMappingIndex(mapping) {
        const visitedDropLocations = [];
        // Save reference to this due to nested some calls
        const that = this;
        if (this.question.correctMappings.some(function(correctMapping) {
            if (!visitedDropLocations.some(dropLocation => {
                return that.dragAndDropQuestionUtil.isSameDropLocationOrDragItem(dropLocation, correctMapping.dropLocation);
            })) {
                visitedDropLocations.push(correctMapping.dropLocation);
            }
            return that.dragAndDropQuestionUtil.isSameDropLocationOrDragItem(correctMapping.dropLocation, mapping.dropLocation);
        })) {
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
    getMappingsForDropLocation(dropLocation) {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        return this.question.correctMappings.filter(mapping => this.dragAndDropQuestionUtil.isSameDropLocationOrDragItem(mapping.dropLocation, dropLocation));
    }

    /**
     * @function getMappingsForDragItem
     * @desc Get all mappings that involve the given drag item
     * @param dragItem {object} the drag item for which we want to get all mappings
     * @return {Array} all mappings that belong to the given drag item
     */
    getMappingsForDragItem(dragItem) {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        return this.question.correctMappings.filter(mapping => this.dragAndDropQuestionUtil.isSameDropLocationOrDragItem(mapping.dragItem, dragItem))
            /** Moved the sorting from the template to the function call **/
            .sort( (m1, m2) => this.getMappingIndex(m1) - this.getMappingIndex(m2));
    }

    /**
     * @function deleteMappingsForDropLocation
     * @desc Delete all mappings for the given drop location
     * @param dropLocation {object} the drop location for which we want to delete all mappings
     */
    deleteMappingsForDropLocation(dropLocation) {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        this.question.correctMappings = this.question.correctMappings.filter(mapping =>
            !this.dragAndDropQuestionUtil.isSameDropLocationOrDragItem(mapping.dropLocation, dropLocation)
        );
    }

    /**
     * @function deleteMappingsForDragItem
     * @desc Delete all mappings for the given drag item
     * @param dragItem {object} the drag item for which we want to delete all mappings
     */
    deleteMappingsForDragItem(dragItem) {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        this.question.correctMappings = this.question.correctMappings.filter(mapping =>
            !this.dragAndDropQuestionUtil.isSameDropLocationOrDragItem(mapping.dragItem, dragItem)
        );
    }

    /**
     * @function deleteMapping
     * @desc Delete the given mapping from the question
     * @param mappingToDelete {object} the mapping to delete
     */
    deleteMapping(mappingToDelete) {
        if (!this.question.correctMappings) {
            this.question.correctMappings = [];
        }
        this.question.correctMappings = this.question.correctMappings.filter(mapping =>  mapping !== mappingToDelete);
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
    changeToTextDragItem(dragItem) {
        dragItem.pictureFilePath = null;
        dragItem.text = 'Text';
    }

    /**
     * @function changeToPictureDragItem
     * @desc Change Text-Drag-Item to Picture-Drag-Item with PictureFile: this.dragItemFile
     * @param dragItem {dragItem} the dragItem, which will be changed
     */
    changeToPictureDragItem(dragItem) {
        const file = this.dragItemFile;

        this.isUploadingDragItemFile = true;
        this.fileUploaderService.uploadFile(file).then(result => {
            this.dragItemPicture = result.path;
            this.questionUpdated.emit();
            this.isUploadingDragItemFile = false;
            if (this.dragItemPicture != null) {
                dragItem.text = null;
                dragItem.pictureFilePath = this.dragItemPicture;
            }
        }, error => {
            console.error('Error during file upload in changeToPictureDragItem()', error.message);
            this.isUploadingDragItemFile = false;
            this.dragItemFile = null;
        });
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
    resetDropLocation(dropLocation) {
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
    resetDragItem(dragItem) {
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
