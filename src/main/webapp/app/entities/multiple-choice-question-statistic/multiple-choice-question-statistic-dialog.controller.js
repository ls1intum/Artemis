(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('MultipleChoiceQuestionStatisticDialogController', MultipleChoiceQuestionStatisticDialogController);

    MultipleChoiceQuestionStatisticDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'MultipleChoiceQuestionStatistic', 'AnswerCounter'];

    function MultipleChoiceQuestionStatisticDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, MultipleChoiceQuestionStatistic, AnswerCounter) {
        var vm = this;

        vm.multipleChoiceQuestionStatistic = entity;
        vm.clear = clear;
        vm.save = save;
        vm.answercounters = AnswerCounter.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.multipleChoiceQuestionStatistic.id !== null) {
                MultipleChoiceQuestionStatistic.update(vm.multipleChoiceQuestionStatistic, onSaveSuccess, onSaveError);
            } else {
                MultipleChoiceQuestionStatistic.save(vm.multipleChoiceQuestionStatistic, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:multipleChoiceQuestionStatisticUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
