(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ExerciseDeleteController',ExerciseDeleteController);

    ExerciseDeleteController.$inject = ['$uibModalInstance', 'entity', 'Exercise'];

    function ExerciseDeleteController($uibModalInstance, entity, Exercise) {
        var vm = this;

        vm.exercise = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            Exercise.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
