(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('LtiUserIdDetailController', LtiUserIdDetailController);

    LtiUserIdDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'LtiUserId', 'User'];

    function LtiUserIdDetailController($scope, $rootScope, $stateParams, previousState, entity, LtiUserId, User) {
        var vm = this;

        vm.ltiUserId = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:ltiUserIdUpdate', function(event, result) {
            vm.ltiUserId = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
