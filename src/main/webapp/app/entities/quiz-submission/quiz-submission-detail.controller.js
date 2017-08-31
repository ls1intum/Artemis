(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('QuizSubmissionDetailController', QuizSubmissionDetailController);

    QuizSubmissionDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'QuizSubmission', 'SubmittedAnswer'];

    function QuizSubmissionDetailController($scope, $rootScope, $stateParams, previousState, entity, QuizSubmission, SubmittedAnswer) {
        var vm = this;

        vm.quizSubmission = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('exerciseApplicationApp:quizSubmissionUpdate', function(event, result) {
            vm.quizSubmission = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
