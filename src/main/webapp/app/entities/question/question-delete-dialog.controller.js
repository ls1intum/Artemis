(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuestionDeleteController',QuestionDeleteController);

    QuestionDeleteController.$inject = ['$uibModalInstance', 'entity', 'Question'];

    function QuestionDeleteController($uibModalInstance, entity, Question) {
        var vm = this;

        vm.question = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            Question.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
