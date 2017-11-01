(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizController', QuizController);

    QuizController.$inject = ['$scope', '$stateParams', 'QuizExerciseForStudent', '$interval'];

    function QuizController($scope, $stateParams, QuizExerciseForStudent, $interval) {
        var vm = this;

        vm.remainingTime = "?";
        $interval(function() {
            if (vm.quizExercise && vm.quizExercise.isPlannedToStart && vm.quizExercise.releaseDate && vm.quizExercise.duration) {
                var endDate = moment(vm.quizExercise.releaseDate).add(vm.quizExercise.duration, "seconds");
                if (endDate.isAfter(moment())) {
                    vm.remainingTime = endDate.fromNow(true);
                } else {
                    vm.remainingTime = "Time over!";
                }
            } else {
                vm.remainingTime = "?";
            }
        }, 1000);

        vm.onSubmit = onSubmit;

        load();

        function load() {
            QuizExerciseForStudent.get({id : $stateParams.id}).$promise.then(function (quizExercise) {
                vm.quizExercise = quizExercise;
                vm.selectedAnswerOptions = []; // TODO: load existing submission
                vm.totalScore = quizExercise.questions.reduce(function (score, question) {
                    return score + question.score;
                }, 0);
            });
        }

        function onSubmit() {
            // TODO
            console.log(vm.selectedAnswerOptions);
        }
    }
})();
