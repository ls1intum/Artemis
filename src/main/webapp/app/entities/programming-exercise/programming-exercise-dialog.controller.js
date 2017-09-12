(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ProgrammingExerciseDialogController', ProgrammingExerciseDialogController);

    ProgrammingExerciseDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'ProgrammingExercise', 'Course'];

    function ProgrammingExerciseDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, ProgrammingExercise, Course) {
        var vm = this;

        vm.programmingExercise = entity;
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
            if (vm.programmingExercise.id !== null) {
                ProgrammingExercise.update(vm.programmingExercise, onSaveSuccess, onSaveError);
            } else {
                ProgrammingExercise.save(vm.programmingExercise, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:programmingExerciseUpdate', result);
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
