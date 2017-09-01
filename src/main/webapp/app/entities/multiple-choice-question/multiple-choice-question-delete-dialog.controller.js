(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('MultipleChoiceQuestionDeleteController',MultipleChoiceQuestionDeleteController);

    MultipleChoiceQuestionDeleteController.$inject = ['$uibModalInstance', 'entity', 'MultipleChoiceQuestion'];

    function MultipleChoiceQuestionDeleteController($uibModalInstance, entity, MultipleChoiceQuestion) {
        var vm = this;

        vm.multipleChoiceQuestion = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            MultipleChoiceQuestion.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
