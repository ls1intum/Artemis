(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropQuestionController', DragAndDropQuestionController);

    DragAndDropQuestionController.$inject = ['DragAndDropQuestion'];

    function DragAndDropQuestionController(DragAndDropQuestion) {

        var vm = this;

        vm.dragAndDropQuestions = [];

        loadAll();

        function loadAll() {
            DragAndDropQuestion.query(function(result) {
                vm.dragAndDropQuestions = result;
                vm.searchQuery = null;
            });
        }
    }
})();
