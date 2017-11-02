(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizPointStatisticDetailController', QuizPointStatisticDetailController);

    QuizPointStatisticDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'QuizPointStatistic', 'PointCounter', 'QuizExercise'];

    function QuizPointStatisticDetailController($scope, $rootScope, $stateParams, previousState, entity, QuizPointStatistic, PointCounter, QuizExercise) {
        var vm = this;

        vm.quizPointStatistic = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('arTeMiSApp:quizPointStatisticUpdate', function(event, result) {
            vm.quizPointStatistic = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
