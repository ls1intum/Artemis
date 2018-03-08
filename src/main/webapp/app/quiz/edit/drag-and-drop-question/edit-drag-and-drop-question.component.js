(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('EditDragAndDropQuestionController', EditDragAndDropQuestionController);

    EditDragAndDropQuestionController.$inject = ['$translate', '$translatePartialLoader', '$scope', 'FileUpload', '$document', 'MAX_FILE_SIZE', 'ArtemisMarkdown', 'DragAndDropQuestionUtil'];

    function EditDragAndDropQuestionController($translate, $translatePartialLoader, $scope, FileUpload, $document, MAX_FILE_SIZE, ArtemisMarkdown, DragAndDropQuestionUtil) {

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
            },
            {
                key: "PROPORTIONAL_WITH_PENALTY",
                label: "Proportional with Penalty"
            }
        ];
        //create BackUp for resets
        var backUpQuestion = angular.copy(vm.question);

        vm.dragItemPicture = null;

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
        vm.uploadPictureForDragItemChange = uploadPictureForDragItemChange;
        vm.deleteDragItem = deleteDragItem;
        vm.onDragDrop = onDragDrop;
        vm.getMappingsForDropLocation = getMappingsForDropLocation;
        vm.getMappingsForDragItem = getMappingsForDragItem;
        vm.getMappingIndex = getMappingIndex;
        vm.deleteMappingsForDropLocation = deleteMappingsForDropLocation;
        vm.deleteMapping = deleteMapping;
        vm.changeToTextDragItem = changeToTextDragItem;
        vm.changeToPictureDragItem = changeToPictureDragItem;
        vm.resetQuestionTitle = resetQuestionTitle;
        vm.resetQuestionText = resetQuestionText;
        vm.resetQuestion = resetQuestion;
        vm.resetBackground = resetBackground;
        vm.resetDropLocation = resetDropLocation;
        vm.resetDragItem = resetDragItem;


        function togglePreview() {
            vm.showPreview = !vm.showPreview;
        }

        /**
         * Prevent page from jumping back to last clicked/selected element on drop
         */
        $scope.$on('ANGULAR_DRAG_START', function () {
            window.getSelection().removeAllRanges();
        });

        vm.random = pseudoRandomLong();
        var editor;

        setUpQuestionEditor();

        /**
         * set up Question text editor
         */
        function setUpQuestionEditor() {
            requestAnimationFrame(function () {
                editor = ace.edit("question-content-editor-" + vm.random);
                editor.setTheme("ace/theme/chrome");
                editor.getSession().setMode("ace/mode/markdown");
                editor.renderer.setShowGutter(false);
                editor.renderer.setPadding(10);
                editor.renderer.setScrollMargin(8, 8);
                editor.setHighlightActiveLine(false);
                editor.setShowPrintMargin(false);

                // generate markdown from question and show result in editor
                editor.setValue(ArtemisMarkdown.generateTextHintExplanation(vm.question));
                editor.clearSelection();

                editor.on("blur", function () {
                    // parse the markdown in the editor and update question accordingly
                    ArtemisMarkdown.parseTextHintExplanation(editor.getValue(), vm.question);
                    vm.onUpdated();
                    $scope.$apply();
                });
            });
        }

        /**
         * add the markdown for a hint at the current cursor location
         */
        function addHintAtCursor() {
            ArtemisMarkdown.addHintAtCursor(editor);
        }

        /**
         * add the markdown for an explanation at the current cursor location
         */
        function addExplanationAtCursor() {
            ArtemisMarkdown.addExplanationAtCursor(editor);
        }

        /**
         * Upload the selected file (from "Upload Background") and use it for the question's backgroundFilePath
         */
        function uploadBackground() {
            var file = vm.backgroundFile;

            vm.isUploadingBackgroundFile = true;
            FileUpload(file).then(
                function (result) {
                    vm.question.backgroundFilePath = result.data.path;
                    vm.isUploadingBackgroundFile = false;
                    vm.backgroundFile = null;
                }, function (error) {
                    if (error && error.data) {
                        alert(error.data.message);
                    }
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
            deleteMappingsForDropLocation(dropLocationToDelete);
        }

        /**
         * Add an identical drop location to the question
         * @param dropLocation {object} the drop location to duplicate
         */
        function duplicateDropLocation(dropLocation) {
            vm.question.dropLocations.push({
                tempID: pseudoRandomLong(),
                posX: dropLocation.posX + dropLocation.width < 197 ? dropLocation.posX + 3 : Math.max(0, dropLocation.posX - 3),
                posY: dropLocation.posY + dropLocation.height < 197 ? dropLocation.posY + 3 : Math.max(0, dropLocation.posY - 3),
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
                    if (error && error.data) {
                        alert(error.data.message);
                    }
                    vm.isUploadingDragItemFile = false;
                    vm.dragItemFile = null;
                });
        }

        /**
         * Upload a Picture for Drag Item Change with the selected file as its picture
         */
        function uploadPictureForDragItemChange() {
            var file = vm.dragItemFile;

            vm.isUploadingDragItemFile = true;
            FileUpload(file).then(
                function (result) {

                    vm.dragItemPicture = result.data.path;

                    vm.onUpdated();
                    vm.isUploadingDragItemFile = false;
                    vm.dragItemFile = null;
                }, function (error) {
                    if (error && error.data) {
                        alert(error.data.message);
                    }
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
            deleteMappingsForDragItem(dragItemToDelete);
        }

        /**
         * React to a drag item being dropped on a drop location
         * @param dropLocation {object} the drop location involved
         * @param dragItem {object} the drag item involved (may be a copy at this point)
         */
        function onDragDrop(dropLocation, dragItem) {
            // replace dragItem with original (because it may be a copy)
            dragItem = vm.question.dragItems.find(function (originalDragItem) {
                return dragItem.id ? originalDragItem.id === dragItem.id : originalDragItem.tempID === dragItem.tempID;
            });
            if (!dragItem) {
                // drag item was not found in question => do nothing
                return;
            }

            if (!vm.question.correctMappings) {
                vm.question.correctMappings = [];
            }

            // check if this mapping already exists
            if (!vm.question.correctMappings.some(function (existingMapping) {
                    return (
                        DragAndDropQuestionUtil.isSameDropLocationOrDragItem(existingMapping.dropLocation, dropLocation)
                        &&
                        DragAndDropQuestionUtil.isSameDropLocationOrDragItem(existingMapping.dragItem, dragItem)
                    );
                })) {
                // mapping doesn't exit yet => add this mapping
                vm.question.correctMappings.push({
                    dropLocation: dropLocation,
                    dragItem: dragItem
                });

                // notify parent of changes
                vm.onUpdated();
            }
        }

        /**
         * Get the mapping index for the given mapping
         * @param mapping {object} the mapping we want to get an index for
         * @return {number} the index of the mapping (starting with 1), or 0 if unassigned
         */
        function getMappingIndex(mapping) {
            var visitedDropLocations = [];
            if (vm.question.correctMappings.some(function (correctMapping) {
                    if (!visitedDropLocations.some(function (dropLocation) {
                            return DragAndDropQuestionUtil.isSameDropLocationOrDragItem(dropLocation, correctMapping.dropLocation);
                        })) {
                        visitedDropLocations.push(correctMapping.dropLocation);
                    }
                    return DragAndDropQuestionUtil.isSameDropLocationOrDragItem(correctMapping.dropLocation, mapping.dropLocation);
                })) {
                return visitedDropLocations.length;
            } else {
                return 0;
            }
        }

        /**
         * Get all mappings that involve the given drop location
         *
         * @param dropLocation {object} the drop location for which we want to get all mappings
         * @return {Array} all mappings that belong to the given drop location
         */
        function getMappingsForDropLocation(dropLocation) {
            if (!vm.question.correctMappings) {
                vm.question.correctMappings = [];
            }
            return vm.question.correctMappings.filter(function (mapping) {
                return DragAndDropQuestionUtil.isSameDropLocationOrDragItem(mapping.dropLocation, dropLocation);
            });
        }

        /**
         * Get all mappings that involve the given drag item
         *
         * @param dragItem {object} the drag item for which we want to get all mappings
         * @return {Array} all mappings that belong to the given drag item
         */
        function getMappingsForDragItem(dragItem) {
            if (!vm.question.correctMappings) {
                vm.question.correctMappings = [];
            }
            return vm.question.correctMappings.filter(function (mapping) {
                return DragAndDropQuestionUtil.isSameDropLocationOrDragItem(mapping.dragItem, dragItem);
            });
        }

        /**
         * Delete all mappings for the given drop location
         *
         * @param dropLocation {object} the drop location for which we want to delete all mappings
         */
        function deleteMappingsForDropLocation(dropLocation) {
            if (!vm.question.correctMappings) {
                vm.question.correctMappings = [];
            }
            vm.question.correctMappings = vm.question.correctMappings.filter(function (mapping) {
                return !DragAndDropQuestionUtil.isSameDropLocationOrDragItem(mapping.dropLocation, dropLocation);
            });
        }

        /**
         * Delete all mappings for the given drag item
         *
         * @param dragItem {object} the drag item for which we want to delete all mappings
         */
        function deleteMappingsForDragItem(dragItem) {
            if (!vm.question.correctMappings) {
                vm.question.correctMappings = [];
            }
            vm.question.correctMappings = vm.question.correctMappings.filter(function (mapping) {
                return !DragAndDropQuestionUtil.isSameDropLocationOrDragItem(mapping.dragItem, dragItem);
            });
        }

        /**
         * delete the given mapping from the question
         * @param mappingToDelete {object} the mapping to delete
         */
        function deleteMapping(mappingToDelete) {
            if (!vm.question.correctMappings) {
                vm.question.correctMappings = [];
            }
            vm.question.correctMappings = vm.question.correctMappings.filter(function (mapping) {
                return mapping !== mappingToDelete;
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
         * move this question one position up
         */
        vm.moveUp = function () {
            vm.onMoveUp();
        };

        /**
         * move this question one position down
         */

        vm.moveDown = function () {
            vm.onMoveDown();
        };
        /**
         * delete this question from the quiz
         */
        vm.delete = function () {
            vm.onDelete();
        };

        /**
         * Change Picture-Drag-Item to Text-Drag-Item with text: "Text"
         *
         * @param dragItem {dragItem} the dragItem, which will be changed
         */
        function changeToTextDragItem(dragItem) {
            dragItem.pictureFilePath = null;
            dragItem.text = "Text";
        }

        /**
         * Change Text-Drag-Item to Picture-Drag-Item with PictureFile: vm.dragItemFile
         *
         * @param dragItem {dragItem} the dragItem, which will be changed
         */
        function changeToPictureDragItem(dragItem) {
            var file = vm.dragItemFile;

            vm.isUploadingDragItemFile = true;
            FileUpload(file).then(
                function (result) {

                    vm.dragItemPicture = result.data.path;

                    vm.onUpdated();
                    vm.isUploadingDragItemFile = false;
                    if (vm.dragItemPicture !== null) {
                        dragItem.text = null;
                        dragItem.pictureFilePath = angular.copy(vm.dragItemPicture);
                    }
                }, function (error) {
                    if (error && error.data) {
                        alert(error.data.message);
                    }
                    vm.isUploadingDragItemFile = false;
                    vm.dragItemFile = null;
                });


        }

        /**
         * Resets the question title
         */
        function resetQuestionTitle() {
            vm.question.title = angular.copy(backUpQuestion.title);
        }

        /**
         * Resets the question text
         */
        function resetQuestionText() {
            vm.question.text = angular.copy(backUpQuestion.text);
            vm.question.expalanation = angular.copy(backUpQuestion.expalanation);
            vm.question.hint = angular.copy(backUpQuestion.hint);
            setUpQuestionEditor();
        }

        /**
         * Resets the whole question
         */
        function resetQuestion() {
            vm.question.title = angular.copy(backUpQuestion.title);
            vm.question.invalid = angular.copy(backUpQuestion.invalid);
            vm.question.randomizeOrder = angular.copy(backUpQuestion.randomizeOrder);
            vm.question.scoringType = angular.copy(backUpQuestion.scoringType);
            resetBackground();
            vm.question.dropLocations = angular.copy(backUpQuestion.dropLocations);
            vm.question.dragItems = angular.copy(backUpQuestion.dragItems);
            vm.question.correctMappings = angular.copy(backUpQuestion.correctMappings);
            resetQuestionText();
        }

        /**
         * Resets background-picture
         */
        function resetBackground() {
            vm.question.backgroundFilePath = angular.copy(backUpQuestion.backgroundFilePath);
            vm.backgroundFile = null;
            vm.isUploadingBackgroundFile = false;
        }

        /**
         * Resets the dropLocation
         *
         * @param dropLocation {dropLocation} the dropLocation, which will be reset
         */
        function resetDropLocation(dropLocation) {
            for (var i = 0; i < backUpQuestion.dropLocations.length; i++) {
                if (backUpQuestion.dropLocations[i].id === dropLocation.id) {

                    //find correct answer if they have another order
                    vm.question.dropLocations[vm.question.dropLocations.indexOf(dropLocation)] = angular.copy(backUpQuestion.dropLocations[i]);
                    dropLocation = angular.copy(backUpQuestion.dropLocations[i]);

                }
            }
        }

        /**
         * Resets the dragItem
         *
         * @param dragItem {dragItem} the dragItem, which will be reset
         */
        function resetDragItem(dragItem) {
            for (var i = 0; i < backUpQuestion.dragItems.length; i++) {
                if (backUpQuestion.dragItems[i].id === dragItem.id) {

                    //find correct answer if they have another order
                    vm.question.dragItems[vm.question.dragItems.indexOf(dragItem)] = angular.copy(backUpQuestion.dragItems[i]);
                    dragItem = angular.copy(backUpQuestion.dragItems[i]);

                }
            }
        }
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
})();
