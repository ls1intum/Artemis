(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuestionStatisticDialogController', QuestionStatisticDialogController);

    QuestionStatisticDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'QuestionStatistic', 'Question'];

    function QuestionStatisticDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, QuestionStatistic, Question) {
        var vm = this;

        vm.questionStatistic = entity;
        vm.clear = clear;
        vm.save = save;
        vm.questions = Question.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.questionStatistic.id !== null) {
                QuestionStatistic.update(vm.questionStatistic, onSaveSuccess, onSaveError);
            } else {
                QuestionStatistic.save(vm.questionStatistic, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:questionStatisticUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
