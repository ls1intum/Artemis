import { Component, EventEmitter, Input, OnChanges, OnInit, Output, ViewChild, ViewEncapsulation, inject } from '@angular/core';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { DragAndDropQuestionUtil } from 'app/exercises/quiz/shared/drag-and-drop-question-util.service';
import { polyfill } from 'mobile-drag-drop';
import { scrollBehaviourDragImageTranslateOverride } from 'mobile-drag-drop/scroll-behaviour';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { RenderedQuizQuestionMarkDownElement } from 'app/entities/quiz/quiz-question.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { faExclamationCircle, faExclamationTriangle, faQuestionCircle, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { DragItem } from 'app/entities/quiz/drag-item.model';

// options are optional ;)
polyfill({
    // use this to make use of the scroll behaviour
    dragImageTranslateOverride: scrollBehaviourDragImageTranslateOverride,
});

// Drag-enter listener for mobile devices: without this code, mobile drag and drop will not work correctly!
// eslint-disable-next-line @typescript-eslint/no-unused-expressions
(event: any) => {
    event.preventDefault();
};
window.addEventListener('touchmove', () => {}, { passive: false });

enum MappingResult {
    MAPPED_CORRECT,
    MAPPED_INCORRECT,
    NOT_MAPPED,
}
@Component({
    selector: 'jhi-drag-and-drop-question',
    templateUrl: './drag-and-drop-question.component.html',
    providers: [DragAndDropQuestionUtil],
    styleUrls: ['./drag-and-drop-question.component.scss', '../../../participate/quiz-participation.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class DragAndDropQuestionComponent implements OnChanges, OnInit {
    private artemisMarkdown = inject(ArtemisMarkdownService);
    private dragAndDropQuestionUtil = inject(DragAndDropQuestionUtil);

    /** needed to trigger a manual reload of the drag and drop background picture */
    @ViewChild(SecuredImageComponent, { static: false })
    secureImageComponent: SecuredImageComponent;

    _question: DragAndDropQuestion;
    _forceSampleSolution: boolean;

    @Input()
    set question(question) {
        this._question = question;
        this.watchCollection();
    }
    get question() {
        return this._question;
    }
    // TODO: Map vs. Array --> consistency
    @Input()
    mappings: DragAndDropMapping[];
    @Input()
    clickDisabled: boolean;
    @Input()
    showResult: boolean;
    @Input()
    questionIndex: number;
    @Input()
    score: number;
    @Input()
    set forceSampleSolution(forceSampleSolution) {
        this._forceSampleSolution = forceSampleSolution;
        if (this.forceSampleSolution) {
            this.showSampleSolution();
        }
    }
    get forceSampleSolution() {
        return this._forceSampleSolution;
    }
    @Input()
    onMappingUpdate: any;
    @Input()
    filePreviewPaths: Map<string, string> = new Map<string, string>();

    @Output()
    mappingsChange = new EventEmitter<DragAndDropMapping[]>();

    showingSampleSolution = false;
    renderedQuestion: RenderedQuizQuestionMarkDownElement;
    sampleSolutionMappings = new Array<DragAndDropMapping>();
    dropAllowed = false;
    correctAnswer: number;
    incorrectLocationMappings: number;
    mappedLocations: number;

    readonly MappingResult = MappingResult;

    loadingState = 'loading';

    // Icons
    faSpinner = faSpinner;
    faQuestionCircle = faQuestionCircle;
    faExclamationTriangle = faExclamationTriangle;
    faExclamationCircle = faExclamationCircle;

    ngOnInit(): void {
        this.evaluateDropLocations();
    }

    ngOnChanges(): void {
        this.evaluateDropLocations();
    }

    watchCollection() {
        // update html for text, hint and explanation for the question
        this.renderedQuestion = new RenderedQuizQuestionMarkDownElement();
        this.renderedQuestion.text = this.artemisMarkdown.safeHtmlForMarkdown(this.question.text);
        this.renderedQuestion.hint = this.artemisMarkdown.safeHtmlForMarkdown(this.question.hint);
        this.renderedQuestion.explanation = this.artemisMarkdown.safeHtmlForMarkdown(this.question.explanation);
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

    /** Sets the view displayed to the user
     * @param {Output} value -> loading: background picture for drag and drop question is currently loading
     *                          success: background picture for drag and drop question was loaded
     *                          error: an error occurred during background download */
    changeLoading(value: string) {
        this.loadingState = value;
    }

    /**
     * Prevent scrolling when dragging elements on mobile devices
     * @param event
     */
    preventDefault(event: any) {
        event.mouseEvent.preventDefault();
        return false;
    }

    /**
     * react to the drop event of a drag item
     *
     * @param dropLocation {object | undefined} the dropLocation that the drag item was dropped on.
     *                     May be undefined if drag item was dragged back to the unassigned items.
     * @param dropEvent {object} an event containing the drag item that was dropped
     */
    onDragDrop(dropLocation: DropLocation | undefined, dropEvent: CdkDragDrop<DragItem, DragItem>) {
        this.drop();
        const dragItem = dropEvent.item.data as DragItem;

        if (dropLocation) {
            // check if this mapping is new
            if (this.dragAndDropQuestionUtil.isMappedTogether(this.mappings, dragItem, dropLocation)) {
                // Do nothing
                return;
            }

            // remove existing mappings that contain the drop location or drag item and save their old partners
            let oldDragItem;
            let oldDropLocation;
            this.mappings = this.mappings.filter(function (mapping) {
                if (this.dragAndDropQuestionUtil.isSameEntityWithTempId(dropLocation, mapping.dropLocation)) {
                    oldDragItem = mapping.dragItem;
                    return false;
                }
                if (this.dragAndDropQuestionUtil.isSameEntityWithTempId(dragItem, mapping.dragItem)) {
                    oldDropLocation = mapping.dropLocation;
                    return false;
                }
                return true;
            }, this);

            // add new mapping
            this.mappings.push(new DragAndDropMapping(dragItem, dropLocation));

            // map oldDragItem and oldDropLocation, if they exist
            // this flips positions of drag items when a drag item is dropped on a drop location with an existing drag item
            if (oldDragItem && oldDropLocation) {
                this.mappings.push(new DragAndDropMapping(oldDragItem, oldDropLocation));
            }
        } else {
            const lengthBefore = this.mappings.length;
            // remove existing mapping that contains the drag item
            this.mappings = this.mappings.filter(function (mapping) {
                return !this.dragAndDropQuestionUtil.isSameEntityWithTempId(mapping.dragItem, dragItem);
            }, this);
            if (this.mappings.length === lengthBefore) {
                // nothing changed => return here to skip calling this.onMappingUpdate()
                return;
            }
        }

        this.mappingsChange.emit(this.mappings);
        /** Only execute the onMappingUpdate function if we received such input **/
        if (this.onMappingUpdate) {
            this.onMappingUpdate();
        }
    }

    /**
     * Get the drag item that was mapped to the given drop location
     *
     * @param dropLocation {object} the drop location that the drag item should be mapped to
     * @return {object | undefined} the mapped drag item, or undefined, if no drag item has been mapped to this location
     */
    dragItemForDropLocation(dropLocation: DropLocation) {
        if (this.mappings) {
            const mapping = this.mappings.find((localMapping) => this.dragAndDropQuestionUtil.isSameEntityWithTempId(localMapping.dropLocation, dropLocation));
            if (mapping) {
                return mapping.dragItem;
            } else {
                return undefined;
            }
        }
        return undefined;
    }

    invalidDragItemForDropLocation(dropLocation: DropLocation) {
        const item = this.dragItemForDropLocation(dropLocation);
        return item ? item.invalid : false;
    }

    /**
     * Get all drag items that have not been assigned to a drop location yet
     *
     * @return {Array} an array of all unassigned drag items
     */
    getUnassignedDragItems() {
        return this.question.dragItems?.filter((dragItem) => {
            return !this.mappings?.some((mapping) => {
                return this.dragAndDropQuestionUtil.isSameEntityWithTempId(mapping.dragItem, dragItem);
            }, this);
        }, this);
    }

    /**
     * Check if the assigned drag item from the given location is correct
     * (Only possible if this.question.correctMappings is available)
     *
     * @param dropLocation {object} the drop location to check for correctness
     * @return {MappingResult} MAPPED_CORRECT, if the drop location is correct, MAPPED_INCORRECT if not and NOT_MAPPED if the location is correctly left blank
     */
    isLocationCorrect(dropLocation: DropLocation): MappingResult {
        if (!this.question.correctMappings) {
            return MappingResult.MAPPED_INCORRECT;
        }
        const validDragItems = this.question.correctMappings
            .filter(function (mapping) {
                return this.dragAndDropQuestionUtil.isSameEntityWithTempId(mapping.dropLocation, dropLocation);
            }, this)
            .map(function (mapping) {
                return mapping.dragItem;
            });
        const selectedItem = this.dragItemForDropLocation(dropLocation);

        if (!selectedItem) {
            return validDragItems.length === 0 ? MappingResult.NOT_MAPPED : MappingResult.MAPPED_INCORRECT;
        } else {
            return validDragItems.some(function (dragItem) {
                return this.dragAndDropQuestionUtil.isSameEntityWithTempId(dragItem, selectedItem);
            }, this)
                ? MappingResult.MAPPED_CORRECT
                : MappingResult.MAPPED_INCORRECT;
        }
    }

    /**
     * Check if there is a drag item assigned to the given location in the solution of the question
     * (Only possible if this.question.correctMappings is available)
     *
     * @param dropLocation {object} the drop location to check for mapping
     * @return {boolean} true, if the drop location is part of a mapping, otherwise false.
     */

    isAssignedLocation(dropLocation: DropLocation): boolean {
        if (!this.question.correctMappings) {
            return false;
        }
        return this.question.correctMappings.some((mapping) => this.dragAndDropQuestionUtil.isSameEntityWithTempId(dropLocation, mapping.dropLocation));
    }

    /**
     * Display a sample solution instead of the student's answer
     */
    showSampleSolution() {
        this.sampleSolutionMappings = this.dragAndDropQuestionUtil.solve(this.question, this.mappings);
        this.showingSampleSolution = true;
    }

    /**
     * Display the student's answer again
     */
    hideSampleSolution() {
        this.showingSampleSolution = false;
    }

    /**
     * Get the drag item that was mapped to the given drop location in the sample solution
     *
     * @param dropLocation {object} the drop location that the drag item should be mapped to
     * @return {DragItem | undefined} the mapped drag item, or undefined, if no drag item has been mapped to this location
     */
    correctDragItemForDropLocation(dropLocation: DropLocation) {
        const dragAndDropQuestionUtil = this.dragAndDropQuestionUtil;
        const mapping = this.sampleSolutionMappings.find(function (solutionMapping) {
            return dragAndDropQuestionUtil.isSameEntityWithTempId(solutionMapping.dropLocation, dropLocation);
        });
        return mapping?.dragItem;
    }

    /**
     * Count and assign the amount of right mappings, incorrect mappings and the number of drop locations participating in at least one mapping for a question
     * by using the isLocationCorrect Method and the isAssignedLocation Method
     */
    evaluateDropLocations(): void {
        if (this.question.dropLocations) {
            this.correctAnswer = this.question.dropLocations.filter((dropLocation) => this.isLocationCorrect(dropLocation) === MappingResult.MAPPED_CORRECT).length;
            this.incorrectLocationMappings = this.question.dropLocations.filter((dropLocation) => this.isLocationCorrect(dropLocation) === MappingResult.MAPPED_INCORRECT).length;
            this.mappedLocations = this.question.dropLocations.filter((dropLocation) => this.isAssignedLocation(dropLocation)).length;
        }
    }
}
