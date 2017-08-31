(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('SubmittedAnswerDetailController', SubmittedAnswerDetailController);

    SubmittedAnswerDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'SubmittedAnswer', 'QuizSubmission'];

    function SubmittedAnswerDetailController($scope, $rootScope, $stateParams, previousState, entity, SubmittedAnswer, QuizSubmission) {
        var vm = this;

        vm.submittedAnswer = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('exerciseApplicationApp:submittedAnswerUpdate', function(event, result) {
            vm.submittedAnswer = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
