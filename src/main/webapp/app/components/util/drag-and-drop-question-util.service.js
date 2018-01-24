(function () {
    'use strict';

    angular
        .module('artemisApp')
        .factory('DragAndDropQuestionUtil', DragAndDropQuestionUtil);

    function DragAndDropQuestionUtil() {
        var service = {
            solve: solve,
            validateNoMisleadingCorrectMapping: validateNoMisleadingCorrectMapping,
            isMappedTogether: isMappedTogether,
            isSameDropLocationOrDragItem: isSameDropLocationOrDragItem
        };

        return service;

        /**
         * Get a sample solution for the given drag and drop question
         *
         * @param question {object} the drag and drop question we want to solve
         * @param [mappings] {Array} (optional) the mappings we try to use in the sample solution (this may contain incorrect mappings - they will be filtered out)
         * @return {Array} array of mappings that would solve this question (may be empty, if question is unsolvable)
         */
        function solve(question, mappings) {
            if (!question.correctMappings) {
                return [];
            }

            var sampleMappings = [];
            var availableDragItems = question.dragItems;

            // filter out dropLocations that do not need to be mapped
            var remainingDropLocations = question.dropLocations.filter(function (dropLocation) {
                return question.correctMappings.some(function (mapping) {
                    return isSameDropLocationOrDragItem(mapping.dropLocation, dropLocation);
                });
            });

            if (mappings) {
                // add mappings that are already correct
                mappings.forEach(function (mapping) {
                    var correctMapping = getMapping(question.correctMappings, mapping.dragItem, mapping.dropLocation);
                    if (correctMapping) {
                        sampleMappings.push(correctMapping);
                        remainingDropLocations = remainingDropLocations.filter(function (dropLocation) {
                            return !isSameDropLocationOrDragItem(dropLocation, mapping.dropLocation);
                        });
                        availableDragItems = availableDragItems.filter(function (dragItem) {
                            return !isSameDropLocationOrDragItem(dragItem, mapping.dragItem);
                        });
                    }
                });
            }

            // solve recursively
            var solved = solveRec(question.correctMappings, remainingDropLocations, availableDragItems, sampleMappings);

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
        function solveRec(correctMappings, remainingDropLocations, availableDragItems, sampleMappings) {
            if (remainingDropLocations.length === 0) {
                return true;
            }

            var dropLocation = remainingDropLocations[0];
            return availableDragItems.some(function (dragItem, index) {
                var correctMapping = getMapping(correctMappings, dragItem, dropLocation);
                if (correctMapping) {
                    sampleMappings.push(correctMapping); // add new mapping
                    remainingDropLocations.splice(0, 1); // remove first dropLocation
                    availableDragItems.splice(index, 1); // remove the used dragItem
                    var solved = solveRec(correctMappings, remainingDropLocations, availableDragItems, sampleMappings);
                    remainingDropLocations.splice(0, 0, dropLocation); // re-insert first dropLocation
                    availableDragItems.splice(index, 0, dragItem); // re-insert the used dragItem
                    if (!solved) {
                        sampleMappings.pop(); // remove new mapping (only if solution was not found)
                    }
                    return solved;
                } else {
                    return false;
                }
            });
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
        function validateNoMisleadingCorrectMapping(question) {
            if (!question.correctMappings) {
                // no correct mappings at all means there can be no misleading mappings
                return true;
            }
            // iterate through all pairs of drag items
            for (var i = 0; i < question.dragItems.length; i++) {
                for (var j = 0; j < i; j++) {
                    // if these two drag items have one common drop location, they must share all drop locations
                    var dragItem1 = question.dragItems[i];
                    var dragItem2 = question.dragItems[j];
                    var shareOneDropLocation = question.dropLocations.some(function (dropLocation) {
                        var isMappedWithDragItem1 = isMappedTogether(question.correctMappings, dragItem1, dropLocation);
                        var isMappedWithDragItem2 = isMappedTogether(question.correctMappings, dragItem2, dropLocation);
                        return isMappedWithDragItem1 && isMappedWithDragItem2;
                    });
                    if (shareOneDropLocation) {
                        var allDropLocationsForDragItem1 = getAllDropLocationsForDragItem(question.correctMappings, dragItem1);
                        var allDropLocationsForDragItem2 = getAllDropLocationsForDragItem(question.correctMappings, dragItem2);
                        if (!isSameSetOfDropLocationsOrDragItems(allDropLocationsForDragItem1, allDropLocationsForDragItem2)) {
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
        function isMappedTogether(mappings, dragItem, dropLocation) {
            return !!getMapping(mappings, dragItem, dropLocation);
        }

        /**
         * Get the mapping that maps the given dragItem and dropLocation together
         *
         * @param mappings {Array} the existing mappings to consider
         * @param dragItem {object} the drag item to search for
         * @param dropLocation {object} the drop location to search for
         * @return {object | null} the found mapping, or null if it doesn't exist
         */
        function getMapping(mappings, dragItem, dropLocation) {
            return mappings.find(function (mapping) {
                return (
                    isSameDropLocationOrDragItem(dropLocation, mapping.dropLocation)
                    &&
                    isSameDropLocationOrDragItem(dragItem, mapping.dragItem)
                );
            });
        }

        /**
         * Get all drop locations that are mapped to the given drag items
         *
         * @param mappings {Array} the existing mappings to consider
         * @param dragItem {object} the drag item that the returned drop locations have to be mapped to
         * @return {Array} the resulting drop locations
         */
        function getAllDropLocationsForDragItem(mappings, dragItem) {
            return mappings
                .filter(function (mapping) {
                    return isSameDropLocationOrDragItem(mapping.dragItem, dragItem);
                })
                .map(function (mapping) {
                    return mapping.dropLocation;
                });
        }

        /**
         * Check if set1 and set2 contain the same drag items or drop locations
         *
         * @param set1 {Array} one set of drag items or drop locations
         * @param set2 {Array} another set of drag items or drop locations
         * @return {boolean} true if the sets contain the same items, otherwise false
         */
        function isSameSetOfDropLocationsOrDragItems(set1, set2) {
            if (set1.length !== set2.length) {
                // different number of elements => impossible to contain the same elements
                return false;
            }
            return (
                // for every element in set1 there has to be an identical element in set2 and vice versa
                set1.every(function (element1) {
                    return set2.some(function (element2) {
                        return isSameDropLocationOrDragItem(element1, element2);
                    });
                })
                &&
                set2.every(function (element2) {
                    return set1.some(function (element1) {
                        return isSameDropLocationOrDragItem(element1, element2);
                    });
                })
            );
        }

        /**
         * compare if the two objects are the same drag item or drop location
         *
         * @param a {object} a drag item or drop location
         * @param b {object} another drag item or drop location
         * @return {boolean}
         */
        function isSameDropLocationOrDragItem(a, b) {
            return a === b || (a && b && (a.id && b.id && a.id === b.id || a.tempID && b.tempID && a.tempID === b.tempID));
        }
    }

})();
