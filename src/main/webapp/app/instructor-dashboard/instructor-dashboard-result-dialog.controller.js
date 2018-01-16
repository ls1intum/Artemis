(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('InstructorDashboardResultDialogController', InstructorDashboardResultDialogController);

    InstructorDashboardResultDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'participationEntity', 'Result', 'AlertService', 'Feedback'];

    function InstructorDashboardResultDialogController($timeout, $scope, $stateParams, $uibModalInstance, entity, participationEntity, Result, AlertService, Feedback) {
        var vm = this;

        vm.result = entity;
        if(vm.result.completionDate) {
            vm.result.completionDate = new Date(vm.result.completionDate);
        }
        vm.clear = clear;
        vm.datePickerOpenStatus = {};
        vm.openCalendar = openCalendar;
        vm.save = save;
        vm.addFeedbackClicked = false;
        vm.feedbackIndices = [0];
        vm.result.feedbacks = [];
        vm.pushFeedback = pushFeedback;
        vm.popFeedback = popFeedback;

        if(participationEntity) {
            entity.participation = participationEntity;
        } else {
            clear();
        }

        $timeout(function () {
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear() {
            $uibModalInstance.dismiss('cancel');
        }

        function save() {
            vm.isSaving = true;
            for(var i = 0; i < vm.result.feedbacks.length; i++) {
                vm.result.feedbacks[i].type = 'MANUAL';
                if(!vm.result.feedbacks[i].positive) {
                    vm.result.feedbacks[i].positive = false;
                }
            }
            console.log(JSON.stringify(vm.result));
            if (vm.result.id !== null) {
                Result.update(vm.result, onSaveSuccess, onSaveError);
            } else {
                Result.save(vm.result, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess(result) {
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError() {
            vm.isSaving = false;
        }

        vm.datePickerOpenStatus.completionDate = false;

        function openCalendar(date) {
            vm.datePickerOpenStatus[date] = true;
        }

        function pushFeedback() {
            vm.feedbackIndices.push(vm.feedbackIndices.length);
        }

        function popFeedback() {
            if(vm.feedbackIndices.length == 1) {
                vm.addFeedbackClicked = false;
            }
            if(vm.feedbackIndices.length === vm.result.feedbacks.length) {
                vm.result.feedbacks.pop();
            }
            if(vm.feedbackIndices.length > 1) {
                vm.feedbackIndices.pop();
            }
        }
    }
})();
