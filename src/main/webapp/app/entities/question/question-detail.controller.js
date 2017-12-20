(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuestionDetailController', QuestionDetailController);

    QuestionDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'Question', 'QuestionStatistic', 'QuizExercise'];

    function QuestionDetailController($scope, $rootScope, $stateParams, previousState, entity, Question, QuestionStatistic, QuizExercise) {
        var vm = this;

        vm.question = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:questionUpdate', function(event, result) {
            vm.question = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
