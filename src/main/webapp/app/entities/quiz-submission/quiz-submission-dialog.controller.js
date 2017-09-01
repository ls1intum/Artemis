(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizSubmissionDialogController', QuizSubmissionDialogController);

    QuizSubmissionDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'QuizSubmission', 'SubmittedAnswer'];

    function QuizSubmissionDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, QuizSubmission, SubmittedAnswer) {
        var vm = this;

        vm.quizSubmission = entity;
        vm.clear = clear;
        vm.save = save;
        vm.submittedanswers = SubmittedAnswer.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.quizSubmission.id !== null) {
                QuizSubmission.update(vm.quizSubmission, onSaveSuccess, onSaveError);
            } else {
                QuizSubmission.save(vm.quizSubmission, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:quizSubmissionUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
