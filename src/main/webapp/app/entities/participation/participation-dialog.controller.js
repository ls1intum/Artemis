(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ParticipationDialogController', ParticipationDialogController);

    ParticipationDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'Participation', 'User', 'Result', 'Exercise'];

    function ParticipationDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, Participation, User, Result, Exercise) {
        var vm = this;

        vm.participation = entity;
        vm.clear = clear;
        vm.save = save;
        vm.users = User.query();
        vm.results = Result.query();
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
            $scope.$emit('exerciseApplicationApp:participationUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
