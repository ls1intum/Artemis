(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizController', QuizController);

    QuizController.$inject = ['$scope', '$stateParams', '$interval', 'QuizExerciseForStudent', 'QuizSubmission', 'QuizSubmissionForExercise', 'JhiWebsocketService'];

    function QuizController($scope, $stateParams, $interval, QuizExerciseForStudent, QuizSubmission, QuizSubmissionForExercise, JhiWebsocketService) {
        var vm = this;

        var timeDifference = 0;

        vm.isSaving = false;
        vm.lastSavedTimeText = "never";
        vm.justSaved = false;

        vm.remainingTimeText = "?";
        vm.remainingTimeSeconds = 0;

        vm.sendWebsocket = null;

        vm.onSelectionChanged = onSelectionChanged;
        vm.onSubmit = onSubmit;

        init();
        $interval(updateDisplayedTimes, 100);  // update displayed times in UI regularly

        /**
         * loads latest submission from server and sets up socket connection
         */
        function init() {
            load(function() {
                var websocketChannel = '/topic/quizSubmissions/' + vm.submission.id;

                JhiWebsocketService.subscribe(websocketChannel);

                JhiWebsocketService.receive(websocketChannel).then(null, null, function(payload) {
                    onSaveSuccess(payload);
                });

                vm.sendWebsocket = function(data) {
                    JhiWebsocketService.send(websocketChannel + '/save', data);
                };

                $scope.$on('$destroy', function() {
                    JhiWebsocketService.unsubscribe(websocketChannel);
                });
            });
        }

        /**
         * updates all displayed (relative) times in the UI
         */
        function updateDisplayedTimes() {
            // update remaining time
            if (vm.quizExercise && vm.quizExercise.adjustedDueDate) {
                var endDate = vm.quizExercise.adjustedDueDate;
                if (endDate.isAfter(moment())) {
                    // quiz is still running => calculate remaining seconds and generate text based on that
                    vm.remainingTimeSeconds = endDate.diff(moment(), "seconds");
                    if (vm.remainingTimeSeconds > 210) {
                        vm.remainingTimeText = Math.ceil(vm.remainingTimeSeconds / 60) + " min"
                    } else if (vm.remainingTimeSeconds > 59) {
                        vm.remainingTimeText = Math.floor(vm.remainingTimeSeconds / 60) + " min " + (vm.remainingTimeSeconds % 60) + " s";
                    } else {
                        vm.remainingTimeText = vm.remainingTimeSeconds + " s";
                    }
                } else {
                    // quiz is over => set remaining seconds to negative, to deactivate "Submit" button
                    vm.remainingTimeSeconds = -1;
                    vm.remainingTimeText = "Quiz has ended!";
                }
            } else {
                // remaining time is unknown => Set remaining seconds to 0, to keep "Submit" button enabled
                vm.remainingTimeSeconds = 0;
                vm.remainingTimeText = "?";
            }

            // update submission time
            if (vm.submission && vm.submission.adjustedSubmissionDate) {
                // exact value is not important => use default relative time from moment for better readability and less distraction
                vm.lastSavedTimeText = moment(vm.submission.adjustedSubmissionDate).fromNow();
            }
        }

        /**
         * applies the data from the model to the UI (reverse of applySelection)
         */
        function applySubmission() {
            vm.selectedAnswerOptions = {};
            vm.quizExercise.questions.forEach(function (question) {
                var submittedAnswer = vm.submission.submittedAnswers.find(function (submittedAnswer) {
                    return submittedAnswer.question.id === question.id;
                });
                vm.selectedAnswerOptions[question.id] = submittedAnswer ? submittedAnswer.selectedOptions : [];
            });
        }

        /**
         * updates the model according to UI state (reverse of applySubmission)
         */
        function applySelection() {
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
        }

        /**
         * Load the latest submission data for this user and this exercise
         * @param callback [optional] callback function to be called when data has been loaded
         */
        function load(callback) {
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
                    }

                    // prepare answers for submission
                    applySubmission();

                    if(callback) {
                        callback();
                    }
                });
            });
        }

        /**
         * Callback method to be triggered when the user (de-)selects answers
         */
        function onSelectionChanged() {
            applySelection();
            vm.isSaving = true;
            if (vm.sendWebsocket) {
                vm.sendWebsocket(vm.submission);
            }
        }

        /**
         * This function is called when the user clicks the "Submit" button
         */
        function onSubmit() {
            applySelection();
            vm.isSubmitting = true;
            // TODO: send final submission
            // QuizSubmission.update(vm.submission, onSubmitSuccess, onSubmitError);
        }

        /**
         * Callback function for handling response after saving submission to server
         * @param quizSubmission The response data from the server
         */
        function onSaveSuccess(quizSubmission) {
            vm.isSaving = false;
            vm.submission = quizSubmission;
            applySubmission();
            if (vm.submission.submissionDate) {
                vm.submission.adjustedSubmissionDate = moment(vm.submission.submissionDate).subtract(timeDifference, "seconds").toDate();
                if (Math.abs(moment(vm.submission.adjustedSubmissionDate).diff(moment(), "seconds")) < 2) {
                    vm.justSaved = true;
                    timeoutJustSaved();
                }
            }
        }

        /**
         * debounced function to reset "vm.justSubmitted", so that time since last submission is displayed again when no submission has been made for at least 2 seconds
         * @type {Function}
         */
        var timeoutJustSaved = _.debounce(function() {
            vm.justSaved = false;
        }, 2000);

        /**
         * Callback function for handling response after submitting
         * @param response
         */
        function onSubmitSuccess(response) {
            // TODO
            console.log(response);
            vm.isSubmitting = false;
        }

        /**
         * Callback function for handling error when submitting
         * @param error
         */
        function onSubmitError(error) {
            console.log(error);
            alert("Submitting answers failed! Please try again later.");
            vm.isSubmitting = false;
        }
    }
})();
