(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizController', QuizController);

    QuizController.$inject = ['$scope', '$stateParams', '$interval', 'QuizExerciseForStudent', 'QuizSubmission', 'QuizSubmissionForExercise'];

    function QuizController($scope, $stateParams, $interval, QuizExerciseForStudent, QuizSubmission, QuizSubmissionForExercise) {
        var vm = this;

        var timeDifference = 0;

        vm.isSubmitting = false;
        vm.lastSubmissionTimeText = "never";

        vm.remainingTime = "?";
        vm.remainingTimeSeconds = 0;

        // update displayed times in UI regularly
        $interval(function () {
            // update remaining time
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
                    vm.remainingTimeSeconds = -1;
                    vm.remainingTime = "Quiz has ended!";
                }
            } else {
                vm.remainingTimeSeconds = 0;
                vm.remainingTime = "?";
            }

            // update submission time
            if (vm.submission && vm.submission.adjustedSubmissionDate) {
                vm.lastSubmissionTimeText = moment(vm.submission.adjustedSubmissionDate).fromNow();
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
                    timeDifference = moment(vm.quizExercise.dueDate).diff(vm.quizExercise.adjustedDueDate, "seconds");

                    // update submission time
                    if (vm.submission.submissionDate) {
                        vm.submission.adjustedSubmissionDate = moment(vm.submission.submissionDate).subtract(timeDifference, "seconds").toDate();
                        vm.lastSubmissionTimeText = moment(vm.submission.adjustedSubmissionDate).fromNow();
                    }

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

        /**
         * This function is called when the user clicks the "Submit" button
         */
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

        /**
         * Callback function for handling response after sending submission to server
         * @param quizSubmission The response data from the server
         */
        function onSubmitSuccess(quizSubmission) {
            vm.isSubmitting = false;
            vm.submission = quizSubmission;
            if (vm.submission.submissionDate) {
                vm.submission.adjustedSubmissionDate = moment(vm.submission.submissionDate).subtract(timeDifference, "seconds").toDate();
                vm.lastSubmissionTimeText = moment(vm.submission.adjustedSubmissionDate).fromNow();
            }
        }

        /**
         * Callback function for handling error when sending submission to server
         * @param error
         */
        function onSubmitError(error) {
            console.log(error);
            alert("Submitting answers failed! Please try again later.");
            vm.isSubmitting = false;
        }
    }
})();
