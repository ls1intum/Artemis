DragAndDropQuestionController.$inject = ['$translate', '$translatePartialLoader', '$scope', '$sanitize', '$timeout', 'ArtemisMarkdown'];

function DragAndDropQuestionController($translate, $translatePartialLoader, $scope, $sanitize, $timeout, ArtemisMarkdown) {

    $translatePartialLoader.addPart('question');
    $translatePartialLoader.addPart('dragAndDropQuestion');
    $translate.refresh();

    var vm = this;

    vm.rendered = {
        text: ArtemisMarkdown.htmlForMarkdown(vm.question.text),
        hint: ArtemisMarkdown.htmlForMarkdown(vm.question.hint),
        explanation: ArtemisMarkdown.htmlForMarkdown(vm.question.explanation)
    };

    vm.onDragDrop = onDragDrop;
    vm.dragItemForDropLocation = dragItemForDropLocation;
    vm.getUnassignedDragItems = getUnassignedDragItems;

    /**
     * react to the drop event of a drag item
     *
     * @param dropLocation {object | null} the dropLocation that the drag item was dropped on.
     *                     May be null if drag item was dragged back to the unassigned items.
     * @param dragItem {object} the drag item that was dropped
     */
    function onDragDrop(dropLocation, dragItem) {
        if (dropLocation) {
            // remove existing mappings that contain the drop location or the drag item
            vm.mappings = vm.mappings.filter(function (mapping) {
                return mapping.dropLocation.id !== dropLocation.id && mapping.dragItem.id !== dragItem.id;
            });

            // add new mapping
            vm.mappings.push({
                dropLocation: dropLocation,
                dragItem: dragItem
            });
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
