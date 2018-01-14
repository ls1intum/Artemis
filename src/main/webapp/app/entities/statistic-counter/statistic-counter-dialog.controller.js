(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('StatisticCounterDialogController', StatisticCounterDialogController);

    StatisticCounterDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'StatisticCounter'];

    function StatisticCounterDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, StatisticCounter) {
        var vm = this;

        vm.statisticCounter = entity;
        vm.clear = clear;
        vm.save = save;

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.statisticCounter.id !== null) {
                StatisticCounter.update(vm.statisticCounter, onSaveSuccess, onSaveError);
            } else {
                StatisticCounter.save(vm.statisticCounter, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:statisticCounterUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
