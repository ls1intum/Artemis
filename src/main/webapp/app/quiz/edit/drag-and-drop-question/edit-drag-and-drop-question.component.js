EditDragAndDropQuestionController.$inject = ['$translate', '$translatePartialLoader', '$scope', 'FileUpload', '$document'];

function EditDragAndDropQuestionController($translate, $translatePartialLoader, $scope, FileUpload, $document) {

    /**
     * enum for the different drag operations
     *
     * @type {{NONE: number, CREATE: number, MOVE: number, RESIZE_BOTH: number, RESIZE_X: number, RESIZE_Y: number}}
     */
    var DragState = {
        NONE: 0,
        CREATE: 1,
        MOVE: 2,
        RESIZE_BOTH: 3,
        RESIZE_X: 4,
        RESIZE_Y: 5
    };

    $translatePartialLoader.addPart('question');
    $translatePartialLoader.addPart('dragAndDropQuestion');
    $translate.refresh();

    var vm = this;
    vm.scoringTypeOptions = [
        {
            key: "ALL_OR_NOTHING",
            label: "All or Nothing"
        }
    ];

    vm.backgroundFile = null;
    vm.showPreview = false;
    vm.isUploadingBackgroundFile = false;
    vm.togglePreview = togglePreview;
    vm.uploadBackground = uploadBackground;
    vm.backgroundMouseDown = backgroundMouseDown;
    vm.dropLocationMouseDown = dropLocationMouseDown;
    vm.deleteDropLocation = deleteDropLocation;
    vm.duplicateDropLocation = duplicateDropLocation;

    function togglePreview() {
        vm.showPreview = !vm.showPreview;
    }

    /**
     * Upload the selected file (from "Upload Background") and use it for the question's backgroundFilePath
     */
    function uploadBackground() {
        var file = vm.backgroundFile;

        var fileExtension = file.name.split('.').pop().toLocaleLowerCase();
        if (fileExtension !== "png" && fileExtension !== "jpg" && fileExtension !== "jpeg" && fileExtension !== "svg") {
            alert('Unsupported file-type! Only files of type ".png", ".jpg" or ".svg" allowed.');
            return;
        }
        if (file.size > 5000000) {
            alert('File is too big! Maximum allowed file size: 5 MB.');
            return;
        }

        if (!file) {
            alert("Please select a file to upload first.");
            return;
        }

        vm.isUploadingBackgroundFile = true;
        FileUpload(file).then(
            function (result) {
                vm.question.backgroundFilePath = result.data.path;
                vm.isUploadingBackgroundFile = false;
            }, function (error) {
                alert(error);
                vm.isUploadingBackgroundFile = false;
            });
    }

    /**
     * keep track of what the current drag action is doing
     * @type {number}
     */
    var draggingState = DragState.NONE;

    /**
     * keep track of the currently dragged drop location
     * @type {object | null}
     */
    var currentDropLocation = null;

    /**
     * keep track of the current mouse location
     * @type {object}
     */
    var mouse = {};

    /**
     * bind to mouse events
     */
    $document.bind("mousemove", mouseMove);
    $document.bind("mouseup", mouseUp);

    /**
     * unbind mouse events when this component is removed
     */
    $scope.$on('$destroy', function () {
        $document.unbind("mousemove", mouseMove);
        $document.unbind("mouseup", mouseUp);
    });

    /**
     * react to mousemove events on the entire page to update:
     * - mouse object (always)
     * - current drop location (only while dragging)
     *
     * @param e {object} the mouse move event
     */
    function mouseMove(e) {
        // update mouse x and y value
        var ev = e || window.event; //Moz || IE
        var clickLayer = $(".click-layer");
        var backgroundOffset = clickLayer.offset();
        var backgroundWidth = clickLayer.width();
        var backgroundHeight = clickLayer.height();
        if (ev.pageX) { //Moz
            mouse.x = ev.pageX - backgroundOffset.left;
            mouse.y = ev.pageY - backgroundOffset.top;
        } else if (ev.clientX) { //IE
            mouse.x = ev.clientX - backgroundOffset.left;
            mouse.y = ev.clientY - backgroundOffset.top;
        }
        mouse.x = Math.min(Math.max(0, mouse.x), backgroundWidth);
        mouse.y = Math.min(Math.max(0, mouse.y), backgroundHeight);

        if (currentDropLocation) {
            switch (draggingState) {
                case DragState.CREATE:
                    // update current drop location's position and size
                    currentDropLocation.posX = Math.round(200 * Math.min(mouse.x, mouse.startX) / backgroundWidth);
                    currentDropLocation.posY = Math.round(200 * Math.min(mouse.y, mouse.startY) / backgroundHeight);
                    currentDropLocation.width = Math.round(200 * Math.abs(mouse.x - mouse.startX) / backgroundWidth);
                    currentDropLocation.height = Math.round(200 * Math.abs(mouse.y - mouse.startY) / backgroundHeight);

                    // update view
                    $scope.$apply();
                    break;
                case DragState.MOVE:
                    // update current drop location's position
                    currentDropLocation.posX = Math.round(Math.min(Math.max(0, 200 * (mouse.x + mouse.offsetX) / backgroundWidth), 200 - currentDropLocation.width));
                    currentDropLocation.posY = Math.round(Math.min(Math.max(0, 200 * (mouse.y + mouse.offsetY) / backgroundHeight), 200 - currentDropLocation.height));

                    // update view
                    $scope.$apply();
                    break;
                case DragState.RESIZE_BOTH:
                    // TODO
                    break;
                case DragState.RESIZE_X:
                    // TODO
                    break;
                case DragState.RESIZE_Y:
                    // TODO
                    break;
            }

        }
    }

    /**
     * react to mouseup events to finish dragging operations
     */
    function mouseUp() {
        if (currentDropLocation) {
            switch (draggingState) {
                case DragState.CREATE:
                    var clickLayer = $(".click-layer");
                    var backgroundWidth = clickLayer.width();
                    var backgroundHeight = clickLayer.height();
                    // remove drop Location if minimum dimensions are not met,
                    // notify parent of new drop location otherwise
                    if (currentDropLocation.width / 200 * backgroundWidth < 10 || currentDropLocation.height / 200 * backgroundHeight < 10) {
                        vm.question.dropLocations.pop();
                    } else {
                        vm.onUpdated();
                    }
                    // update view
                    $scope.$apply();
                    break;
                case DragState.MOVE:
                    // notify parent of changed drop location
                    vm.onUpdated();
                    // update view
                    $scope.$apply();
                    break;
                case DragState.RESIZE_BOTH:
                    // TODO
                    break;
                case DragState.RESIZE_X:
                    // TODO
                    break;
                case DragState.RESIZE_Y:
                    // TODO
                    break;
            }
        }
        // update state
        draggingState = DragState.NONE;
        currentDropLocation = null;
    }

    /**
     * react to mouse down events on the background to start dragging
     */
    function backgroundMouseDown() {
        if (vm.question.backgroundFilePath && draggingState === DragState.NONE) {
            // save current mouse position as starting position
            mouse.startX = mouse.x;
            mouse.startY = mouse.y;

            // create new drop location
            currentDropLocation = {
                posX: mouse.x,
                posY: mouse.y,
                width: 0,
                height: 0
            };

            // add drop location to question
            if (!vm.question.dropLocations) {
                vm.question.dropLocations = [];
            }
            vm.question.dropLocations.push(currentDropLocation);

            // update state
            draggingState = DragState.CREATE;
        }
    }

    /**
     * react to mousedown events on a drop location to start moving it
     *
     * @param dropLocation {object} the drop location to move
     */
    function dropLocationMouseDown(dropLocation) {
        var clickLayer = $(".click-layer");
        var backgroundWidth = clickLayer.width();
        var backgroundHeight = clickLayer.height();

        var dropLocationX = dropLocation.posX / 200 * backgroundWidth;
        var dropLocationY = dropLocation.posY / 200 * backgroundHeight;

        // save offset of mouse in drop location
        mouse.offsetX = dropLocationX - mouse.x;
        mouse.offsetY = dropLocationY - mouse.y;

        // update state
        currentDropLocation = dropLocation;
        draggingState = DragState.MOVE;
    }

    /**
     * Delete the given drop location
     * @param dropLocationToDelete {object} the drop location to delete
     */
    function deleteDropLocation(dropLocationToDelete) {
        vm.question.dropLocations = vm.question.dropLocations.filter(function (dropLocation) {
            return dropLocation !== dropLocationToDelete;
        });
    }

    /**
     * Add an identical drop location to the question
     * @param dropLocation {object} the drop location to duplicate
     */
    function duplicateDropLocation(dropLocation) {
        vm.question.dropLocations.push({
            posX: dropLocation.posX,
            posY: dropLocation.posY,
            width: dropLocation.width,
            height: dropLocation.height
        });
    }

    /**
     * watch for any changes to the question model and notify listener
     *
     * (use 'initializing' boolean to prevent $watch from firing immediately)
     */
    var initializing = true;
    $scope.$watchCollection('vm.question', function () {
        if (initializing) {
            initializing = false;
            return;
        }
        vm.onUpdated();
    });

    /**
     * delete this question from the quiz
     */
    vm.delete = function () {
        vm.onDelete();
    };
}

angular.module('artemisApp').component('editDragAndDropQuestion', {
    templateUrl: 'app/quiz/edit/drag-and-drop-question/edit-drag-and-drop-question.html',
    controller: EditDragAndDropQuestionController,
    controllerAs: 'vm',
    bindings: {
        question: '=',
        onDelete: '&',
        onUpdated: '&',
        questionIndex: '<'
    }
});
