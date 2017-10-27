(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropStatisticController', DragAndDropStatisticController);

    DragAndDropStatisticController.$inject = ['DragAndDropStatistic'];

    function DragAndDropStatisticController(DragAndDropStatistic) {

        var vm = this;

        vm.dragAndDropStatistics = [];

        loadAll();

        function loadAll() {
            DragAndDropStatistic.query(function(result) {
                vm.dragAndDropStatistics = result;
                vm.searchQuery = null;
            });
        }
    }
})();
