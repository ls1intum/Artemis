(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('MultipleChoiceQuestionDialogController', MultipleChoiceQuestionDialogController);

    MultipleChoiceQuestionDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'MultipleChoiceQuestion', 'AnswerOption'];

    function MultipleChoiceQuestionDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, MultipleChoiceQuestion, AnswerOption) {
        var vm = this;

        vm.multipleChoiceQuestion = entity;
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
            if (vm.multipleChoiceQuestion.id !== null) {
                MultipleChoiceQuestion.update(vm.multipleChoiceQuestion, onSaveSuccess, onSaveError);
            } else {
                MultipleChoiceQuestion.save(vm.multipleChoiceQuestion, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('exerciseApplicationApp:multipleChoiceQuestionUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
