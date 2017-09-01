(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ParticipationDialogController', ParticipationDialogController);

    ParticipationDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'Participation', 'Result', 'User', 'Exercise'];

    function ParticipationDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, Participation, Result, User, Exercise) {
        var vm = this;

        vm.participation = entity;
        vm.clear = clear;
        vm.datePickerOpenStatus = {};
        vm.openCalendar = openCalendar;
        vm.save = save;
        vm.results = Result.query();
        vm.users = User.query();
        vm.exercises = Exercise.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.participation.id !== null) {
                Participation.update(vm.participation, onSaveSuccess, onSaveError);
            } else {
                Participation.save(vm.participation, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:participationUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }

        vm.datePickerOpenStatus.initializationDate = false;

        function openCalendar (date) {
            vm.datePickerOpenStatus[date] = true;
        }
    }
})();
