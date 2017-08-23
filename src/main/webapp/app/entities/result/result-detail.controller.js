(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ResultDetailController', ResultDetailController);

    ResultDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'Result', 'Participation'];

    function ResultDetailController($scope, $rootScope, $stateParams, previousState, entity, Result, Participation) {
        var vm = this;

        vm.result = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('exerciseApplicationApp:resultUpdate', function(event, result) {
            vm.result = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
