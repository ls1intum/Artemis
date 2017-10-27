(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('MultipleChoiceStatisticDialogController', MultipleChoiceStatisticDialogController);

    MultipleChoiceStatisticDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'MultipleChoiceStatistic', 'AnswerCounter'];

    function MultipleChoiceStatisticDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, MultipleChoiceStatistic, AnswerCounter) {
        var vm = this;

        vm.multipleChoiceStatistic = entity;
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
            if (vm.multipleChoiceStatistic.id !== null) {
                MultipleChoiceStatistic.update(vm.multipleChoiceStatistic, onSaveSuccess, onSaveError);
            } else {
                MultipleChoiceStatistic.save(vm.multipleChoiceStatistic, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('arTeMiSApp:multipleChoiceStatisticUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
