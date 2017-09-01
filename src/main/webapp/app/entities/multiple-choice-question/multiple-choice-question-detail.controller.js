(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('MultipleChoiceQuestionDetailController', MultipleChoiceQuestionDetailController);

    MultipleChoiceQuestionDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'MultipleChoiceQuestion', 'AnswerOption'];

    function MultipleChoiceQuestionDetailController($scope, $rootScope, $stateParams, previousState, entity, MultipleChoiceQuestion, AnswerOption) {
        var vm = this;

        vm.multipleChoiceQuestion = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:multipleChoiceQuestionUpdate', function(event, result) {
            vm.multipleChoiceQuestion = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
