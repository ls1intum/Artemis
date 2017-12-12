(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('StatisticCounterController', StatisticCounterController);

    StatisticCounterController.$inject = ['StatisticCounter'];

    function StatisticCounterController(StatisticCounter) {

        var vm = this;

        vm.statisticCounters = [];

        loadAll();

        function loadAll() {
            StatisticCounter.query(function(result) {
                vm.statisticCounters = result;
                vm.searchQuery = null;
            });
        }
    }
})();
