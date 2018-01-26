(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizController', QuizController);

    QuizController.$inject = ['$scope', '$stateParams', '$interval', 'QuizExerciseForStudent', 'QuizSubmission', 'QuizSubmissionForExercise', 'JhiWebsocketService', 'ExerciseParticipation', 'ParticipationResult', '$timeout'];

    function QuizController($scope, $stateParams, $interval, QuizExerciseForStudent, QuizSubmission, QuizSubmissionForExercise, JhiWebsocketService, ExerciseParticipation, ParticipationResult, $timeout) {
        var vm = this;

        var timeDifference = 0;

        vm.isSubmitting = false;
        vm.isSaving = false;
        vm.lastSavedTimeText = "never";
        vm.justSaved = false;
        vm.waitingForQuizStart = false;

        vm.remainingTimeText = "?";
        vm.remainingTimeSeconds = 0;
        vm.timeUntilStart = 0;

        vm.sendWebsocket = null;
        vm.showingResult = false;
        vm.userScore = "?";

        vm.onSelectionChanged = onSelectionChanged;
        vm.onSubmit = onSubmit;

        init();
        $interval(updateDisplayedTimes, 100);  // update displayed times in UI regularly

        /**
         * Websocket channels
         */
        var submissionChannel;
        var participationChannel;
        var quizExerciseChannel;

        /**
         * unsubscribe from all subscribed websocket channels when page is closed
         */
        $scope.$on('$destroy', function () {
            if (submissionChannel) {
                JhiWebsocketService.unsubscribe(submissionChannel);
            }
            if (participationChannel) {
                JhiWebsocketService.unsubscribe(participationChannel);
            }
            if (quizExerciseChannel) {
                JhiWebsocketService.unsubscribe(quizExerciseChannel);
            }
        });

        /**
         * loads latest submission from server and sets up socket connection
         */
        function init() {
            // initialize websocket channel for changes to quiz exercise
            quizExerciseChannel = '/topic/quizExercise/' + $stateParams.id;
            JhiWebsocketService.subscribe(quizExerciseChannel);
            JhiWebsocketService.receive(quizExerciseChannel).then(null, null, function () {
                load();
            });

            // load the quiz (and existing submission if quiz has started)
            load();
        }

        /**
         * subscribe to any outstanding websocket channels
         */
        function subscribeToWebsocketChannels() {
            if (!submissionChannel) {
                submissionChannel = '/topic/quizSubmissions/' + vm.submission.id;

                // submission channel => react to new submissions
                JhiWebsocketService.subscribe(submissionChannel);
                JhiWebsocketService.receive(submissionChannel).then(null, null, function (payload) {
                    onSaveSuccess(payload);
                });

                // save answers (submissions) through websocket
                vm.sendWebsocket = function (data) {
                    JhiWebsocketService.send(submissionChannel + '/save', data);
                };
            }
            if (!participationChannel) {
                participationChannel = '/topic/participation/' + vm.participation.id + '/newResults';

                // participation channel => react to new results
                JhiWebsocketService.subscribe(participationChannel);
                JhiWebsocketService.receive(participationChannel).then(null, null, function () {
                    if (vm.remainingTimeSeconds <= 0) {
                        // only reload if quiz is over to prevent jumping ui during participation
                        load();
                    }
                });
            }
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
                    vm.remainingTimeText = relativeTimeText(vm.remainingTimeSeconds);
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

            // update time until start
            if (vm.quizExercise && vm.quizExercise.adjustedReleaseDate) {
                if (vm.quizExercise.adjustedReleaseDate.isAfter(moment())) {
                    vm.timeUntilStart = relativeTimeText(vm.quizExercise.adjustedReleaseDate.diff(moment(), "seconds"));
                } else {
                    vm.timeUntilStart = "Now"
                }
            } else {
                vm.timeUntilStart = "";
            }
        }

        /**
         * Express the given timespan as humanized text
         *
         * @param remainingTimeSeconds {number} the amount of seconds to display
         * @return {string} humanized text for the given amount of seconds
         */
        function relativeTimeText(remainingTimeSeconds) {
            if (remainingTimeSeconds > 210) {
                return Math.ceil(remainingTimeSeconds / 60) + " min"
            } else if (remainingTimeSeconds > 59) {
                return Math.floor(remainingTimeSeconds / 60) + " min " + (remainingTimeSeconds % 60) + " s";
            } else {
                return remainingTimeSeconds + " s";
            }
        }

        /**
         * applies the data from the model to the UI (reverse of applySelection):
         *
         * Sets the checkmarks (selected answers) for all questions according to the submission data
         * this needs to be done when we get new submission data, e.g. through the websocket connection
         */
        function applySubmission() {
            // create dictionaries (key: questionID, value: Array of selected answerOptions / mappings)
            // for the submittedAnswers to hand the selected options / mappings in individual arrays to the question components
            vm.selectedAnswerOptions = {};
            vm.dragAndDropMappings = {};

            // iterate through all questions of this quiz
            vm.quizExercise.questions.forEach(function (question) {
                // find the submitted answer that belongs to this question
                var submittedAnswer = vm.submission.submittedAnswers.find(function (submittedAnswer) {
                    return submittedAnswer.question.id === question.id;
                });
                switch (question.type) {
                    case "multiple-choice":
                        // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        vm.selectedAnswerOptions[question.id] = submittedAnswer ? submittedAnswer.selectedOptions : [];
                        break;
                    case "drag-and-drop":
                        // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        vm.dragAndDropMappings[question.id] = submittedAnswer ? submittedAnswer.mappings : [];
                        break;
                    default:
                        console.error("Unknown question type: " + question.type);
                }
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
            // convert the selection dictionary (key: questionID, value: Array of selected answerOptions / mappings)
            // into an array of submittedAnswer objects and save it as the submittedAnswers of the submission
            vm.submission.submittedAnswers = [];

            // for multiple-choice questions
            Object.keys(vm.selectedAnswerOptions).forEach(function (questionID) {
                // find the question object for the given question id
                var question = vm.quizExercise.questions.find(function (question) {
                    return question.id === Number(questionID);
                });
                if (!question) {
                    console.error("question not found for ID: " + questionID);
                    return;
                }
                // generate the submittedAnswer object
                vm.submission.submittedAnswers.push({
                    question: question,
                    selectedOptions: vm.selectedAnswerOptions[questionID],
                    type: question.type
                });
            });

            // for drag-and-drop questions
            Object.keys(vm.dragAndDropMappings).forEach(function (questionID) {
                // find the question object for the given question id
                var question = vm.quizExercise.questions.find(function (question) {
                    return question.id === Number(questionID);
                });
                if (!question) {
                    console.error("question not found for ID: " + questionID);
                    return;
                }
                // generate the submittedAnswer object
                vm.submission.submittedAnswers.push({
                    question: question,
                    mappings: vm.dragAndDropMappings[questionID],
                    type: question.type
                });
            });
        }

        /**
         * Load the latest submission data for this user and this exercise
         */
        function load() {
            QuizExerciseForStudent.get({id: $stateParams.id}).$promise.then(function (quizExercise) {
                vm.quizExercise = quizExercise;
                if (quizExercise.remainingTime != null) {
                    QuizSubmissionForExercise.get({
                        courseId: 1,
                        exerciseId: $stateParams.id
                    }).$promise.then(function (quizSubmission) {
                        vm.submission = quizSubmission;
                        vm.waitingForQuizStart = false;
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
                        }).$promise.then(function (participation) {
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

                            subscribeToWebsocketChannels();
                        });
                    });
                } else {
                    // quiz hasn't started yet
                    vm.waitingForQuizStart = true;
                    if (quizExercise.isPlannedToStart) {
                        // synchronize time with server
                        vm.quizExercise.releaseDate = moment(vm.quizExercise.releaseDate);
                        vm.quizExercise.adjustedReleaseDate = moment().add(quizExercise.timeUntilPlannedStart, "seconds");

                        // load quiz when it is planned to start (at most once every second)
                        $timeout(function () {
                            load();
                        }, Math.max(1, quizExercise.timeUntilPlannedStart) * 1000);
                    }
                }
            });
        }

        /**
         * Display results of quiz
         * @param results
         */
        function showResult(results) {
            vm.result = results[0];
            if (vm.result) {
                vm.showingResult = true;

                // assign user score
                vm.userScore = vm.result.submission.scoreInPoints || 0;

                // create dictionary with scores for each question
                vm.questionScores = {};
                vm.result.submission.submittedAnswers.forEach(function (submittedAnswer) {
                    vm.questionScores[submittedAnswer.question.id] = submittedAnswer.scoreInPoints;
                });
            }
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
        var timeoutJustSaved = _.debounce(function () {
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
