(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizPointStatisticDialogController', QuizPointStatisticDialogController);

    QuizPointStatisticDialogController.$inject = ['$timeout', '$scope', '$stateParams', '$uibModalInstance', 'entity', 'QuizPointStatistic', 'QuizExercise'];

    function QuizPointStatisticDialogController ($timeout, $scope, $stateParams, $uibModalInstance, entity, QuizPointStatistic, QuizExercise) {
        var vm = this;

        vm.quizPointStatistic = entity;
        vm.clear = clear;
        vm.save = save;
        vm.quizexercises = QuizExercise.query();

        $timeout(function (){
            angular.element('.form-group:eq(1)>input').focus();
        });

        function clear () {
            $uibModalInstance.dismiss('cancel');
        }

        function save () {
            vm.isSaving = true;
            if (vm.quizPointStatistic.id !== null) {
                QuizPointStatistic.update(vm.quizPointStatistic, onSaveSuccess, onSaveError);
            } else {
                QuizPointStatistic.save(vm.quizPointStatistic, onSaveSuccess, onSaveError);
            }
        }

        function onSaveSuccess (result) {
            $scope.$emit('arTeMiSApp:quizPointStatisticUpdate', result);
            $uibModalInstance.close(result);
            vm.isSaving = false;
        }

        function onSaveError () {
            vm.isSaving = false;
        }


    }
})();
