(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizExerciseDialogController', QuizExerciseDialogController);

    QuizExerciseDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'QuizExercise', 'QuizPointStatistic', 'Question'];

    function QuizExerciseDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, QuizExercise, QuizPointStatistic, Question) {
        var vm = this;

        vm.quizExercise = entity;
        vm.clear = clear;
        vm.save = save;
        vm.quizpointstatistics = QuizPointStatistic.query({filter: 'quiz-is-null'});
        $q.all([vm.quizExercise.$promise, vm.quizpointstatistics.$promise]).then(function() {
            if (!vm.quizExercise.quizPointStatistic || !vm.quizExercise.quizPointStatistic.id) {
                return $q.reject();
            }
            return QuizPointStatistic.get({id : vm.quizExercise.quizPointStatistic.id}).$promise;
        }).then(function(quizPointStatistic) {
            vm.quizpointstatistics.push(quizPointStatistic);
        });
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
            $scope.$emit('artemisApp:quizExerciseUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
