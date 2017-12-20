(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('StatisticCounterDeleteController',StatisticCounterDeleteController);

    StatisticCounterDeleteController.$inject = ['$uibModalInstance', 'entity', 'StatisticCounter'];

    function StatisticCounterDeleteController($uibModalInstance, entity, StatisticCounter) {
        var vm = this;

        vm.statisticCounter = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            StatisticCounter.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
