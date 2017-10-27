(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('AnswerCounterDetailController', AnswerCounterDetailController);

    AnswerCounterDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'AnswerCounter', 'MultipleChoiceStatistic', 'AnswerOption'];

    function AnswerCounterDetailController($scope, $rootScope, $stateParams, previousState, entity, AnswerCounter, MultipleChoiceStatistic, AnswerOption) {
        var vm = this;

        vm.answerCounter = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('arTeMiSApp:answerCounterUpdate', function(event, result) {
            vm.answerCounter = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
