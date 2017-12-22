(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropQuestionStatisticDeleteController',DragAndDropQuestionStatisticDeleteController);

    DragAndDropQuestionStatisticDeleteController.$inject = ['$uibModalInstance', 'entity', 'DragAndDropQuestionStatistic'];

    function DragAndDropQuestionStatisticDeleteController($uibModalInstance, entity, DragAndDropQuestionStatistic) {
        var vm = this;

        vm.dragAndDropQuestionStatistic = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            DragAndDropQuestionStatistic.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
