(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizSubmissionDetailController', QuizSubmissionDetailController);

    QuizSubmissionDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'QuizSubmission', 'SubmittedAnswer'];

    function QuizSubmissionDetailController($scope, $rootScope, $stateParams, previousState, entity, QuizSubmission, SubmittedAnswer) {
        var vm = this;

        vm.quizSubmission = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:quizSubmissionUpdate', function(event, result) {
            vm.quizSubmission = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
