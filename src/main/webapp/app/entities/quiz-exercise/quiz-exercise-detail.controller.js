(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizExerciseDetailController', QuizExerciseDetailController);

    QuizExerciseDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'QuizExercise', 'Question'];

    function QuizExerciseDetailController($scope, $rootScope, $stateParams, previousState, entity, QuizExercise, Question) {
        var vm = this;

        vm.quizExercise = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:quizExerciseUpdate', function(event, result) {
            vm.quizExercise = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
