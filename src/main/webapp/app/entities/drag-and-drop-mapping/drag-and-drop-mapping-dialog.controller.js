(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropMappingDialogController', DragAndDropMappingDialogController);

    DragAndDropMappingDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'DragAndDropMapping', 'DragItem', 'DropLocation', 'DragAndDropSubmittedAnswer', 'DragAndDropQuestion'];

    function DragAndDropMappingDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, DragAndDropMapping, DragItem, DropLocation, DragAndDropSubmittedAnswer, DragAndDropQuestion) {
        var vm = this;

        vm.dragAndDropMapping = entity;
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
            if (vm.dragAndDropMapping.id !== null) {
                DragAndDropMapping.update(vm.dragAndDropMapping, onSaveSuccess, onSaveError);
            } else {
                DragAndDropMapping.save(vm.dragAndDropMapping, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:dragAndDropMappingUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
