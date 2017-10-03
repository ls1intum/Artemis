(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizExerciseDetailController', QuizExerciseDetailController);

    QuizExerciseDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'QuizExercise', 'Question', 'courseEntity'];

    function QuizExerciseDetailController($scope, $rootScope, $stateParams, previousState, entity, QuizExercise, Question, courseEntity) {
        var vm = this;

        vm.quizExercise = entity;
        vm.previousState = previousState.name;
        vm.quizExercise.course = courseEntity;
        vm.datePickerOpenStatus = {
            plannedStartDateTime: false
        };
        vm.openCalendar = openCalendar;

        console.log(vm.quizExercise);

        var unsubscribe = $rootScope.$on('artemisApp:quizExerciseUpdate', function (event, result) {
            vm.quizExercise = result;
        });
        $scope.$on('$destroy', unsubscribe);

        function openCalendar(date) {
            vm.datePickerOpenStatus[date] = true;
        }
    }
})();
