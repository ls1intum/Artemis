(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('SubmissionDeleteController',SubmissionDeleteController);

    SubmissionDeleteController.$inject = ['$uibModalInstance', 'entity', 'Submission'];

    function SubmissionDeleteController($uibModalInstance, entity, Submission) {
        var vm = this;

        vm.submission = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            Submission.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
