(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ModelingExerciseDeleteController',ModelingExerciseDeleteController);

    ModelingExerciseDeleteController.$inject = ['$uibModalInstance', 'entity', 'ModelingExercise'];

    function ModelingExerciseDeleteController($uibModalInstance, entity, ModelingExercise) {
        var vm = this;

        vm.modelingExercise = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            ModelingExercise.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
