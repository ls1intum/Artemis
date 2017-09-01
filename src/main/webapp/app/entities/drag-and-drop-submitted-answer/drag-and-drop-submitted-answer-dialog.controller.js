(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropSubmittedAnswerDialogController', DragAndDropSubmittedAnswerDialogController);

    DragAndDropSubmittedAnswerDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'DragAndDropSubmittedAnswer', 'DragAndDropAssignment'];

    function DragAndDropSubmittedAnswerDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, DragAndDropSubmittedAnswer, DragAndDropAssignment) {
        var vm = this;

        vm.dragAndDropSubmittedAnswer = entity;
        vm.clear = clear;
        vm.save = save;
        vm.draganddropassignments = DragAndDropAssignment.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.dragAndDropSubmittedAnswer.id !== null) {
                DragAndDropSubmittedAnswer.update(vm.dragAndDropSubmittedAnswer, onSaveSuccess, onSaveError);
            } else {
                DragAndDropSubmittedAnswer.save(vm.dragAndDropSubmittedAnswer, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:dragAndDropSubmittedAnswerUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
