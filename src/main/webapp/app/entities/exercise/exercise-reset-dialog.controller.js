(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ExerciseResetController',ExerciseResetController);

    ExerciseResetController.$inject = ['$uibModalInstance', 'entity', 'Exercise'];

    function ExerciseResetController($uibModalInstance, entity, Exercise) {
        var vm = this;

        vm.exercise = entity;
        vm.clear = clear;
        vm.confirmReset = confirmReset;
        vm.confirmExerciseName = "";
        vm.deleteParticipations = false;
        vm.resetInProgress = false;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmReset (id) {
            vm.resetInProgress = true;
            Exercise.reset({
                    id: id
                },
                function () {
                    $uibModalInstance.close(true);
                    vm.resetInProgress = false;
                }, function () {
                    vm.resetInProgress = false;
                });
        }



    }
})();
