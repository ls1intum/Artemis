(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropSubmittedAnswerDeleteController',DragAndDropSubmittedAnswerDeleteController);

    DragAndDropSubmittedAnswerDeleteController.$inject = ['$uibModalInstance', 'entity', 'DragAndDropSubmittedAnswer'];

    function DragAndDropSubmittedAnswerDeleteController($uibModalInstance, entity, DragAndDropSubmittedAnswer) {
        var vm = this;

        vm.dragAndDropSubmittedAnswer = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            DragAndDropSubmittedAnswer.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
