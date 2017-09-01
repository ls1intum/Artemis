(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DropLocationDialogController', DropLocationDialogController);

    DropLocationDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'DropLocation', 'DragAndDropQuestion'];

    function DropLocationDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, DropLocation, DragAndDropQuestion) {
        var vm = this;

        vm.dropLocation = entity;
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
            if (vm.dropLocation.id !== null) {
                DropLocation.update(vm.dropLocation, onSaveSuccess, onSaveError);
            } else {
                DropLocation.save(vm.dropLocation, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:dropLocationUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
