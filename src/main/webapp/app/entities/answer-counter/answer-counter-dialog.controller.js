(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('AnswerCounterDialogController', AnswerCounterDialogController);

    AnswerCounterDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', '$q', 'entity', 'AnswerCounter', 'MultipleChoiceQuestionStatistic', 'AnswerOption'];

    function AnswerCounterDialogController ($timeout, $scope, $stateParams, $uibModalInstance, $q, entity, AnswerCounter, MultipleChoiceQuestionStatistic, AnswerOption) {
        var vm = this;

        vm.answerCounter = entity;
        vm.clear = clear;
        vm.save = save;
        vm.answers = AnswerOption.query({filter: 'answercounter-is-null'});
        $q.all([vm.answerCounter.$promise, vm.answers.$promise]).then(function() {
            if (!vm.answerCounter.answer || !vm.answerCounter.answer.id) {
                return $q.reject();
            }
            return AnswerOption.get({id : vm.answerCounter.answer.id}).$promise;
        }).then(function(answer) {
            vm.answers.push(answer);
        });
        vm.multiplechoicequestionstatistics = MultipleChoiceQuestionStatistic.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.answerCounter.id !== null) {
                AnswerCounter.update(vm.answerCounter, onSaveSuccess, onSaveError);
            } else {
                AnswerCounter.save(vm.answerCounter, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('arTeMiSApp:answerCounterUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
