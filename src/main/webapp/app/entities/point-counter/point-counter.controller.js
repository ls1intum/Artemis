(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('PointCounterController', PointCounterController);

    PointCounterController.$inject = ['PointCounter'];

    function PointCounterController(PointCounter) {

        var vm = this;

        vm.pointCounters = [];

        loadAll();

        function loadAll() {
            PointCounter.query(function(result) {
                vm.pointCounters = result;
                vm.searchQuery = null;
            });
        }
    }
})();
