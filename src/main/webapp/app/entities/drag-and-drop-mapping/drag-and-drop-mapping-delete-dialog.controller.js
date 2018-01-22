(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropMappingDeleteController',DragAndDropMappingDeleteController);

    DragAndDropMappingDeleteController.$inject = ['$uibModalInstance', 'entity', 'DragAndDropMapping'];

    function DragAndDropMappingDeleteController($uibModalInstance, entity, DragAndDropMapping) {
        var vm = this;

        vm.dragAndDropMapping = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            DragAndDropMapping.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
