(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('SubmittedAnswerDeleteController',SubmittedAnswerDeleteController);

    SubmittedAnswerDeleteController.$inject = ['$uibModalInstance', 'entity', 'SubmittedAnswer'];

    function SubmittedAnswerDeleteController($uibModalInstance, entity, SubmittedAnswer) {
        var vm = this;

        vm.submittedAnswer = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            SubmittedAnswer.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
