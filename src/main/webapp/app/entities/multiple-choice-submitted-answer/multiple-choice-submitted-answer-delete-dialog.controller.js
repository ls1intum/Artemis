(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('MultipleChoiceSubmittedAnswerDeleteController',MultipleChoiceSubmittedAnswerDeleteController);

    MultipleChoiceSubmittedAnswerDeleteController.$inject = ['$uibModalInstance', 'entity', 'MultipleChoiceSubmittedAnswer'];

    function MultipleChoiceSubmittedAnswerDeleteController($uibModalInstance, entity, MultipleChoiceSubmittedAnswer) {
        var vm = this;

        vm.multipleChoiceSubmittedAnswer = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            MultipleChoiceSubmittedAnswer.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
