(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('MultipleChoiceSubmittedAnswerDetailController', MultipleChoiceSubmittedAnswerDetailController);

    MultipleChoiceSubmittedAnswerDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'MultipleChoiceSubmittedAnswer', 'AnswerOption'];

    function MultipleChoiceSubmittedAnswerDetailController($scope, $rootScope, $stateParams, previousState, entity, MultipleChoiceSubmittedAnswer, AnswerOption) {
        var vm = this;

        vm.multipleChoiceSubmittedAnswer = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('exerciseApplicationApp:multipleChoiceSubmittedAnswerUpdate', function(event, result) {
            vm.multipleChoiceSubmittedAnswer = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
