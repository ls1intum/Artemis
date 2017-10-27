(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('StatisticController', StatisticController);

    StatisticController.$inject = ['Statistic'];

    function StatisticController(Statistic) {

        var vm = this;

        vm.statistics = [];

        loadAll();

        function loadAll() {
            Statistic.query(function(result) {
                vm.statistics = result;
                vm.searchQuery = null;
            });
        }
    }
})();
