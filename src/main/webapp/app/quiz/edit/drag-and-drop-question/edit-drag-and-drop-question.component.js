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

    function pseudoRandomLong() {
        return Math.floor(Math.random() * Number.MAX_SAFE_INTEGER);
    }

    $translatePartialLoader.addPart('question');
    $translatePartialLoader.addPart('dragAndDropQuestion');
    $translatePartialLoader.addPart('multipleChoiceQuestion');
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
    vm.dragItemFile = null;
    vm.isUploadingDragItemFile = false;
    vm.addHintAtCursor = addHintAtCursor;
    vm.addExplanationAtCursor = addExplanationAtCursor;
    vm.togglePreview = togglePreview;
    vm.uploadBackground = uploadBackground;
    vm.backgroundMouseDown = backgroundMouseDown;
    vm.dropLocationMouseDown = dropLocationMouseDown;
    vm.deleteDropLocation = deleteDropLocation;
    vm.duplicateDropLocation = duplicateDropLocation;
    vm.resizeMouseDown = resizeMouseDown;
    vm.addTextDragItem = addTextDragItem;
    vm.uploadDragItem = uploadDragItem;
    vm.deleteDragItem = deleteDragItem;
    vm.onDragDrop = onDragDrop;
    vm.getAssignmentsFor = getAssignmentsFor;
    vm.getAssignmentIndex = getAssignmentIndex;
    vm.deleteAssignmentsFor = deleteAssignmentsFor;
    vm.deleteAssignment = deleteAssignment;

    function togglePreview() {
        vm.showPreview = !vm.showPreview;
    }

    // set up editor
    vm.random = pseudoRandomLong();
    var editor;
    requestAnimationFrame(function () {
        editor = ace.edit("question-content-editor-" + vm.random);
        editor.setTheme("ace/theme/chrome");
        editor.getSession().setMode("ace/mode/markdown");
        editor.renderer.setShowGutter(false);
        editor.renderer.setPadding(10);
        editor.renderer.setScrollMargin(8, 8);
        editor.setHighlightActiveLine(false);
        editor.setShowPrintMargin(false);

        generateMarkdown();

        editor.on("blur", function () {
            parseMarkdown(editor.getValue());
            vm.onUpdated();
            $scope.$apply();
        });
    });

    /**
     * generate the markdown text for this question
     *
     * The markdown is generated according to these rules:
     *
     * 1. First the question text is inserted
     * 2. If hint and/or explanation exist, they are added after the text with a linebreak and tab in front of them
     *
     */
    function generateMarkdown() {
        var markdownText = (
            vm.question.text +
            (vm.question.hint ? "\n\t[-h] " + vm.question.hint : "") +
            (vm.question.explanation ? "\n\t[-e] " + vm.question.explanation : "")
        );
        editor.setValue(markdownText);
        editor.clearSelection();
    }

    /**
     * Parse the markdown and apply the result to the question's data
     *
     * The question text is split at [-h] and [-e] tags.
     *  => First part is text. Everything after [-h] is Hint, anything after [-e] is explanation
     *
     * @param questionText {string} the markdown text to parse
     */
    function parseMarkdown(questionText) {
        // split question into main text, hint and explanation
        var questionTextParts = questionText.split(/\[\-e\]|\[\-h\]/g);
        vm.question.text = questionTextParts[0].trim();
        if (questionText.indexOf("[-h]") !== -1 && questionText.indexOf("[-e]") !== -1) {
            if (questionText.indexOf("[-h]") < questionText.indexOf("[-e]")) {
                vm.question.hint = questionTextParts[1].trim();
                vm.question.explanation = questionTextParts[2].trim();
            } else {
                vm.question.hint = questionTextParts[2].trim();
                vm.question.explanation = questionTextParts[1].trim();
            }
        } else if (questionText.indexOf("[-h]") !== -1) {
            vm.question.hint = questionTextParts[1].trim();
            vm.question.explanation = null;
        } else if (questionText.indexOf("[-e]") !== -1) {
            vm.question.hint = null;
            vm.question.explanation = questionTextParts[1].trim();
        } else {
            vm.question.hint = null;
            vm.question.explanation = null;
        }
    }

    /**
     * add the markdown for a hint at the current cursor location
     */
    function addHintAtCursor() {
        var addedText = "\n\t[-h] Add a hint here (visible during the quiz via \"?\"-Button)";
        editor.focus();
        editor.insert(addedText);
        var range = editor.selection.getRange();
        range.setStart(range.start.row, range.start.column - addedText.length + 7);
        editor.selection.setRange(range);
    }

    /**
     * add the markdown for an explanation at the current cursor location
     */
    function addExplanationAtCursor() {
        var addedText = "\n\t[-e] Add an explanation here (only visible in feedback after quiz has ended)";
        editor.focus();
        editor.insert(addedText);
        var range = editor.selection.getRange();
        range.setStart(range.start.row, range.start.column - addedText.length + 7);
        editor.selection.setRange(range);
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
                vm.backgroundFile = null;
            }, function (error) {
                alert(error);
                vm.isUploadingBackgroundFile = false;
                vm.backgroundFile = null;
            });
    }

    /**
     * keep track of what the current drag action is doing
     * @type {number}
     */
    var draggingState = DragState.NONE;

    /**
     * keep track of the currently dragged drop location
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
        var clickLayer = $("#click-layer-" + vm.random);
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

        if (draggingState !== DragState.NONE) {
            switch (draggingState) {
                case DragState.CREATE:
                case DragState.RESIZE_BOTH:
                    // update current drop location's position and size
                    currentDropLocation.posX = Math.round(200 * Math.min(mouse.x, mouse.startX) / backgroundWidth);
                    currentDropLocation.posY = Math.round(200 * Math.min(mouse.y, mouse.startY) / backgroundHeight);
                    currentDropLocation.width = Math.round(200 * Math.abs(mouse.x - mouse.startX) / backgroundWidth);
                    currentDropLocation.height = Math.round(200 * Math.abs(mouse.y - mouse.startY) / backgroundHeight);
                    break;
                case DragState.MOVE:
                    // update current drop location's position
                    currentDropLocation.posX = Math.round(Math.min(Math.max(0, 200 * (mouse.x + mouse.offsetX) / backgroundWidth), 200 - currentDropLocation.width));
                    currentDropLocation.posY = Math.round(Math.min(Math.max(0, 200 * (mouse.y + mouse.offsetY) / backgroundHeight), 200 - currentDropLocation.height));
                    break;
                case DragState.RESIZE_X:
                    // update current drop location's position and size (only x-axis)
                    currentDropLocation.posX = Math.round(200 * Math.min(mouse.x, mouse.startX) / backgroundWidth);
                    currentDropLocation.width = Math.round(200 * Math.abs(mouse.x - mouse.startX) / backgroundWidth);
                    break;
                case DragState.RESIZE_Y:
                    // update current drop location's position and size (only y-axis)
                    currentDropLocation.posY = Math.round(200 * Math.min(mouse.y, mouse.startY) / backgroundHeight);
                    currentDropLocation.height = Math.round(200 * Math.abs(mouse.y - mouse.startY) / backgroundHeight);
                    break;
            }

            // update view
            $scope.$apply();
        }
    }

    /**
     * react to mouseup events to finish dragging operations
     */
    function mouseUp() {
        if (draggingState !== DragState.NONE) {
            switch (draggingState) {
                case DragState.CREATE:
                    var clickLayer = $("#click-layer-" + vm.random);
                    var backgroundWidth = clickLayer.width();
                    var backgroundHeight = clickLayer.height();
                    if (currentDropLocation.width / 200 * backgroundWidth < 14 && currentDropLocation.height / 200 * backgroundHeight < 14) {
                        // remove drop Location if too small (assume it was an accidental click/drag),
                        deleteDropLocation(currentDropLocation);
                    } else {
                        // notify parent of new drop location
                        vm.onUpdated();
                    }
                    break;
                case DragState.MOVE:
                case DragState.RESIZE_BOTH:
                case DragState.RESIZE_X:
                case DragState.RESIZE_Y:
                    // notify parent of changed drop location
                    vm.onUpdated();
                    break;
            }

            // update view
            $scope.$apply();
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
                tempID: pseudoRandomLong(),
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
        if (draggingState === DragState.NONE) {
            var clickLayer = $("#click-layer-" + vm.random);
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
    }

    /**
     * Delete the given drop location
     * @param dropLocationToDelete {object} the drop location to delete
     */
    function deleteDropLocation(dropLocationToDelete) {
        vm.question.dropLocations = vm.question.dropLocations.filter(function (dropLocation) {
            return dropLocation !== dropLocationToDelete;
        });
        deleteAssignmentsFor(dropLocationToDelete);
    }

    /**
     * Add an identical drop location to the question
     * @param dropLocation {object} the drop location to duplicate
     */
    function duplicateDropLocation(dropLocation) {
        vm.question.dropLocations.push({
            tempID: pseudoRandomLong(),
            posX: dropLocation.posX,
            posY: dropLocation.posY,
            width: dropLocation.width,
            height: dropLocation.height
        });
    }

    /**
     * react to mousedown events on the resize handles to start resizing the drop location
     *
     * @param dropLocation {object} the drop location that will be resized
     * @param resizeLocationY {string} "top", "middle" or "bottom"
     * @param resizeLocationX {string} "left", "center" or "right"
     */
    function resizeMouseDown(dropLocation, resizeLocationY, resizeLocationX) {
        if (draggingState === DragState.NONE) {
            var clickLayer = $("#click-layer-" + vm.random);
            var backgroundWidth = clickLayer.width();
            var backgroundHeight = clickLayer.height();

            // update state
            draggingState = DragState.RESIZE_BOTH;  // default is both, will be overwritten later, if needed
            currentDropLocation = dropLocation;

            switch (resizeLocationY) {
                case "top":
                    // use opposite end as startY
                    mouse.startY = (dropLocation.posY + dropLocation.height) / 200 * backgroundHeight;
                    break;
                case "middle":
                    // limit to x-axis, startY will not be used
                    draggingState = DragState.RESIZE_X;
                    break;
                case "bottom":
                    // use opposite end as startY
                    mouse.startY = dropLocation.posY / 200 * backgroundHeight;
                    break;
            }

            switch (resizeLocationX) {
                case "left":
                    // use opposite end as startX
                    mouse.startX = (dropLocation.posX + dropLocation.width) / 200 * backgroundWidth;
                    break;
                case "center":
                    // limit to y-axis, startX will not be used
                    draggingState = DragState.RESIZE_Y;
                    break;
                case "right":
                    // use opposite end as startX
                    mouse.startX = dropLocation.posX / 200 * backgroundWidth;
                    break;
            }
        }
    }

    /**
     * Add an empty Text Drag Item to the question
     */
    function addTextDragItem() {
        // add drag item to question
        if (!vm.question.dragItems) {
            vm.question.dragItems = [];
        }
        vm.question.dragItems.push({
            tempID: pseudoRandomLong(),
            text: "Text"
        });
        vm.onUpdated();
    }

    /**
     * Add a Picture Drag Item with the selected file as its picture to the question
     */
    function uploadDragItem() {
        var file = vm.dragItemFile;

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

        vm.isUploadingDragItemFile = true;
        FileUpload(file).then(
            function (result) {
                // add drag item to question
                if (!vm.question.dragItems) {
                    vm.question.dragItems = [];
                }
                vm.question.dragItems.push({
                    tempID: pseudoRandomLong(),
                    pictureFilePath: result.data.path
                });
                vm.onUpdated();
                vm.isUploadingDragItemFile = false;
                vm.dragItemFile = null;
            }, function (error) {
                alert(error);
                vm.isUploadingDragItemFile = false;
                vm.dragItemFile = null;
            });
    }

    /**
     * Delete the drag item from the question
     * @param dragItemToDelete {object} the drag item that should be deleted
     */
    function deleteDragItem(dragItemToDelete) {
        vm.question.dragItems = vm.question.dragItems.filter(function (dragItem) {
            return dragItem !== dragItemToDelete;
        });
        deleteAssignmentsFor(dragItemToDelete);
    }

    /**
     * React to a drag item being dropped on a drop location
     * @param dropLocation {object} the drop location involved
     * @param dragItem {object} the drag item involved (may be a copy at this point)
     */
    function onDragDrop(dropLocation, dragItem) {
        console.log(vm.question.correctAssignments);
        // replace dragItem with original (because it may be a copy)
        dragItem = vm.question.dragItems.find(function (originalDragItem) {
            return dragItem.id ? originalDragItem.id === dragItem.id : originalDragItem.tempID === dragItem.tempID;
        });
        if (!dragItem) {
            // drag item was not found in question => do nothing
            return;
        }

        if (!vm.question.correctAssignments) {
            vm.question.correctAssignments = [];
        }

        // remove assignments that contain the dropLocation or dragItem
        // deleteAssignmentsFor(dropLocation);
        // deleteAssignmentsFor(dragItem);

        // add this assignment
        vm.question.correctAssignments.push({
            location: dropLocation,
            item: dragItem
        });

        vm.onUpdated();
    }

    /**
     * Get the assignment index for the given assignment
     * @param assignment {object} the assignment we want to get an index for
     * @return {number} the index of the assignment (starting with 1), or 0 if unassigned
     */
    function getAssignmentIndex(assignment) {
        var visitedLocations = [];
        if (vm.question.correctAssignments.some(function (correctAssignment) {
                if (!visitedLocations.some(function (location) {
                        return isSameDropLocationOrDragItem(location, correctAssignment.location);
                    })) {
                    visitedLocations.push(correctAssignment.location);
                }
                return isSameDropLocationOrDragItem(correctAssignment.location, assignment.location);
            })) {
            return visitedLocations.length;
        } else {
            return 0;
        }
    }

    function getAssignmentsFor(dropLocationOrDragItem) {
        if (!vm.question.correctAssignments) {
            vm.question.correctAssignments = [];
        }
        return vm.question.correctAssignments.filter(function (assignment) {
            return isSameDropLocationOrDragItem(assignment.location, dropLocationOrDragItem) ||
                isSameDropLocationOrDragItem(assignment.item, dropLocationOrDragItem);
        });
    }

    /**
     * Remove the assignment for the given drop location or drag item
     * @param dropLocationOrDragItem {object} a drop location or drag item
     */
    function deleteAssignmentsFor(dropLocationOrDragItem) {
        if (!vm.question.correctAssignments) {
            vm.question.correctAssignments = [];
        }
        vm.question.correctAssignments = vm.question.correctAssignments.filter(function (assignment) {
            return !isSameDropLocationOrDragItem(assignment.location, dropLocationOrDragItem) &&
                !isSameDropLocationOrDragItem(assignment.item, dropLocationOrDragItem);
        });
    }

    /**
     * delete the given assignment from the question
     * @param assignmentToDelete {object} the assignment to delete
     */
    function deleteAssignment(assignmentToDelete) {
        if (!vm.question.correctAssignments) {
            vm.question.correctAssignments = [];
        }
        vm.question.correctAssignments = vm.question.correctAssignments.filter(function (assignment) {
            return assignment !== assignmentToDelete;
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
