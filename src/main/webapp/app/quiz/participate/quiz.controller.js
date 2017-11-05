(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizController', QuizController);

    QuizController.$inject = ['$scope', '$stateParams', 'QuizExerciseForStudent', '$interval'];

    function QuizController($scope, $stateParams, QuizExerciseForStudent, $interval) {
        var vm = this;

        vm.remainingTime = "?";
        vm.remainingTimeSeconds = 0;
        $interval(function() {
            vm.remainingTimeSeconds = 0;
            if (vm.quizExercise && vm.quizExercise.adjustedDueDate) {
                var endDate = vm.quizExercise.adjustedDueDate;
                if (endDate.isAfter(moment())) {
                    vm.remainingTimeSeconds = endDate.diff(moment(), "seconds");
                    if (vm.remainingTimeSeconds > 90) {
                        vm.remainingTime = Math.ceil(vm.remainingTimeSeconds / 60) + " minutes";
                    } else if (vm.remainingTimeSeconds > 4){
                        vm.remainingTime = vm.remainingTimeSeconds + " seconds";
                    } else {
                        vm.remainingTime = "< 5 seconds";
                    }
                } else {
                    vm.remainingTime = "Quiz has ended!";
                }
            } else {
                vm.remainingTime = "?";
            }
        }, 100);

        vm.onSubmit = onSubmit;

        load();

        function load() {
            QuizExerciseForStudent.get({id : $stateParams.id}).$promise.then(function (quizExercise) {
                vm.quizExercise = quizExercise;
                vm.totalScore = quizExercise.questions.reduce(function (score, question) {
                    return score + question.score;
                }, 0);
                vm.quizExercise.adjustedDueDate = moment().add(quizExercise.remainingTime, "seconds");

                // prepare answers for submission
                vm.selectedAnswerOptions = {};
                vm.quizExercise.questions.forEach(function(question) {
                    vm.selectedAnswerOptions[question.id] = []; // TODO: Load from existing submission, if quiz was resumed
                });
            });
        }

        function onSubmit() {
            var submission = {
                submittedAnswers: Object.keys(vm.selectedAnswerOptions).map(function(questionID) {
                    return {
                        question: vm.quizExercise.questions.find(function(question) {
                            return question.id === Number(questionID);
                        }),
                        selectedOptions: vm.selectedAnswerOptions[questionID]
                    };
                })
            };
            console.log(submission);
        }
    }
})();
