(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('MultipleChoiceStatisticController', MultipleChoiceStatisticController);

    MultipleChoiceStatisticController.$inject = ['MultipleChoiceStatistic'];

    function MultipleChoiceStatisticController(MultipleChoiceStatistic) {

        var vm = this;

        vm.multipleChoiceStatistics = [];

        loadAll();

        function loadAll() {
            MultipleChoiceStatistic.query(function(result) {
                vm.multipleChoiceStatistics = result;
                vm.searchQuery = null;
            });
        }
    }
})();
