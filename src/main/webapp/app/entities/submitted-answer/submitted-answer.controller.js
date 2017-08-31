(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('SubmittedAnswerController', SubmittedAnswerController);

    SubmittedAnswerController.$inject = ['SubmittedAnswer'];

    function SubmittedAnswerController(SubmittedAnswer) {

        var vm = this;

        vm.submittedAnswers = [];

        loadAll();

        function loadAll() {
            SubmittedAnswer.query(function(result) {
                vm.submittedAnswers = result;
                vm.searchQuery = null;
            });
        }
    }
})();
