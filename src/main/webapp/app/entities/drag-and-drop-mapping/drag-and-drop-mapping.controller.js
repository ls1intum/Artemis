(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropMappingController', DragAndDropMappingController);

    DragAndDropMappingController.$inject = ['DragAndDropMapping'];

    function DragAndDropMappingController(DragAndDropMapping) {

        var vm = this;

        vm.dragAndDropMappings = [];

        loadAll();

        function loadAll() {
            DragAndDropMapping.query(function(result) {
                vm.dragAndDropMappings = result;
                vm.searchQuery = null;
            });
        }
    }
})();
