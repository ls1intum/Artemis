(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('QuizExerciseController', QuizExerciseController);

    QuizExerciseController.$inject = ['QuizExercise'];

    function QuizExerciseController(QuizExercise) {

        var vm = this;

        vm.quizExercises = [];

        loadAll();

        function loadAll() {
            QuizExercise.query(function(result) {
                vm.quizExercises = result;
                vm.searchQuery = null;
            });
        }
    }
})();
