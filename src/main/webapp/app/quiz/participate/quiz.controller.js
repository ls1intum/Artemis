(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizController', QuizController);

    QuizController.$inject = ['$scope', '$stateParams', '$interval', 'QuizExerciseForStudent', 'QuizSubmission', 'QuizSubmissionForExercise', 'JhiWebsocketService', 'ExerciseParticipation', 'ParticipationResult'];

    function QuizController($scope, $stateParams, $interval, QuizExerciseForStudent, QuizSubmission, QuizSubmissionForExercise, JhiWebsocketService, ExerciseParticipation, ParticipationResult) {
        var vm = this;

        var timeDifference = 0;

        vm.isSubmitting = false;
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
                var submissionChannel = '/topic/quizSubmissions/' + vm.submission.id;
                var participationChannel = '/topic/participation/' + vm.participation.id + '/newResults';

                // submission channel => react to new submissions
                JhiWebsocketService.subscribe(submissionChannel);
                JhiWebsocketService.receive(submissionChannel).then(null, null, function(payload) {
                    onSaveSuccess(payload);
                });

                // save answers (submissions) through websocket
                vm.sendWebsocket = function(data) {
                    JhiWebsocketService.send(submissionChannel + '/save', data);
                };

                // participation channel => react to new results
                JhiWebsocketService.subscribe(participationChannel);
                JhiWebsocketService.receive(participationChannel).then(null, null, function() {
                    load();
                });

                $scope.$on('$destroy', function() {
                    JhiWebsocketService.unsubscribe(submissionChannel);
                    JhiWebsocketService.unsubscribe(participationChannel);
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
         * applies the data from the model to the UI (reverse of applySelection):
         *
         * Sets the checkmarks (selected answers) for all questions according to the submission data
         * this needs to be done when we get new submission data, e.g. through the websocket connection
         */
        function applySubmission() {
            // create a dictionary (key: questionID, value: Array of selected answerOptions)
            // for the submittedAnswers to hand the selected options in individual arrays to the question components
            vm.selectedAnswerOptions = {};
            // iterate through all questions of this quiz
            vm.quizExercise.questions.forEach(function (question) {
                // find the submitted answer that belongs to this question
                var submittedAnswer = vm.submission.submittedAnswers.find(function (submittedAnswer) {
                    return submittedAnswer.question.id === question.id;
                });
                // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                vm.selectedAnswerOptions[question.id] = submittedAnswer ? submittedAnswer.selectedOptions : [];
            });
        }

        /**
         * updates the model according to UI state (reverse of applySubmission):
         *
         * Creates the submission from the user's selection
         * this needs to be done when we want to send the submission
         * either for saving (through websocket)
         * or for submitting (through REST call)
         */
        function applySelection() {
            // convert the selection dictionary (key: questionID, value: Array of selected answerOptions)
            // into an array of submittedAnswer objects and save it as the submittedAnswers of the submission
            vm.submission.submittedAnswers = Object.keys(vm.selectedAnswerOptions).map(function (questionID) {
                // find the question object for the given question id
                var question = vm.quizExercise.questions.find(function (question) {
                    return question.id === Number(questionID);
                });
                if (!question) {
                    console.error("question not found for ID: " + questionID);
                    return null;
                }
                // generate the submittedAnswer object
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

                    // show submission answers in UI
                    applySubmission();

                    // load participation
                    ExerciseParticipation.get({
                        courseId: vm.quizExercise.course.id,
                        exerciseId: vm.quizExercise.id
                    }).$promise.then(function(participation) {
                        vm.participation = participation;

                        if (vm.quizExercise.remainingTime < 0) {
                            // load result
                            ParticipationResult.query({
                                courseId: vm.participation.exercise.course.id,
                                exerciseId: vm.participation.exercise.id,
                                participationId: vm.participation.id,
                                showAllResults: false
                            }).$promise.then(showResult);
                        }

                        if(callback) {
                            callback();
                        }
                    });
                });
            });
        }

        /**
         * Display results of quiz
         * @param results
         */
        function showResult(results) {
            vm.result = results[0];
            console.log(vm.result);
            // TODO
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
         * This function is called when the user clicks the "Submit" button
         */
        function onSubmit() {
            applySelection();
            vm.isSubmitting = true;
            QuizSubmission.update(vm.submission, onSubmitSuccess, onSubmitError);
        }

        /**
         * Callback function for handling response after submitting
         * @param response
         */
        function onSubmitSuccess(response) {
            vm.isSubmitting = false;
            vm.submission = response;
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
