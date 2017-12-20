(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DropLocationCounterDeleteController',DropLocationCounterDeleteController);

    DropLocationCounterDeleteController.$inject = ['$uibModalInstance', 'entity', 'DropLocationCounter'];

    function DropLocationCounterDeleteController($uibModalInstance, entity, DropLocationCounter) {
        var vm = this;

        vm.dropLocationCounter = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            DropLocationCounter.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
