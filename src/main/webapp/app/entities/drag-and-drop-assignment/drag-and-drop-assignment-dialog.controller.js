(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropAssignmentDialogController', DragAndDropAssignmentDialogController);

    DragAndDropAssignmentDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'DragAndDropAssignment', 'DragItem', 'DropLocation', 'DragAndDropSubmittedAnswer', 'DragAndDropQuestion'];

    function DragAndDropAssignmentDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, DragAndDropAssignment, DragItem, DropLocation, DragAndDropSubmittedAnswer, DragAndDropQuestion) {
        var vm = this;

        vm.dragAndDropAssignment = entity;
        vm.clear = clear;
        vm.save = save;
        vm.dragitems = DragItem.query();
        vm.droplocations = DropLocation.query();
        vm.draganddropsubmittedanswers = DragAndDropSubmittedAnswer.query();
        vm.draganddropquestions = DragAndDropQuestion.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.dragAndDropAssignment.id !== null) {
                DragAndDropAssignment.update(vm.dragAndDropAssignment, onSaveSuccess, onSaveError);
            } else {
                DragAndDropAssignment.save(vm.dragAndDropAssignment, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:dragAndDropAssignmentUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
