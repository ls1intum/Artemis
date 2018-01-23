DragAndDropQuestionController.$inject = ['$translate', '$translatePartialLoader', '$scope', '$timeout', 'ArtemisMarkdown', 'DragAndDropQuestionUtil'];

function DragAndDropQuestionController($translate, $translatePartialLoader, $scope, $timeout, ArtemisMarkdown, DragAndDropQuestionUtil) {

    $translatePartialLoader.addPart('question');
    $translatePartialLoader.addPart('dragAndDropQuestion');
    $translate.refresh();

    var vm = this;

    vm.rendered = {
        text: ArtemisMarkdown.htmlForMarkdown(vm.question.text),
        hint: ArtemisMarkdown.htmlForMarkdown(vm.question.hint),
        explanation: ArtemisMarkdown.htmlForMarkdown(vm.question.explanation)
    };
    vm.showingSampleSolution = false;

    vm.onDragDrop = onDragDrop;
    vm.dragItemForDropLocation = dragItemForDropLocation;
    vm.getUnassignedDragItems = getUnassignedDragItems;
    vm.isLocationCorrect = isLocationCorrect;
    vm.showSampleSolution = showSampleSolution;
    vm.hideSampleSolution = hideSampleSolution;
    vm.correctDragItemForDropLocation = correctDragItemForDropLocation;

    var sampleSolutionMappings = DragAndDropQuestionUtil.solve(vm.question, vm.mappings);

    /**
     * react to the drop event of a drag item
     *
     * @param dropLocation {object | null} the dropLocation that the drag item was dropped on.
     *                     May be null if drag item was dragged back to the unassigned items.
     * @param dragItem {object} the drag item that was dropped
     */
    function onDragDrop(dropLocation, dragItem) {
        if (dropLocation) {
            // check if this mapping is new
            if (DragAndDropQuestionUtil.isMappedTogether(vm.mappings, dragItem, dropLocation)) {
                // Do nothing
                return;
            }

            // remove existing mappings that contain the drop location or drag item and save their old partners
            var oldDragItem;
            var oldDropLocation;
            vm.mappings = vm.mappings.filter(function (mapping) {
                if (DragAndDropQuestionUtil.isSameDropLocationOrDragItem(dropLocation, mapping.dropLocation)) {
                    oldDragItem = mapping.dragItem;
                    return false;
                }
                if (DragAndDropQuestionUtil.isSameDropLocationOrDragItem(dragItem, mapping.dragItem)) {
                    oldDropLocation = mapping.dropLocation;
                    return false;
                }
                return true;
            });

            // add new mapping
            vm.mappings.push({
                dropLocation: dropLocation,
                dragItem: dragItem
            });

            // map oldDragItem and oldDropLocation, if they exist
            // this flips positions of drag items when a drag item is dropped on a drop location with an existing drag item
            if (oldDragItem && oldDropLocation) {
                vm.mappings.push({
                    dropLocation: oldDropLocation,
                    dragItem: oldDragItem
                });
            }
        } else {
            var lengthBefore = vm.mappings.length;
            // remove existing mapping that contains the drag item
            vm.mappings = vm.mappings.filter(function (mapping) {
                return mapping.dragItem.id !== dragItem.id;
            });
            if (vm.mappings.length === lengthBefore) {
                // nothing changed => return here to skip calling vm.onMappingUpdate()
                return;
            }
        }

        // Note: I had to add a timeout of 0ms here, because the model changes are propagated asynchronously,
        // so we wait for one javascript event cycle before we inform the parent of changes
        $timeout(vm.onMappingUpdate, 0);
    }

    /**
     * Get the drag item that was mapped to the given drop location
     *
     * @param dropLocation {object} the drop location that the drag item should be mapped to
     * @return {object | null} the mapped drag item, or null, if no drag item has been mapped to this location
     */
    function dragItemForDropLocation(dropLocation) {
        var mapping = vm.mappings.find(function (mapping) {
            return mapping.dropLocation.id === dropLocation.id;
        });
        if (mapping) {
            return mapping.dragItem;
        } else {
            return null;
        }
    }

    /**
     * Get all drag items that have not been assigned to a drop location yet
     *
     * @return {Array} an array of all unassigned drag items
     */
    function getUnassignedDragItems() {
        return vm.question.dragItems.filter(function (dragItem) {
            return !vm.mappings.some(function (mapping) {
                return mapping.dragItem.id === dragItem.id;
            });
        });
    }

    /**
     * Check if the assigned drag item fro the given location is correct
     * (Only possible if vm.question.correctMappings is available)
     *
     * @param dropLocation {object} the drop location to check for correctness
     * @return {boolean} true, if the drop location is correct, otherwise false
     */
    function isLocationCorrect(dropLocation) {
        if (!vm.question.correctMappings) {
            return false;
        }
        var validDragItems = vm.question.correctMappings
            .filter(function (mapping) {
                return mapping.dropLocation.id === dropLocation.id;
            })
            .map(function (mapping) {
                return mapping.dragItem;
            });
        var selectedItem = dragItemForDropLocation(dropLocation);

        if (selectedItem === null) {
            return validDragItems.length === 0;
        } else {
            return validDragItems.some(function (dragItem) {
                return dragItem.id === selectedItem.id;
            });
        }
    }

    /**
     * Display a sample solution instead of the student's answer
     */
    function showSampleSolution() {
        vm.showingSampleSolution = true;
    }

    /**
     * Display the student's answer again
     */
    function hideSampleSolution() {
        vm.showingSampleSolution = false;
    }

    /**
     * Get the drag item that was mapped to the given drop location in the sample solution
     *
     * @param dropLocation {object} the drop location that the drag item should be mapped to
     * @return {object | null} the mapped drag item, or null, if no drag item has been mapped to this location
     */
    function correctDragItemForDropLocation(dropLocation) {
        var mapping = sampleSolutionMappings.find(function (mapping) {
            return mapping.dropLocation.id === dropLocation.id;
        });
        if (mapping) {
            return mapping.dragItem;
        } else {
            return null;
        }
    }
}

angular.module('artemisApp').component('dragAndDropQuestion', {
    templateUrl: 'app/quiz/participate/drag-and-drop-question/drag-and-drop-question.html',
    controller: DragAndDropQuestionController,
    controllerAs: 'vm',
    bindings: {
        question: '<',
        mappings: '=',
        clickDisabled: '<',
        showResult: '<',
        questionIndex: '<',
        score: '<',
        onMappingUpdate: '&'
    }
});
