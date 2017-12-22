(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuestionDialogController', QuestionDialogController);

    QuestionDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'Question', 'QuestionStatistic', 'QuizExercise'];

    function QuestionDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, Question, QuestionStatistic, QuizExercise) {
        var vm = this;

        vm.question = entity;
        vm.clear = clear;
        vm.save = save;
        vm.questionstatistics = QuestionStatistic.query({filter: 'question-is-null'});
        $q.all([vm.question.$promise, vm.questionstatistics.$promise]).then(function() {
            if (!vm.question.questionStatistic || !vm.question.questionStatistic.id) {
                return $q.reject();
            }
            return QuestionStatistic.get({id : vm.question.questionStatistic.id}).$promise;
        }).then(function(questionStatistic) {
            vm.questionstatistics.push(questionStatistic);
        });
        vm.quizexercises = QuizExercise.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.question.id !== null) {
                Question.update(vm.question, onSaveSuccess, onSaveError);
            } else {
                Question.save(vm.question, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('artemisApp:questionUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
