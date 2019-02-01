import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output } from '@angular/core';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { DragAndDropQuestionUtil } from '../../../components/util/drag-and-drop-question-util.service';
import { DragAndDropQuestion } from '../../../entities/drag-and-drop-question';
import { DragAndDropMapping } from '../../../entities/drag-and-drop-mapping';
import { DropLocation } from '../../../entities/drop-location';
import { polyfill } from 'mobile-drag-drop';
import { scrollBehaviourDragImageTranslateOverride } from 'mobile-drag-drop/scroll-behaviour';

// options are optional ;)
polyfill({
    // use this to make use of the scroll behaviour
    dragImageTranslateOverride: scrollBehaviourDragImageTranslateOverride
});

// Drag-enter listener for mobile devices
// tslint:disable-next-line
(event: any) => {
    event.preventDefault();
};

window.addEventListener('touchmove', function() {});

@Component({
    selector: 'jhi-drag-and-drop-question',
    templateUrl: './drag-and-drop-question.component.html',
    providers: [ArtemisMarkdown, DragAndDropQuestionUtil]
})
export class DragAndDropQuestionComponent implements OnInit, OnDestroy, OnChanges {
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
    fnOnMappingUpdate: any;

    @Output()
    mappingsChange = new EventEmitter();

    showingSampleSolution = false;
    rendered: DragAndDropQuestion;
    sampleSolutionMappings = new Array<DragAndDropMapping>();
    dropAllowed = false;

    chosenCorrectAnswerOption: number;
    chosenWrongAnswerOption: number;
    amountOfAnswerOptions: number;

    test:number;


    constructor(private artemisMarkdown: ArtemisMarkdown, private dragAndDropQuestionUtil: DragAndDropQuestionUtil) {}

    ngOnInit() {
        this.count();
    }

    ngOnChanges() {
    }

    ngOnDestroy() {}

    watchCollection() {
        // update html for text, hint and explanation for the question
        this.rendered = new DragAndDropQuestion();
        this.rendered.text = this.artemisMarkdown.htmlForMarkdown(this.question.text);
        this.rendered.hint = this.artemisMarkdown.htmlForMarkdown(this.question.hint);
        this.rendered.explanation = this.artemisMarkdown.htmlForMarkdown(this.question.explanation);
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
     * react to the drop event of a drag item
     *
     * @param dropLocation {object | null} the dropLocation that the drag item was dropped on.
     *                     May be null if drag item was dragged back to the unassigned items.
     * @param dragEvent {object} the drag item that was dropped
     */
    onDragDrop(dropLocation: DropLocation, dragEvent: any) {
        this.drop();
        const dragItem = dragEvent.dragData;
        if (dropLocation) {
            // check if this mapping is new
            if (this.dragAndDropQuestionUtil.isMappedTogether(this.mappings, dragItem, dropLocation)) {
                // Do nothing
                return;
            }

            // remove existing mappings that contain the drop location or drag item and save their old partners
            let oldDragItem;
            let oldDropLocation;
            this.mappings = this.mappings.filter(function(mapping) {
                if (this.dragAndDropQuestionUtil.isSameDropLocation(dropLocation, mapping.dropLocation)) {
                    oldDragItem = mapping.dragItem;
                    return false;
                }
                if (this.dragAndDropQuestionUtil.isSameDragItem(dragItem, mapping.dragItem)) {
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
            this.mappings = this.mappings.filter(function(mapping) {
                return !this.dragAndDropQuestionUtil.isSameDragItem(mapping.dragItem, dragItem);
            }, this);
            if (this.mappings.length === lengthBefore) {
                // nothing changed => return here to skip calling this.onMappingUpdate()
                return;
            }
        }

        this.mappingsChange.emit(this.mappings);
        /** Only execute the onMappingUpdate function if we received such input **/
        if (this.fnOnMappingUpdate) {
            this.fnOnMappingUpdate();
        }
    }

    /**
     * Get the drag item that was mapped to the given drop location
     *
     * @param dropLocation {object} the drop location that the drag item should be mapped to
     * @return {object | null} the mapped drag item, or null, if no drag item has been mapped to this location
     */
    dragItemForDropLocation(dropLocation: DropLocation) {
        const that = this;
        const mapping = this.mappings.find(localMapping =>
            that.dragAndDropQuestionUtil.isSameDropLocation(localMapping.dropLocation, dropLocation)
        );
        if (mapping) {
            return mapping.dragItem;
        } else {
            return null;
        }
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
        return this.question.dragItems.filter(dragItem => {
            return !this.mappings.some(mapping => {
                return this.dragAndDropQuestionUtil.isSameDragItem(mapping.dragItem, dragItem);
            }, this);
        }, this);
    }

    /**
     * Check if the assigned drag item from the given location is correct
     * (Only possible if this.question.correctMappings is available)
     *
     * @param dropLocation {object} the drop location to check for correctness
     * @return {boolean} true, if the drop location is correct, otherwise false
     */
    isLocationCorrect(dropLocation: DropLocation) {
        if (!this.question.correctMappings) {
            return false;
        }
        const validDragItems = this.question.correctMappings
            .filter(function(mapping) {
                return this.dragAndDropQuestionUtil.isSameDropLocation(mapping.dropLocation, dropLocation);
            }, this)
            .map(function(mapping) {
                return mapping.dragItem;
            });
        const selectedItem = this.dragItemForDropLocation(dropLocation);

        if (selectedItem === null) {
            return validDragItems.length === 0;
        } else {
            return validDragItems.some(function(dragItem) {
                return this.dragAndDropQuestionUtil.isSameDragItem(dragItem, selectedItem);
            }, this);
        }
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
     * @return {object | null} the mapped drag item, or null, if no drag item has been mapped to this location
     */
    correctDragItemForDropLocation(dropLocation: DropLocation) {
        const dragAndDropQuestionUtil = this.dragAndDropQuestionUtil;
        const mapping = this.sampleSolutionMappings.find(function(solutionMapping) {
            return dragAndDropQuestionUtil.isSameDropLocation(solutionMapping.dropLocation, dropLocation);
        });
        if (mapping) {
            return mapping.dragItem;
        } else {
            return null;
        }
    }

    count(): void {
        this.amountOfAnswerOptions = this.question.dropLocations.length;
        this.chosenWrongAnswerOption = this.question.correctMappings.length;

        this.chosenCorrectAnswerOption = this.amountOfAnswerOptions - this.chosenWrongAnswerOption;
    }
}
