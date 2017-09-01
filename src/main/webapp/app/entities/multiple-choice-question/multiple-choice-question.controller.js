(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('MultipleChoiceQuestionController', MultipleChoiceQuestionController);

    MultipleChoiceQuestionController.$inject = ['MultipleChoiceQuestion'];

    function MultipleChoiceQuestionController(MultipleChoiceQuestion) {

        var vm = this;

        vm.multipleChoiceQuestions = [];

        loadAll();

        function loadAll() {
            MultipleChoiceQuestion.query(function(result) {
                vm.multipleChoiceQuestions = result;
                vm.searchQuery = null;
            });
        }
    }
})();
