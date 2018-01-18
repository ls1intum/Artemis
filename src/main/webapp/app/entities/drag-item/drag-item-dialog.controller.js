(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragItemDialogController', DragItemDialogController);

    DragItemDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'DragItem', 'DragAndDropQuestion'];

    function DragItemDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, DragItem, DragAndDropQuestion) {
        var vm = this;

        vm.dragItem = entity;
        vm.clear = clear;
        vm.save = save;
        vm.draganddropquestions = DragAndDropQuestion.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.dragItem.id !== null) {
                DragItem.update(vm.dragItem, onSaveSuccess, onSaveError);
            } else {
                DragItem.save(vm.dragItem, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:dragItemUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
