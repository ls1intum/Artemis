(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .controller('QuizExerciseDialogController', QuizExerciseDialogController);

    QuizExerciseDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'QuizExercise', 'Question'];

    function QuizExerciseDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, QuizExercise, Question) {
        var vm = this;

        vm.quizExercise = entity;
        vm.clear = clear;
        vm.save = save;
        vm.questions = Question.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.quizExercise.id !== null) {
                QuizExercise.update(vm.quizExercise, onSaveSuccess, onSaveError);
            } else {
                QuizExercise.save(vm.quizExercise, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('exerciseApplicationApp:quizExerciseUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
