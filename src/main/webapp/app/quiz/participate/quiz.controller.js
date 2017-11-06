(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizController', QuizController);

    QuizController.$inject = ['$scope', '$stateParams', '$interval', 'QuizExerciseForStudent', 'QuizSubmission', 'QuizSubmissionForExercise'];

    function QuizController($scope, $stateParams, $interval, QuizExerciseForStudent, QuizSubmission, QuizSubmissionForExercise) {
        var vm = this;

        vm.isSubmitting = false;

        vm.remainingTime = "?";
        vm.remainingTimeSeconds = 0;
        $interval(function () {
            vm.remainingTimeSeconds = 0;
            if (vm.quizExercise && vm.quizExercise.adjustedDueDate) {
                var endDate = vm.quizExercise.adjustedDueDate;
                if (endDate.isAfter(moment())) {
                    vm.remainingTimeSeconds = endDate.diff(moment(), "seconds");
                    if (vm.remainingTimeSeconds > 90) {
                        vm.remainingTime = Math.ceil(vm.remainingTimeSeconds / 60) + " minutes";
                    } else if (vm.remainingTimeSeconds > 4) {
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
            QuizSubmissionForExercise.get({
                courseId: 1,
                exerciseId: $stateParams.id
            }).$promise.then(function (quizSubmission) {
                vm.submission = quizSubmission;
                QuizExerciseForStudent.get({id: $stateParams.id}).$promise.then(function (quizExercise) {
                    vm.quizExercise = quizExercise;
                    vm.totalScore = quizExercise.questions.reduce(function (score, question) {
                        return score + question.score;
                    }, 0);
                    vm.quizExercise.adjustedDueDate = moment().add(quizExercise.remainingTime, "seconds");

                    // prepare answers for submission
                    vm.selectedAnswerOptions = {};
                    vm.quizExercise.questions.forEach(function (question) {
                        var submittedAnswer = vm.submission.submittedAnswers.find(function (submittedAnswer) {
                            return submittedAnswer.question.id === question.id;
                        });
                        vm.selectedAnswerOptions[question.id] = submittedAnswer ? submittedAnswer.selectedOptions : [];
                    });
                });
            });
        }

        function onSubmit() {
            vm.submission.submittedAnswers = Object.keys(vm.selectedAnswerOptions).map(function (questionID) {
                var question = vm.quizExercise.questions.find(function (question) {
                    return question.id === Number(questionID);
                });
                if (!question) {
                    console.error("question not found for ID: " + questionID);
                    return null;
                }
                return {
                    question: question,
                    selectedOptions: vm.selectedAnswerOptions[questionID],
                    type: question.type
                };
            });
            vm.isSubmitting = true;
            QuizSubmission.update(vm.submission, onSubmitSuccess, onSubmitError)
        }

        function onSubmitSuccess(result) {
            vm.isSubmitting = false;
            console.log("success", result);
        }

        function onSubmitError() {
            alert("Submitting answers failed! Please try again later.");
            vm.isSubmitting = false;
        }
    }
})();
