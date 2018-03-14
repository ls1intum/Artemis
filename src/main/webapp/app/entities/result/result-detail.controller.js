(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ResultDetailController', ResultDetailController);

    ResultDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity'];

    function ResultDetailController($scope, $rootScope, $stateParams, previousState, entity) {
        var vm = this;

        vm.result = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:resultUpdate', function(event, result) {
            vm.result = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
