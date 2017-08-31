(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('DragAndDropAssignmentDeleteController',DragAndDropAssignmentDeleteController);

    DragAndDropAssignmentDeleteController.$inject = ['$uibModalInstance', 'entity', 'DragAndDropAssignment'];

    function DragAndDropAssignmentDeleteController($uibModalInstance, entity, DragAndDropAssignment) {
        var vm = this;

        vm.dragAndDropAssignment = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            DragAndDropAssignment.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
