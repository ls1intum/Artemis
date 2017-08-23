(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('ResultDeleteController',ResultDeleteController);

    ResultDeleteController.$inject = ['$uibModalInstance', 'entity', 'Result'];

    function ResultDeleteController($uibModalInstance, entity, Result) {
        var vm = this;

        vm.result = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            Result.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
