(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropStatisticDialogController', DragAndDropStatisticDialogController);

    DragAndDropStatisticDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'DragAndDropStatistic', 'DropLocationCounter'];

    function DragAndDropStatisticDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, DragAndDropStatistic, DropLocationCounter) {
        var vm = this;

        vm.dragAndDropStatistic = entity;
        vm.clear = clear;
        vm.save = save;
        vm.droplocationcounters = DropLocationCounter.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.dragAndDropStatistic.id !== null) {
                DragAndDropStatistic.update(vm.dragAndDropStatistic, onSaveSuccess, onSaveError);
            } else {
                DragAndDropStatistic.save(vm.dragAndDropStatistic, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('arTeMiSApp:dragAndDropStatisticUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
