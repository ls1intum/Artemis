(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('AnswerOptionDialogController', AnswerOptionDialogController);

    AnswerOptionDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'AnswerOption', 'MultipleChoiceQuestion'];

    function AnswerOptionDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, AnswerOption, MultipleChoiceQuestion) {
        var vm = this;

        vm.answerOption = entity;
        vm.clear = clear;
        vm.save = save;
        vm.multiplechoicequestions = MultipleChoiceQuestion.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.answerOption.id !== null) {
                AnswerOption.update(vm.answerOption, onSaveSuccess, onSaveError);
            } else {
                AnswerOption.save(vm.answerOption, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('exerciseApplicationApp:answerOptionUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
