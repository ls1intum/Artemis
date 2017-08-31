(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('MultipleChoiceSubmittedAnswerController', MultipleChoiceSubmittedAnswerController);

    MultipleChoiceSubmittedAnswerController.$inject = ['MultipleChoiceSubmittedAnswer'];

    function MultipleChoiceSubmittedAnswerController(MultipleChoiceSubmittedAnswer) {

        var vm = this;

        vm.multipleChoiceSubmittedAnswers = [];

        loadAll();

        function loadAll() {
            MultipleChoiceSubmittedAnswer.query(function(result) {
                vm.multipleChoiceSubmittedAnswers = result;
                vm.searchQuery = null;
            });
        }
    }
})();
