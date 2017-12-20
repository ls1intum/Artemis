(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('MultipleChoiceQuestionStatisticDeleteController',MultipleChoiceQuestionStatisticDeleteController);

    MultipleChoiceQuestionStatisticDeleteController.$inject = ['$uibModalInstance', 'entity', 'MultipleChoiceQuestionStatistic'];

    function MultipleChoiceQuestionStatisticDeleteController($uibModalInstance, entity, MultipleChoiceQuestionStatistic) {
        var vm = this;

        vm.multipleChoiceQuestionStatistic = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            MultipleChoiceQuestionStatistic.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
