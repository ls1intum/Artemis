(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('AnswerOptionDeleteController',AnswerOptionDeleteController);

    AnswerOptionDeleteController.$inject = ['$uibModalInstance', 'entity', 'AnswerOption'];

    function AnswerOptionDeleteController($uibModalInstance, entity, AnswerOption) {
        var vm = this;

        vm.answerOption = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            AnswerOption.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
