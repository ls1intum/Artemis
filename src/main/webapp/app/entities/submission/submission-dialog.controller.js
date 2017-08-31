(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('SubmissionDialogController', SubmissionDialogController);

    SubmissionDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'Submission'];

    function SubmissionDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, Submission) {
        var vm = this;

        vm.submission = entity;
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
            if (vm.submission.id !== null) {
                Submission.update(vm.submission, onSaveSuccess, onSaveError);
            } else {
                Submission.save(vm.submission, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('exerciseApplicationApp:submissionUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
