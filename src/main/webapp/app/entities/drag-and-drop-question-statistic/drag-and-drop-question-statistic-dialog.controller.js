(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DragAndDropQuestionStatisticDialogController', DragAndDropQuestionStatisticDialogController);

    DragAndDropQuestionStatisticDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'DragAndDropQuestionStatistic', 'DropLocationCounter'];

    function DragAndDropQuestionStatisticDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, DragAndDropQuestionStatistic, DropLocationCounter) {
        var vm = this;

        vm.dragAndDropQuestionStatistic = entity;
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
            if (vm.dragAndDropQuestionStatistic.id !== null) {
                DragAndDropQuestionStatistic.update(vm.dragAndDropQuestionStatistic, onSaveSuccess, onSaveError);
            } else {
                DragAndDropQuestionStatistic.save(vm.dragAndDropQuestionStatistic, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('arTeMiSApp:dragAndDropQuestionStatisticUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
