(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('DropLocationDeleteController',DropLocationDeleteController);

    DropLocationDeleteController.$inject = ['$uibModalInstance', 'entity', 'DropLocation'];

    function DropLocationDeleteController($uibModalInstance, entity, DropLocation) {
        var vm = this;

        vm.dropLocation = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            DropLocation.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
