(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('StatisticDialogController', StatisticDialogController);

    StatisticDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'Statistic'];

    function StatisticDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, Statistic) {
        var vm = this;

        vm.statistic = entity;
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
            if (vm.statistic.id !== null) {
                Statistic.update(vm.statistic, onSaveSuccess, onSaveError);
            } else {
                Statistic.save(vm.statistic, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:statisticUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
