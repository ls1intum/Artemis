(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('BuildPlansDeleteController', BuildPlansDeleteController);

    BuildPlansDeleteController.$inject = ['$uibModalInstance', 'entity', 'Exercise'];

    function BuildPlansDeleteController($uibModalInstance, entity, Exercise) {
        var vm = this;

        vm.exercise = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            console.log("inside clear function");
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete(id) {
            console.log("inside confirm delete");
            Exercise.deleteBuildPlans({
                    id: id
                },
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
