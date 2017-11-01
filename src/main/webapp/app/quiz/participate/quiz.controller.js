(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizController', QuizController);

    QuizController.$inject = ['$scope', '$stateParams', 'QuizExercise'];

    function QuizController($scope, $stateParams, QuizExercise) {
        var vm = this;

        load();

        function load() {
            QuizExercise.get({id : $stateParams.id}).$promise.then(function (quizExercise) {
                vm.quizExercise = quizExercise;
            });
        }
    }
})();
