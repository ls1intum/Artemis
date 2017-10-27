(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('MultipleChoiceStatisticDeleteController',MultipleChoiceStatisticDeleteController);

    MultipleChoiceStatisticDeleteController.$inject = ['$uibModalInstance', 'entity', 'MultipleChoiceStatistic'];

    function MultipleChoiceStatisticDeleteController($uibModalInstance, entity, MultipleChoiceStatistic) {
        var vm = this;

        vm.multipleChoiceStatistic = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            MultipleChoiceStatistic.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
