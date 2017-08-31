(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('QuizSubmissionDeleteController',QuizSubmissionDeleteController);

    QuizSubmissionDeleteController.$inject = ['$uibModalInstance', 'entity', 'QuizSubmission'];

    function QuizSubmissionDeleteController($uibModalInstance, entity, QuizSubmission) {
        var vm = this;

        vm.quizSubmission = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            QuizSubmission.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
