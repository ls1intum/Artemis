(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('SubmissionDetailController', SubmissionDetailController);

    SubmissionDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'Submission'];

    function SubmissionDetailController($scope, $rootScope, $stateParams, previousState, entity, Submission) {
        var vm = this;

        vm.submission = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('exerciseApplicationApp:submissionUpdate', function(event, result) {
            vm.submission = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
