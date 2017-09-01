(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ExerciseDialogController', ExerciseDialogController);

    ExerciseDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'Exercise', 'Course'];

    function ExerciseDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, Exercise, Course) {
        var vm = this;

        vm.exercise = entity;
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
            if (vm.exercise.id !== null) {
                Exercise.update(vm.exercise, onSaveSuccess, onSaveError);
            } else {
                Exercise.save(vm.exercise, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:exerciseUpdate', result);
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
