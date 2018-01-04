EditDragAndDropQuestionController.$inject = ['$translate', '$translatePartialLoader', '$scope', 'FileUpload'];

function EditDragAndDropQuestionController($translate, $translatePartialLoader, $scope, FileUpload) {

    $translatePartialLoader.addPart('question');
    $translatePartialLoader.addPart('dragAndDropQuestion');
    $translate.refresh();

    var vm = this;
    vm.scoringTypeOptions = [
        {
            key: "ALL_OR_NOTHING",
            label: "All or Nothing"
        },
        {
            key: "PROPORTIONAL_CORRECT_OPTIONS",
            label: "Proportional Points for Correct Answer Options"
        },
        {
            key: "TRUE_FALSE_NEUTRAL",
            label: "True / False / No Answer"
        }
    ];

    vm.backgroundFile = null;
    vm.showPreview = false;
    vm.isUploadingBackgroundFile = false;
    vm.togglePreview = togglePreview;
    vm.uploadBackground = uploadBackground;

    function togglePreview() {
        vm.showPreview = !vm.showPreview;
    }

    /**
     * Upload the selected file (from "Upload Background") and use it for the question's backgroundFilePath
     */
    function uploadBackground() {
        var file = vm.backgroundFile;

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
