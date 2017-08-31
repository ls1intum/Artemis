(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ModelingExerciseDialogController', ModelingExerciseDialogController);

    ModelingExerciseDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'ModelingExercise'];

    function ModelingExerciseDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, ModelingExercise) {
        var vm = this;

        vm.modelingExercise = entity;
        vm.clear = clear;
        vm.save = save;

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
            $scope.$emit('exerciseApplicationApp:modelingExerciseUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
