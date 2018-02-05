(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('StatisticDetailController', StatisticDetailController);

    StatisticDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'Statistic'];

    function StatisticDetailController($scope, $rootScope, $stateParams, previousState, entity, Statistic) {
        var vm = this;

        vm.statistic = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:statisticUpdate', function(event, result) {
            vm.statistic = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
