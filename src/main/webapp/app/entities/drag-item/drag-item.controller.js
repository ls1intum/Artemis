(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragItemController', DragItemController);

    DragItemController.$inject = ['DragItem'];

    function DragItemController(DragItem) {

        var vm = this;

        vm.dragItems = [];

        loadAll();

        function loadAll() {
            DragItem.query(function(result) {
                vm.dragItems = result;
                vm.searchQuery = null;
            });
        }
    }
})();
