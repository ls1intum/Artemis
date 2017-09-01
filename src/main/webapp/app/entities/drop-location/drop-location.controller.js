(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DropLocationController', DropLocationController);

    DropLocationController.$inject = ['DropLocation'];

    function DropLocationController(DropLocation) {

        var vm = this;

        vm.dropLocations = [];

        loadAll();

        function loadAll() {
            DropLocation.query(function(result) {
                vm.dropLocations = result;
                vm.searchQuery = null;
            });
        }
    }
})();
