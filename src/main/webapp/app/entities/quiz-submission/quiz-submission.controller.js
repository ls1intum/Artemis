(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('QuizSubmissionController', QuizSubmissionController);

    QuizSubmissionController.$inject = ['QuizSubmission'];

    function QuizSubmissionController(QuizSubmission) {

        var vm = this;

        vm.quizSubmissions = [];

        loadAll();

        function loadAll() {
            QuizSubmission.query(function(result) {
                vm.quizSubmissions = result;
                vm.searchQuery = null;
            });
        }
    }
})();
