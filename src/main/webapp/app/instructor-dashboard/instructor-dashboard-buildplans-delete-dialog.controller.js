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
            console.log("inside confirm delete");
            //here come service call/s to delete build plans
        }
    }
})();
