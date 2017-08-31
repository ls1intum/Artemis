(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('DragAndDropQuestionDeleteController',DragAndDropQuestionDeleteController);

    DragAndDropQuestionDeleteController.$inject = ['$uibModalInstance', 'entity', 'DragAndDropQuestion'];

    function DragAndDropQuestionDeleteController($uibModalInstance, entity, DragAndDropQuestion) {
        var vm = this;

        vm.dragAndDropQuestion = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            DragAndDropQuestion.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
