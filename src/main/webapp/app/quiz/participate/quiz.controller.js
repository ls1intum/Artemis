(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizController', QuizController);

    QuizController.$inject = ['$scope', '$stateParams', 'QuizExerciseForStudent'];

    function QuizController($scope, $stateParams, QuizExerciseForStudent) {
        var vm = this;

        load();

        function load() {
            QuizExerciseForStudent.get({id : $stateParams.id}).$promise.then(function (quizExercise) {
                vm.quizExercise = quizExercise;
            });
        }
    }
})();
