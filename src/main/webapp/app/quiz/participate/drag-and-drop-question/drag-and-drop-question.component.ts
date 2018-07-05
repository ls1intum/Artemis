import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { ArtemisMarkdown } from '../../../components/util/markdown.service';
import { DragAndDropQuestionUtil } from '../../../components/util/drag-and-drop-question-util.service';

@Component({
    selector: 'jhi-drag-and-drop-question',
    templateUrl: './drag-and-drop-question.component.html',
    providers: [ArtemisMarkdown, DragAndDropQuestionUtil]
})
export class DragAndDropQuestionComponent implements OnInit, OnDestroy {
    _question;
    _forceSampleSolution;

    @Input()
    set question(question) {
        this._question = question;
        this.rendered = {
            text: this.artemisMarkdown.htmlForMarkdown(this.question.text),
            hint: this.artemisMarkdown.htmlForMarkdown(this.question.hint),
            explanation: this.artemisMarkdown.htmlForMarkdown(this.question.explanation)
        };
    }
    get question() {
        return this._question;
    }
    @Input() mappings;
    @Input() clickDisabled;
    @Input() showResult;
    @Input() questionIndex;
    @Input() score;
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
    @Input() fnOnMappingUpdate;

    @Output() mappingsChange = new EventEmitter();

    showingSampleSolution = false;
    rendered;
    sampleSolutionMappings = [];
    dropAllowed = false;

    constructor(private artemisMarkdown: ArtemisMarkdown,
                private dragAndDropQuestionUtil: DragAndDropQuestionUtil) {}

    ngOnInit() {
        setTimeout(this.firefoxWorkaround, 200);
        setTimeout(this.firefoxWorkaround, 1000);
    }

    ngOnDestroy() {}

    /**
     * Handles drag-available UI
     */
    drag() {
        this.dropAllowed = true;
        setTimeout(this.firefoxWorkaround, 200);
        setTimeout(this.firefoxWorkaround, 1000);
    }

    /**
     * Handles drag-available UI
     */
    drop() {
        this.dropAllowed = false;
        setTimeout(this.firefoxWorkaround, 200);
        setTimeout(this.firefoxWorkaround, 1000);
    }

    firefoxWorkaround() {
        const elements: any = document.getElementsByClassName("no-line-height");
        for (let i = 0; i < elements.length; i++) {
            elements[i].style.lineHeight = null;
        }
    }

    /**
     * react to the drop event of a drag item
     *
     * @param dropLocation {object | null} the dropLocation that the drag item was dropped on.
     *                     May be null if drag item was dragged back to the unassigned items.
     * @param dragItem {object} the drag item that was dropped
     */
    onDragDrop(dropLocation, dragEvent) {
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
                if (this.dragAndDropQuestionUtil.isSameDropLocationOrDragItem(dropLocation, mapping.dropLocation)) {
                    oldDragItem = mapping.dragItem;
                    return false;
                }
                if (this.dragAndDropQuestionUtil.isSameDropLocationOrDragItem(dragItem, mapping.dragItem)) {
                    oldDropLocation = mapping.dropLocation;
                    return false;
                }
                return true;
            }, this);

            // add new mapping
            this.mappings.push({
                dropLocation,
                dragItem
            });

            // map oldDragItem and oldDropLocation, if they exist
            // this flips positions of drag items when a drag item is dropped on a drop location with an existing drag item
            if (oldDragItem && oldDropLocation) {
                this.mappings.push({
                    dropLocation: oldDropLocation,
                    dragItem: oldDragItem
                });
            }
        } else {
            const lengthBefore = this.mappings.length;
            // remove existing mapping that contains the drag item
            this.mappings = this.mappings.filter(function(mapping) {
                return !this.dragAndDropQuestionUtil.isSameDropLocationOrDragItem(mapping.dragItem, dragItem);
            }, this);
            if (this.mappings.length === lengthBefore) {
                // nothing changed => return here to skip calling this.onMappingUpdate()
                return;
            }
        }

        this.mappingsChange.emit(this.mappings);
        // Note: I had to add a timeout of 0ms here, because the model changes are propagated asynchronously,
        // so we wait for one javascript event cycle before we inform the parent of changes
        setTimeout( () => { this.fnOnMappingUpdate(); }, 0);
    }

    /**
     * Get the drag item that was mapped to the given drop location
     *
     * @param dropLocation {object} the drop location that the drag item should be mapped to
     * @return {object | null} the mapped drag item, or null, if no drag item has been mapped to this location
     */
    dragItemForDropLocation(dropLocation) {
        const mapping = this.mappings.find(function(localMapping) {
            return this.dragAndDropQuestionUtil.isSameDropLocationOrDragItem(localMapping.dropLocation, dropLocation);
        }, this);
        if (mapping) {
            return mapping.dragItem;
        } else {
            return null;
        }
    }

    invalidDragItemForDropLocation(dropLocation) {
        const item = this.dragItemForDropLocation(dropLocation);
        return item ? item.invalid : false;
    }

    /**
     * Get all drag items that have not been assigned to a drop location yet
     *
     * @return {Array} an array of all unassigned drag items
     */
    getUnassignedDragItems() {
        return this.question.dragItems.filter(function(dragItem) {
            return !this.mappings.some(function(mapping) {
                return this.dragAndDropQuestionUtil.isSameDropLocationOrDragItem(mapping.dragItem, dragItem);
            }, this);
        }, this);
    }

    /**
     * Check if the assigned drag item fro the given location is correct
     * (Only possible if this.question.correctMappings is available)
     *
     * @param dropLocation {object} the drop location to check for correctness
     * @return {boolean} true, if the drop location is correct, otherwise false
     */
     isLocationCorrect(dropLocation) {
        if (!this.question.correctMappings) {
            return false;
        }
        const validDragItems = this.question.correctMappings
            .filter(function(mapping) {
                return this.dragAndDropQuestionUtil.isSameDropLocationOrDragItem(mapping.dropLocation, dropLocation);
            }, this)
            .map(function(mapping) {
                return mapping.dragItem;
            });
        const selectedItem = this.dragItemForDropLocation(dropLocation);

        if (selectedItem === null) {
            return validDragItems.length === 0;
        } else {
            return validDragItems.some(function(dragItem) {
                return this.dragAndDropQuestionUtil.isSameDropLocationOrDragItem(dragItem, selectedItem);
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
    correctDragItemForDropLocation(dropLocation) {
        const dragAndDropQuestionUtil = this.dragAndDropQuestionUtil;
        const mapping = this.sampleSolutionMappings.find(function(solutionMapping) {
            return dragAndDropQuestionUtil.isSameDropLocationOrDragItem(solutionMapping.dropLocation, dropLocation);
        });
        if (mapping) {
            return mapping.dragItem;
        } else {
            return null;
        }
    }
}
