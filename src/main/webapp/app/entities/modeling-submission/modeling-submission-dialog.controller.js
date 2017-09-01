(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ModelingSubmissionDialogController', ModelingSubmissionDialogController);

    ModelingSubmissionDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'ModelingSubmission'];

    function ModelingSubmissionDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, ModelingSubmission) {
        var vm = this;

        vm.modelingSubmission = entity;
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
            if (vm.modelingSubmission.id !== null) {
                ModelingSubmission.update(vm.modelingSubmission, onSaveSuccess, onSaveError);
            } else {
                ModelingSubmission.save(vm.modelingSubmission, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:modelingSubmissionUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
