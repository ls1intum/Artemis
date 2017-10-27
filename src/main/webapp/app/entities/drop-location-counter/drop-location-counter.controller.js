(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DropLocationCounterController', DropLocationCounterController);

    DropLocationCounterController.$inject = ['DropLocationCounter'];

    function DropLocationCounterController(DropLocationCounter) {

        var vm = this;

        vm.dropLocationCounters = [];

        loadAll();

        function loadAll() {
            DropLocationCounter.query(function(result) {
                vm.dropLocationCounters = result;
                vm.searchQuery = null;
            });
        }
    }
})();
