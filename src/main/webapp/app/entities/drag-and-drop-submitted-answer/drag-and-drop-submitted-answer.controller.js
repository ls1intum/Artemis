(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropSubmittedAnswerController', DragAndDropSubmittedAnswerController);

    DragAndDropSubmittedAnswerController.$inject = ['DragAndDropSubmittedAnswer'];

    function DragAndDropSubmittedAnswerController(DragAndDropSubmittedAnswer) {

        var vm = this;

        vm.dragAndDropSubmittedAnswers = [];

        loadAll();

        function loadAll() {
            DragAndDropSubmittedAnswer.query(function(result) {
                vm.dragAndDropSubmittedAnswers = result;
                vm.searchQuery = null;
            });
        }
    }
})();
