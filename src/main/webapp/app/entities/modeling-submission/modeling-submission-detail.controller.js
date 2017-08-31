(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ModelingSubmissionDetailController', ModelingSubmissionDetailController);

    ModelingSubmissionDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'ModelingSubmission'];

    function ModelingSubmissionDetailController($scope, $rootScope, $stateParams, previousState, entity, ModelingSubmission) {
        var vm = this;

        vm.modelingSubmission = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('exerciseApplicationApp:modelingSubmissionUpdate', function(event, result) {
            vm.modelingSubmission = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
