(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('MultipleChoiceQuestionStatisticDetailController', MultipleChoiceQuestionStatisticDetailController);

    MultipleChoiceQuestionStatisticDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'MultipleChoiceQuestionStatistic', 'AnswerCounter'];

    function MultipleChoiceQuestionStatisticDetailController($scope, $rootScope, $stateParams, previousState, entity, MultipleChoiceQuestionStatistic, AnswerCounter) {
        var vm = this;

        vm.multipleChoiceQuestionStatistic = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('arTeMiSApp:multipleChoiceQuestionStatisticUpdate', function(event, result) {
            vm.multipleChoiceQuestionStatistic = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
