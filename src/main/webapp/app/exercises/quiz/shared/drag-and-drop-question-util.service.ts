import { Injectable } from '@angular/core';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { BaseEntityWithTempId, DropLocation } from 'app/entities/quiz/drop-location.model';

@Injectable({ providedIn: 'root' })
export class DragAndDropQuestionUtil {
    constructor() {}

    /**
     * Get a sample solution for the given drag and drop question
     *
     * @param question {object} the drag and drop question we want to solve
     * @param [mappings] {Array} (optional) the mappings we try to use in the sample solution (this may contain incorrect mappings - they will be filtered out)
     * @return {Array} array of mappings that would solve this question (can be empty, if question is unsolvable)
     */
    solve(question: DragAndDropQuestion, mappings?: DragAndDropMapping[]) {
        if (!question.correctMappings) {
            return [];
        }

        const sampleMappings = new Array<DragAndDropMapping>();
        let availableDragItems = question.dragItems;

        // filter out dropLocations that do not need to be mapped
        let remainingDropLocations = question.dropLocations?.filter((dropLocation) => {
            return question.correctMappings?.some((mapping) => {
                return this.isSameEntityWithTempId(mapping.dropLocation, dropLocation);
            }, this);
        }, this);

        if (mappings) {
            // add mappings that are already correct
            mappings.forEach(function (mapping) {
                const correctMapping = this.getMapping(question.correctMappings!, mapping.dragItem!, mapping.dropLocation!);
                if (correctMapping) {
                    sampleMappings.push(correctMapping);
                    remainingDropLocations = remainingDropLocations?.filter(function (dropLocation) {
                        return !this.isSameEntityWithTempId(dropLocation, mapping.dropLocation);
                    }, this);
                    availableDragItems = availableDragItems?.filter(function (dragItem) {
                        return !this.isSameEntityWithTempId(dragItem, mapping.dragItem);
                    }, this);
                }
            }, this);
        }

        // solve recursively
        const solved = this.solveRec(question.correctMappings, remainingDropLocations, availableDragItems, sampleMappings);

        if (solved) {
            return sampleMappings;
        } else {
            return [];
        }
    }

    /**
     * Try to solve a drag and drop question recursively
     *
     * @param correctMappings {Array} the correct mappings defined by the creator of the question
     * @param remainingDropLocations {Array} the drop locations that still need to be mapped (recursion stops if this is empty)
     * @param availableDragItems {Array} the unused drag items that can still be used to map to drop locations (recursion stops if this is empty)
     * @param sampleMappings {Array} the mappings so far
     * @return {boolean} true, if the question was solved (solution is saved in sampleMappings), otherwise false
     */
    solveRec(
        correctMappings: DragAndDropMapping[],
        remainingDropLocations: DropLocation[] | undefined,
        availableDragItems: DragItem[] | undefined,
        sampleMappings: DragAndDropMapping[],
    ) {
        if (!remainingDropLocations || remainingDropLocations.length === 0) {
            return true;
        }

        const dropLocation = remainingDropLocations[0];
        return availableDragItems?.some(function (dragItem, index) {
            const correctMapping = this.getMapping(correctMappings, dragItem, dropLocation);
            if (correctMapping) {
                sampleMappings.push(correctMapping); // add new mapping
                remainingDropLocations.splice(0, 1); // remove first dropLocation
                availableDragItems.splice(index, 1); // remove the used dragItem
                const solved = this.solveRec(correctMappings, remainingDropLocations, availableDragItems, sampleMappings);
                remainingDropLocations.splice(0, 0, dropLocation); // re-insert first dropLocation
                availableDragItems.splice(index, 0, dragItem); // re-insert the used dragItem
                if (!solved) {
                    sampleMappings.pop(); // remove new mapping (only if solution was not found)
                }
                return solved;
            } else {
                return false;
            }
        }, this);
    }

    /**
     * Validate that all correct mappings (and any combination of them that doesn't use a dropLocation or dragItem twice)
     * can be used in a 100% correct solution.
     * This means that if any pair of dragItems share a possible dropLocation, then they must share all dropLocations,
     * or in other words the sets of possible dropLocations for these two dragItems must be identical
     *
     * @param question {object} the question to check
     * @return {boolean} true, if the condition is met, otherwise false
     */
    validateNoMisleadingCorrectMapping(question: DragAndDropQuestion) {
        if (!question.correctMappings || !question.dragItems) {
            // no correct mappings at all means there can be no misleading mappings
            return true;
        }
        // iterate through all pairs of drag items
        for (let i = 0; i < question.dragItems.length; i++) {
            for (let j = 0; j < i; j++) {
                // if these two drag items have one common drop location, they must share all drop locations
                const dragItem1 = question.dragItems[i];
                const dragItem2 = question.dragItems[j];
                const shareOneDropLocation = question.dropLocations?.some(function (dropLocation) {
                    const isMappedWithDragItem1 = this.isMappedTogether(question.correctMappings, dragItem1, dropLocation);
                    const isMappedWithDragItem2 = this.isMappedTogether(question.correctMappings, dragItem2, dropLocation);
                    return isMappedWithDragItem1 && isMappedWithDragItem2;
                }, this);
                if (shareOneDropLocation) {
                    const allDropLocationsForDragItem1 = this.getAllDropLocationsForDragItem(question.correctMappings, dragItem1);
                    const allDropLocationsForDragItem2 = this.getAllDropLocationsForDragItem(question.correctMappings, dragItem2);
                    if (!this.isSameSetOfDropLocations(allDropLocationsForDragItem1, allDropLocationsForDragItem2)) {
                        // condition is violated for this pair of dragItems
                        return false;
                    }
                }
            }
        }
        // condition was met for all pairs of drag items
        return true;
    }

    /**
     * Check if the given dragItem and dropLocation are mapped together in the given mappings
     *
     * @param mappings {Array} the existing mappings to consider
     * @param dragItem {object} the drag item to search for
     * @param dropLocation {object} the drop location to search for
     * @return {boolean} true if they are mapped together, otherwise false
     */
    isMappedTogether(mappings: DragAndDropMapping[], dragItem: DragItem, dropLocation: DropLocation) {
        return !!this.getMapping(mappings, dragItem, dropLocation);
    }

    /**
     * Get the mapping that maps the given dragItem and dropLocation together
     *
     * @param mappings {Array} the existing mappings to consider
     * @param dragItem {object} the drag item to search for
     * @param dropLocation {object} the drop location to search for
     * @return {object | null} the found mapping, or null if it doesn't exist
     */
    getMapping(mappings: DragAndDropMapping[], dragItem: DragItem, dropLocation: DropLocation) {
        const that = this;
        return mappings.find(function (mapping: DragAndDropMapping) {
            return that.isSameEntityWithTempId(dropLocation, mapping.dropLocation) && that.isSameEntityWithTempId(dragItem, mapping.dragItem);
        }, this);
    }

    /**
     * Get all drop locations that are mapped to the given drag items
     *
     * @param mappings {Array} the existing mappings to consider
     * @param dragItem {object} the drag item that the returned drop locations have to be mapped to
     * @return {Array} the resulting drop locations
     */
    getAllDropLocationsForDragItem(mappings: DragAndDropMapping[], dragItem: DragItem): DropLocation[] {
        return mappings.filter((mapping) => this.isSameEntityWithTempId(mapping.dragItem, dragItem)).map((mapping) => mapping.dropLocation!);
    }

    /**
     * Check if set1 and set2 contain the same drag items or drop locations
     *
     * @param set1 {Array} one set of drag items or drop locations
     * @param set2 {Array} another set of drag items or drop locations
     * @return {boolean} true if the sets contain the same items, otherwise false
     */
    isSameSetOfDropLocations(set1: DropLocation[], set2: DropLocation[]): boolean {
        const service = this;
        if (set1.length !== set2.length) {
            // different number of elements => impossible to contain the same elements
            return false;
        }
        return (
            // for every element in set1 there has to be an identical element in set2 and vice versa
            set1.every(function (element1: DropLocation) {
                return set2.some(function (element2: DropLocation) {
                    return service.isSameEntityWithTempId(element1, element2);
                });
            }) &&
            set2.every(function (element2: DropLocation) {
                return set1.some(function (element1: DropLocation) {
                    return service.isSameEntityWithTempId(element1, element2);
                });
            })
        );
    }

    /**
     * compare if the two objects are the same entities with a temp id
     *
     * @param a {object} an entity with a temp id
     * @param b {object} another entity with a temp id
     * @return {boolean}
     */
    isSameEntityWithTempId(a: BaseEntityWithTempId | undefined, b: BaseEntityWithTempId | undefined): boolean {
        return a === b || (a != undefined && b != undefined && ((a.id && b.id && a.id === b.id) || (a.tempID != undefined && b.tempID != undefined && a.tempID === b.tempID)));
    }
}
