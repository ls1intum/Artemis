(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('QuizExerciseDetailController', QuizExerciseDetailController);

    QuizExerciseDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'QuizExercise', 'Question'];

    function QuizExerciseDetailController($scope, $rootScope, $stateParams, previousState, entity, QuizExercise, Question) {
        var vm = this;

        vm.quizExercise = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('exerciseApplicationApp:quizExerciseUpdate', function(event, result) {
            vm.quizExercise = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
