(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('MultipleChoiceSubmittedAnswerDialogController', MultipleChoiceSubmittedAnswerDialogController);

    MultipleChoiceSubmittedAnswerDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'MultipleChoiceSubmittedAnswer', 'AnswerOption'];

    function MultipleChoiceSubmittedAnswerDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, MultipleChoiceSubmittedAnswer, AnswerOption) {
        var vm = this;

        vm.multipleChoiceSubmittedAnswer = entity;
        vm.clear = clear;
        vm.save = save;
        vm.answeroptions = AnswerOption.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.multipleChoiceSubmittedAnswer.id !== null) {
                MultipleChoiceSubmittedAnswer.update(vm.multipleChoiceSubmittedAnswer, onSaveSuccess, onSaveError);
            } else {
                MultipleChoiceSubmittedAnswer.save(vm.multipleChoiceSubmittedAnswer, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('exerciseApplicationApp:multipleChoiceSubmittedAnswerUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
