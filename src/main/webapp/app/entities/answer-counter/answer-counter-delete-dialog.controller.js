(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('AnswerCounterDeleteController',AnswerCounterDeleteController);

    AnswerCounterDeleteController.$inject = ['$uibModalInstance', 'entity', 'AnswerCounter'];

    function AnswerCounterDeleteController($uibModalInstance, entity, AnswerCounter) {
        var vm = this;

        vm.answerCounter = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            AnswerCounter.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
