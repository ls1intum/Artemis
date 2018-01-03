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
    vm.togglePreview = togglePreview;
    vm.uploadBackground = uploadBackground;

    function togglePreview() {
        vm.showPreview = !vm.showPreview;
    }

    function uploadBackground() {
        var file = vm.backgroundFile;

        FileUpload.uploadFileToUrl(file).then(
            function(result){
                console.log(result);
                console.log(FileUpload.getResponse());
            }, function(error) {
                alert(error);
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
