(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ExerciseDeleteController', ExerciseDeleteController);

    ExerciseDeleteController.$inject = ['$uibModalInstance', 'entity', 'Exercise'];

    function ExerciseDeleteController($uibModalInstance, entity, Exercise) {
        var vm = this;

        vm.exercise = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;
        vm.confirmExerciseName = "";
        vm.deleteParticipations = false;
        vm.deleteInProgress = false;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            vm.deleteInProgress = true;
            Exercise.delete({
                    id: id
                },
                function () {
                    $uibModalInstance.close(true);
                    vm.deleteInProgress = false;
                }, function () {
                    vm.deleteInProgress = false;
                });
        }
    }
})();
