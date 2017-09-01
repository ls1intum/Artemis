(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragItemDeleteController',DragItemDeleteController);

    DragItemDeleteController.$inject = ['$uibModalInstance', 'entity', 'DragItem'];

    function DragItemDeleteController($uibModalInstance, entity, DragItem) {
        var vm = this;

        vm.dragItem = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            DragItem.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
