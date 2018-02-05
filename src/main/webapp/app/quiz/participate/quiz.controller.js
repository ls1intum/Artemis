(function () {
    'use strict';

    angular
        .module('artemisApp')
        .controller('QuizController', QuizController);

    QuizController.$inject = ['$scope', '$state', '$stateParams', '$interval', 'QuizExerciseForStudent', 'QuizExercise', 'QuizSubmission', 'QuizSubmissionForExercise', 'JhiWebsocketService', 'ExerciseParticipation', 'ParticipationResult', '$timeout'];

    function QuizController($scope, $state, $stateParams, $interval, QuizExerciseForStudent, QuizExercise, QuizSubmission, QuizSubmissionForExercise, JhiWebsocketService, ExerciseParticipation, ParticipationResult, $timeout) {
        var vm = this;

        var timeDifference = 0;
        var outstandingWebsocketResponses = 0;

        var runningTimeouts = [];

        // set correct mode
        vm.mode = $state.current.data.mode;

        vm.isSubmitting = false;
        vm.isSaving = false;
        vm.lastSavedTimeText = "never";
        vm.justSaved = false;
        vm.waitingForQuizStart = false;

        vm.remainingTimeText = "?";
        vm.remainingTimeSeconds = 0;
        vm.timeUntilStart = 0;
        vm.disconnected = true;
        vm.unsavedChanges = false;

        vm.sendWebsocket = null;
        vm.showingResult = false;
        vm.userScore = "?";

        vm.onSelectionChanged = onSelectionChanged;
        vm.onSubmit = onSubmit;

        // init according to mode
        switch (vm.mode) {
            case "practice":
                initPracticeMode();
                break;
            case "preview":
                initPreview();
                break;
            case "solution":
                initShowSolution();
                break;
            case "default":
                init();
                break;
        }

        $interval(updateDisplayedTimes, 100);  // update displayed times in UI regularly

        /**
         * Websocket channels
         */
        var submissionChannel;
        var participationChannel;
        var quizExerciseChannel;
        var onConnected;
        var onDisconnected;

        /**
         * unsubscribe from all subscribed websocket channels when page is closed
         */
        $scope.$on('$destroy', function () {
            runningTimeouts.forEach(function (timeout) {
                $timeout.cancel(timeout);
            });

            if (submissionChannel) {
                JhiWebsocketService.unsubscribe(submissionChannel);
            }
            if (participationChannel) {
                JhiWebsocketService.unsubscribe(participationChannel);
            }
            if (quizExerciseChannel) {
                JhiWebsocketService.unsubscribe(quizExerciseChannel);
            }
            if (onConnected) {
                JhiWebsocketService.unbind("connect", onConnected);
            }
            if (onDisconnected) {
                JhiWebsocketService.unbind("disconnect", onDisconnected);
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
                if (vm.waitingForQuizStart) {
                    // only reload if quiz is hasn't started to prevent jumping ui during participation
                    load();
                }
            });

            // listen to connect / disconnect events
            onConnected = function () {
                vm.disconnected = false;
                if (vm.unsavedChanges && vm.sendWebsocket) {
                    vm.sendWebsocket(vm.submission);
                }
            };
            JhiWebsocketService.bind("connect", onConnected);
            onDisconnected = function () {
                vm.disconnected = true;
                if (outstandingWebsocketResponses > 0) {
                    outstandingWebsocketResponses = 0;
                    vm.isSaving = false;
                    vm.unsavedChanges = true;
                }
            };
            JhiWebsocketService.bind("disconnect", onDisconnected);

            // load the quiz (and existing submission if quiz has started)
            load();
        }

        /**
         * loads quizExercise and starts practice mode
         */
        function initPracticeMode() {
            QuizExerciseForStudent.get({id: $stateParams.id}).$promise.then(function (quizExercise) {
                if (quizExercise.isOpenForPractice) {
                    startQuizPreviewOrPractice(quizExercise);
                } else {
                    alert("Error: This quiz is not open for practice!");
                }
            });
        }

        /**
         * loads quiz exercise and starts preview mode
         */
        function initPreview() {
            QuizExercise.get({id: $stateParams.id}).$promise.then(function (quizExercise) {
                startQuizPreviewOrPractice(quizExercise);
            });
        }

        function initShowSolution() {
            QuizExercise.get({id: $stateParams.id}).$promise.then(function (quizExercise) {
                // init quiz
                vm.quizExercise = quizExercise;
                initQuiz();
                vm.showingResult = true;
            });
        }

        /**
         * Start the given quiz in practice or preview mode
         *
         * @param quizExercise {object} the quizExercise to start
         */
        function startQuizPreviewOrPractice(quizExercise) {
            // init quiz
            vm.quizExercise = quizExercise;
            initQuiz();

            // randomize order
            randomizeOrder(quizExercise);

            // init empty submission
            vm.submission = {};

            // adjust end date
            vm.quizExercise.adjustedDueDate = moment().add(quizExercise.duration, "seconds");

            // auto submit when time is up
            runningTimeouts.push($timeout(onSubmit, quizExercise.duration * 1000));
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
                    outstandingWebsocketResponses++;
                    JhiWebsocketService.send(submissionChannel + '/save', data);
                };
            }
            if (!participationChannel) {
                participationChannel = '/topic/participation/' + vm.participation.id + '/newResults';

                // participation channel => react to new results
                JhiWebsocketService.subscribe(participationChannel);
                JhiWebsocketService.receive(participationChannel).then(null, null, function () {
                    loadResults();
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
         * Initialize the selections / mappings for each question with an empty array
         */
        function initQuiz() {
            // calculate score
            vm.totalScore = vm.quizExercise.questions.reduce(function (score, question) {
                return score + question.score;
            }, 0);

            // prepare selection arrays for each question
            if (!vm.submission) {
                vm.selectedAnswerOptions = {};
                vm.dragAndDropMappings = {};

                if (vm.quizExercise.questions) {
                    vm.quizExercise.questions.forEach(function (question) {
                        switch (question.type) {
                            case "multiple-choice":
                                // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                                vm.selectedAnswerOptions[question.id] = [];
                                break;
                            case "drag-and-drop":
                                // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                                vm.dragAndDropMappings[question.id] = [];
                                break;
                            default:
                                console.error("Unknown question type: " + question.type);
                        }
                    });
                }
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
                initQuiz();

                if (quizExercise.remainingTime != null) {
                    if (quizExercise.remainingTime > 0) {
                        // apply randomized order where necessary
                        randomizeOrder(quizExercise);

                        // automatically load results 5 seconds after quiz has ended (in case websocket didn't work)
                        runningTimeouts.push(
                            $timeout(function () {
                                if (!vm.showingResult) {
                                    loadResults();
                                }
                            }, (quizExercise.remainingTime + 5) * 1000)
                        );
                    }
                    QuizSubmissionForExercise.get({
                        courseId: 1,
                        exerciseId: $stateParams.id
                    }).$promise.then(function (quizSubmission) {
                        vm.submission = quizSubmission;
                        vm.waitingForQuizStart = false;

                        // update timeDifference
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
                                    showAllResults: false,
                                    ratedOnly: true
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
                        runningTimeouts.push(
                            $timeout(function () {
                                if (vm.waitingForQuizStart) {
                                    load();
                                }
                            }, Math.max(1, quizExercise.timeUntilPlannedStart) * 1000)
                        );
                    }
                }
            });
        }

        /**
         * Load the quiz results and update quizExercise with missing fields
         */
        function loadResults() {
            // TODO: create endpoint to reduce number of REST calls to one

            QuizExerciseForStudent.get({id: $stateParams.id}).$promise.then(function (quizExercise) {
                // only act on it if quiz has ended
                if (quizExercise.remainingTime < 0) {
                    // update questions with explanations and correct answer options / correct mappings
                    applyFullQuizExercise(quizExercise);

                    QuizSubmissionForExercise.get({
                        courseId: 1,
                        exerciseId: $stateParams.id
                    }).$promise.then(function (quizSubmission) {
                        vm.submission = quizSubmission;

                        // show submission answers in UI
                        applySubmission();

                        // load and show result
                        ParticipationResult.query({
                            courseId: vm.participation.exercise.course.id,
                            exerciseId: vm.participation.exercise.id,
                            participationId: vm.participation.id,
                            showAllResults: false,
                            ratedOnly: true
                        }).$promise.then(showResult, onLoadResultError);
                    }, onLoadResultError);
                }
            }, onLoadResultError);
        }

        /**
         * handle error when loading the results
         *
         * @param error
         */
        function onLoadResultError(error) {
            console.error(error);
            alert("Loading results failed. Please wait a few seconds and refresh the page manually.");
        }

        /**
         * Transfer additional information (explanations, correct answers) from
         * the given full quiz exercise to vm.quizExercise
         *
         * @param fullQuizExercise {object} the quizExercise containing additional information
         */
        function applyFullQuizExercise(fullQuizExercise) {
            vm.quizExercise.questions.forEach(function (question) {
                // find updated question
                var fullQuestion = fullQuizExercise.questions.find(function (fullQuestion) {
                    return question.id === fullQuestion.id;
                });
                if (fullQuestion) {
                    question.explanation = fullQuestion.explanation;

                    switch (question.type) {
                        case "multiple-choice":
                            question.answerOptions.forEach(function (answerOption) {
                                // find updated answerOption
                                var fullAnswerOption = fullQuestion.answerOptions.find(function (fullAnswerOption) {
                                    return answerOption.id === fullAnswerOption.id;
                                });
                                if (fullAnswerOption) {
                                    answerOption.explanation = fullAnswerOption.explanation;
                                    answerOption.isCorrect = fullAnswerOption.isCorrect;
                                }
                            });
                            break;
                        case "drag-and-drop":
                            question.correctMappings = fullQuestion.correctMappings;
                            break;
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

                // assign user score (limit decimal places to 2)
                vm.userScore = vm.result.submission.scoreInPoints ? Math.round(vm.result.submission.scoreInPoints * 100) / 100 : 0;

                // create dictionary with scores for each question
                vm.questionScores = {};
                vm.result.submission.submittedAnswers.forEach(function (submittedAnswer) {
                    // limit decimal places to 2
                    vm.questionScores[submittedAnswer.question.id] = Math.round(submittedAnswer.scoreInPoints * 100) / 100;
                });
            }
        }

        /**
         * Randomize the order of the questions
         * (and answerOptions or dragItems within each question)
         * if randomizeOrder is true
         *
         * @param quizExercise {object} the quizExercise to randomize elements in
         */
        function randomizeOrder(quizExercise) {
            if (quizExercise.questions) {
                // shuffle questions
                if (quizExercise.randomizeQuestionOrder) {
                    shuffle(quizExercise.questions);
                }

                // shuffle answerOptions / dragItems within questions
                quizExercise.questions.forEach(function (question) {
                    if (question.randomizeOrder) {
                        switch (question.type) {
                            case "multiple-choice":
                                shuffle(question.answerOptions);
                                break;
                            case "drag-and-drop":
                                shuffle(question.dragItems);
                                break;
                        }
                    }
                });
            }
        }

        /**
         * Shuffles array in place.
         * @param {Array} items An array containing the items.
         */
        function shuffle(items) {
            for (var i = items.length - 1; i > 0; i--) {
                var pickedIndex = Math.floor(Math.random() * (i + 1));
                var picked = items[pickedIndex];
                items[pickedIndex] = items[i];
                items[i] = picked;
            }
        }

        /**
         * Callback method to be triggered when the user (de-)selects answers
         */
        function onSelectionChanged() {
            applySelection();
            if (vm.sendWebsocket) {
                if (!vm.disconnected) {
                    vm.isSaving = true;
                    vm.sendWebsocket(vm.submission);
                } else {
                    vm.unsavedChanges = true;
                }
            }
        }

        /**
         * Callback function for handling response after saving submission to server
         * @param quizSubmission The response data from the server
         */
        function onSaveSuccess(quizSubmission) {
            outstandingWebsocketResponses = Math.max(0, outstandingWebsocketResponses - 1);
            if (outstandingWebsocketResponses === 0) {
                vm.isSaving = false;
                vm.unsavedChanges = false;
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
            switch (vm.mode) {
                case "practice":
                    if (!vm.submission.id) {
                        QuizSubmission.submitForPractice({
                            courseId: 1,
                            exerciseId: $stateParams.id
                        }, vm.submission, onSubmitPracticeSuccess, onSubmitError);
                    }
                    break;
                case "preview":
                    if (!vm.submission.id) {
                        QuizSubmission.submitForPreview({
                            courseId: 1,
                            exerciseId: $stateParams.id
                        }, vm.submission, onSubmitPreviewSuccess, onSubmitError);
                    }
                    break;
                case "default":
                    QuizSubmission.update(vm.submission, onSubmitSuccess, onSubmitError);
                    break;
            }
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
         * Callback function for handling response after submitting for practice
         * @param response
         */
        function onSubmitPracticeSuccess(response) {
            vm.isSubmitting = false;
            vm.submission = response;
            applySubmission();

            // load participation
            ExerciseParticipation.get({
                courseId: vm.quizExercise.course.id,
                exerciseId: vm.quizExercise.id
            }).$promise.then(function (participation) {
                vm.participation = participation;
                // load result
                ParticipationResult.query({
                    courseId: vm.participation.exercise.course.id,
                    exerciseId: vm.participation.exercise.id,
                    participationId: vm.participation.id,
                    showAllResults: false,
                    ratedOnly: false
                }).$promise.then(showResult);
            });
        }

        /**
         * Callback function for handling response after submitting for preview
         * @param response
         */
        function onSubmitPreviewSuccess(response) {
            showResult([response]);
        }

        /**
         * Callback function for handling error when submitting
         * @param error
         */
        function onSubmitError(error) {
            console.error(error);
            alert("Submitting was not possible. Please try again later. If your answers have been saved, you can also wait until the quiz has finished.");
            vm.isSubmitting = false;
        }
    }
})();
