(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ProgrammingExerciseDeleteController',ProgrammingExerciseDeleteController);

    ProgrammingExerciseDeleteController.$inject = ['$uibModalInstance', 'entity', 'ProgrammingExercise'];

    function ProgrammingExerciseDeleteController($uibModalInstance, entity, ProgrammingExercise) {
        var vm = this;

        vm.programmingExercise = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            ProgrammingExercise.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
