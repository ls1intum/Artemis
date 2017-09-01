(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('ModelingSubmissionDeleteController',ModelingSubmissionDeleteController);

    ModelingSubmissionDeleteController.$inject = ['$uibModalInstance', 'entity', 'ModelingSubmission'];

    function ModelingSubmissionDeleteController($uibModalInstance, entity, ModelingSubmission) {
        var vm = this;

        vm.modelingSubmission = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            ModelingSubmission.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
