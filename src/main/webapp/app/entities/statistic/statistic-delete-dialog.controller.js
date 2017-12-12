(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('StatisticDeleteController',StatisticDeleteController);

    StatisticDeleteController.$inject = ['$uibModalInstance', 'entity', 'Statistic'];

    function StatisticDeleteController($uibModalInstance, entity, Statistic) {
        var vm = this;

        vm.statistic = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            Statistic.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
