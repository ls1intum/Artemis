(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropStatisticDeleteController',DragAndDropStatisticDeleteController);

    DragAndDropStatisticDeleteController.$inject = ['$uibModalInstance', 'entity', 'DragAndDropStatistic'];

    function DragAndDropStatisticDeleteController($uibModalInstance, entity, DragAndDropStatistic) {
        var vm = this;

        vm.dragAndDropStatistic = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            DragAndDropStatistic.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
