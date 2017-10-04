(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('BuildPlansDeleteController', BuildPlansDeleteController);

    BuildPlansDeleteController.$inject = ['$uibModalInstance', 'entity', 'Exercise'];

    // what is entity??
    function BuildPlansDeleteController($uibModalInstance, entity, Exercise) {
        var vm = this;
        console.log(JSON.stringify(entity));
        vm.exercise = entity;

        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            console.log("inside clear function");
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete(id) {
            //here come service call/s to delete build plans
        }
    }
})();
