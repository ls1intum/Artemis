(function () {
    'use strict';

    angular
        .module('artemisApp')
        .factory('DragAndDropQuestionUtil', DragAndDropQuestionUtil);

    function DragAndDropQuestionUtil() {
        var service = {
            solve: solve,
            isSameDropLocationOrDragItem: isSameDropLocationOrDragItem
        };

        return service;

        /**
         * Get a sample solution for the given drag and drop question
         *
         * @param question {object} the drag and drop question we want to solve
         * @return {Array} array of mappings that would solve this question (may be empty, if question is unsolvable)
         */
        function solve(question) {
            if (!question.correctMappings) {
                return [];
            }

            var sampleMappings = [];

            // filter out dropLocations that do not need to be mapped
            var remainingDropLocations = question.dropLocations.filter(function (dropLocation) {
                return question.correctMappings.some(function (mapping) {
                    return isSameDropLocationOrDragItem(mapping.dropLocation, dropLocation);
                });
            });

            // solve recursively
            var solved = solveRec(question.correctMappings, remainingDropLocations, question.dragItems, sampleMappings);

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
                var newMapping = correctMappings.find(function (mapping) {
                    return isSameDropLocationOrDragItem(dropLocation, mapping.dropLocation) &&
                        isSameDropLocationOrDragItem(dragItem, mapping.dragItem);
                });
                if (newMapping) {
                    sampleMappings.push(newMapping); // add new mapping
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
         * compare if the two objects are the same drag item or drop location
         * @param a {object} a drag item or drop location
         * @param b {object} another drag item or drop location
         * @return {boolean}
         */
        function isSameDropLocationOrDragItem(a, b) {
            return a === b || (a && b && (a.id && b.id && a.id === b.id || a.tempID && b.tempID && a.tempID === b.tempID));
        }
    }

})();
