(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('PointCounterDeleteController',PointCounterDeleteController);

    PointCounterDeleteController.$inject = ['$uibModalInstance', 'entity', 'PointCounter'];

    function PointCounterDeleteController($uibModalInstance, entity, PointCounter) {
        var vm = this;

        vm.pointCounter = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            PointCounter.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
