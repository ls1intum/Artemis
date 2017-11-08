(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ResultDialogController', ResultDialogController);

    ResultDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', '$q', 'entity', 'Result', 'Submission', 'Feedback', 'Participation'];

    function ResultDialogController ($timeout, $scope, $stateParams, $uibModalInstance, $q, entity, Result, Submission, Feedback, Participation) {
        var vm = this;

        vm.result = entity;
        if(vm.result.completionDate) {
            vm.result.completionDate = new Date(vm.result.completionDate);
        }
        vm.clear = clear;
        vm.datePickerOpenStatus = {};
        vm.openCalendar = openCalendar;
        vm.save = save;
        vm.submissions = Submission.query({filter: 'result-is-null'});
        $q.all([vm.result.$promise, vm.submissions.$promise]).then(function() {
            if (!vm.result.submission || !vm.result.submission.id) {
                return $q.reject();
            }
            return Submission.get({id : vm.result.submission.id}).$promise;
        }).then(function(submission) {
            vm.submissions.push(submission);
        });
        vm.feedbacks = Feedback.query();
        vm.participations = Participation.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.result.id !== null) {
                Result.update(vm.result, onSaveSuccess, onSaveError);
            } else {
                Result.save(vm.result, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:resultUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }

        vm.datePickerOpenStatus.completionDate = false;

        function openCalendar (date) {
            vm.datePickerOpenStatus[date] = true;
        }
    }
})();
