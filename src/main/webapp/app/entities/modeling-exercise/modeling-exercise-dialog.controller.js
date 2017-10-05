(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ModelingExerciseDialogController', ModelingExerciseDialogController);

    ModelingExerciseDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'ModelingExercise', 'Course'];

    function ModelingExerciseDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, ModelingExercise, Course) {
        var vm = this;

        vm.modelingExercise = entity;
        vm.clear = clear;
        vm.datePickerOpenStatus = {};
        vm.openCalendar = openCalendar;
        vm.save = save;
        vm.courses = Course.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.modelingExercise.id !== null) {
                ModelingExercise.update(vm.modelingExercise, onSaveSuccess, onSaveError);
            } else {
                ModelingExercise.save(vm.modelingExercise, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:modelingExerciseUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }

        vm.datePickerOpenStatus.releaseDate = false;
        vm.datePickerOpenStatus.dueDate = false;

        function openCalendar (date) {
            vm.datePickerOpenStatus[date] = true;
        }

    }
})();
