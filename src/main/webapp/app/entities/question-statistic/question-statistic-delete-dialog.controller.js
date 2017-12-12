(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuestionStatisticDeleteController',QuestionStatisticDeleteController);

    QuestionStatisticDeleteController.$inject = ['$uibModalInstance', 'entity', 'QuestionStatistic'];

    function QuestionStatisticDeleteController($uibModalInstance, entity, QuestionStatistic) {
        var vm = this;

        vm.questionStatistic = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            QuestionStatistic.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
