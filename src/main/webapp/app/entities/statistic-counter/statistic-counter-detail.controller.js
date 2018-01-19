(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('StatisticCounterDetailController', StatisticCounterDetailController);

    StatisticCounterDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'StatisticCounter'];

    function StatisticCounterDetailController($scope, $rootScope, $stateParams, previousState, entity, StatisticCounter) {
        var vm = this;

        vm.statisticCounter = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:statisticCounterUpdate', function(event, result) {
            vm.statisticCounter = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
