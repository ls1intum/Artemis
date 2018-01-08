EditDragAndDropQuestionController.$inject = ['$translate', '$translatePartialLoader', '$scope', 'FileUpload', '$document'];

function EditDragAndDropQuestionController($translate, $translatePartialLoader, $scope, FileUpload, $document) {

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
     * keep track of whether the user is currently dragging a dropLocation
     * @type {boolean}
     */
    var isDragging = false;

    /**
     * keep track of the currently edited / created drop location
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

        if (isDragging && currentDropLocation) {
            // update current drop locations postion and size
            currentDropLocation.posX = 100 * Math.min(mouse.x, mouse.startX) / backgroundWidth;
            currentDropLocation.posY = 100 * Math.min(mouse.y, mouse.startY) / backgroundHeight;
            currentDropLocation.width = 100 * Math.abs(mouse.x - mouse.startX) / backgroundWidth;
            currentDropLocation.height = 100 * Math.abs(mouse.y - mouse.startY) / backgroundHeight;

            // update view
            $scope.$apply();
        }
    }

    /**
     * react to mouseup events to finish dragging operations
     */
    function mouseUp() {
        if (isDragging) {
            var clickLayer = $(".click-layer");
            var backgroundWidth = clickLayer.width();
            var backgroundHeight = clickLayer.height();
            // remove drop Location if minimum dimensions are not met,
            // notify parent of new drop location otherwise
            if (currentDropLocation.width / 100 * backgroundWidth < 10 || currentDropLocation.height / 100 * backgroundHeight < 10) {
                vm.question.dropLocations.pop();
            } else {
                vm.onUpdated();
            }
            // update view
            $scope.$apply();
        }
        // update state
        isDragging = false;
        currentDropLocation = null;
    }

    /**
     * react to mouse down events on the background to start dragging
     */
    function backgroundMouseDown() {
        if (vm.question.backgroundFilePath && !isDragging) {
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
            isDragging = true;
        }
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
