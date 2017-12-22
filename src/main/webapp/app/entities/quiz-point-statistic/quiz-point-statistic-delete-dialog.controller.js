(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizPointStatisticDeleteController',QuizPointStatisticDeleteController);

    QuizPointStatisticDeleteController.$inject = ['$uibModalInstance', 'entity', 'QuizPointStatistic'];

    function QuizPointStatisticDeleteController($uibModalInstance, entity, QuizPointStatistic) {
        var vm = this;

        vm.quizPointStatistic = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            QuizPointStatistic.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
