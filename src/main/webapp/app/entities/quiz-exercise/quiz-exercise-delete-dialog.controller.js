(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('QuizExerciseDeleteController',QuizExerciseDeleteController);

    QuizExerciseDeleteController.$inject = ['$uibModalInstance', 'entity', 'QuizExercise'];

    function QuizExerciseDeleteController($uibModalInstance, entity, QuizExercise) {
        var vm = this;

        vm.quizExercise = entity;
        vm.clear = clear;
        vm.confirmDelete = confirmDelete;

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function confirmDelete (id) {
            QuizExercise.delete({id: id},
                function () {
                    $uibModalInstance.close(true);
                });
        }
    }
})();
