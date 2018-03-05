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

            // disable automatic websocket reconnect
            JhiWebsocketService.disableReconnect();

            if (submissionChannel) {
                JhiWebsocketService.unsubscribe("/user" + submissionChannel);
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

            subscribeToWebsocketChannels();

            // load the quiz (and existing submission if quiz has started)
            ExerciseParticipation.get({
                courseId: 1,
                exerciseId: $stateParams.id
            }).$promise.then(applyParticipationFull);
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
                submissionChannel = '/topic/quizExercise/' + $stateParams.id + '/submission';

                // submission channel => react to new submissions
                JhiWebsocketService.subscribe("/user" + submissionChannel);
                JhiWebsocketService.receive("/user" + submissionChannel).then(null, null, function (payload) {
                    onSaveSuccess(payload);
                });

                // save answers (submissions) through websocket
                vm.sendWebsocket = function (data) {
                    outstandingWebsocketResponses++;
                    JhiWebsocketService.send(submissionChannel, data);
                };
            }

            if (!participationChannel) {
                participationChannel = '/user/topic/quizExercise/' + $stateParams.id + '/participation';

                // participation channel => react to new results
                JhiWebsocketService.subscribe(participationChannel);
                JhiWebsocketService.receive(participationChannel).then(null, null, function (participation) {
                    if (vm.waitingForQuizStart) {
                        // only apply completely if quiz is hasn't started to prevent jumping ui during participation
                        applyParticipationFull(participation);
                    } else {
                        // update quizExercise and results / submission
                        applyParticipationAfterStart(participation);
                    }
                });
            }

            if (!quizExerciseChannel) {
                quizExerciseChannel = "/topic/quizExercise/" + $stateParams.id;

                // quizExercise channel => react to changes made to quizExercise (e.g. start date)
                JhiWebsocketService.subscribe(quizExerciseChannel);
                JhiWebsocketService.receive(quizExerciseChannel).then(null, null, function (quizExercise) {
                    // only reload if quiz is hasn't started to prevent jumping ui during participation
                    if (vm.waitingForQuizStart) {
                        applyQuizFull(quizExercise);
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
         * Apply the data of the participation, replacing all old data
         */
        function applyParticipationFull(participation) {
            console.log(participation);
            applyQuizFull(participation.exercise);

            // apply submission if it exists
            if (participation.results.length) {
                vm.submission = participation.results[0].submission;
                vm.waitingForQuizStart = false;

                // update submission time
                updateSubmissionTime();

                // show submission answers in UI
                applySubmission();

                if (participation.initializationState === "FINISHED" && vm.quizExercise.remainingTime < 0) {
                    // quiz has ended and results are available
                    showResult(participation.results);
                }
            }


        }

        /**
         * apply the data of the quiz, replacing all old data and enabling reconnect if necessary
         * @param quizExercise
         */
        function applyQuizFull(quizExercise) {
            vm.quizExercise = quizExercise;
            initQuiz();

            // check if quiz has started
            console.log(vm.quizExercise);
            if (vm.quizExercise.started) {
                // check if quiz hasn't ended
                if (!vm.quizExercise.ended) {
                    // enable automatic websocket reconnect
                    JhiWebsocketService.enableReconnect();

                    // apply randomized order where necessary
                    randomizeOrder(vm.quizExercise);

                    // update timeDifference
                    vm.quizExercise.adjustedDueDate = moment().add(vm.quizExercise.remainingTime, "seconds");
                    timeDifference = moment(vm.quizExercise.dueDate).diff(vm.quizExercise.adjustedDueDate, "seconds");

                    // alert user 5 seconds after quiz has ended (in case websocket didn't work)
                    runningTimeouts.push(
                        $timeout(function () {
                            if (vm.disconnected && !vm.showingResult) {
                                alert("Loading results failed. Please wait a few seconds and refresh the page manually.");
                            }
                        }, (vm.quizExercise.remainingTime + 5) * 1000)
                    );
                }
            } else {
                // quiz hasn't started yet
                vm.waitingForQuizStart = true;

                // enable automatic websocket reconnect
                JhiWebsocketService.enableReconnect();

                if (vm.quizExercise.isPlannedToStart) {
                    // synchronize time with server
                    vm.quizExercise.releaseDate = moment(vm.quizExercise.releaseDate);
                    vm.quizExercise.adjustedReleaseDate = moment().add(vm.quizExercise.timeUntilPlannedStart, "seconds");

                    // load quiz when it is planned to start (at most once every second)
                    runningTimeouts.push(
                        $timeout(function () {
                            if (vm.waitingForQuizStart) {
                                // Load only quizExercise
                                QuizExerciseForStudent.get({id: $stateParams.id}).$promise.then(applyQuizFull);
                            }
                        }, Math.max(1, vm.quizExercise.timeUntilPlannedStart + 1) * 1000)
                    );
                }
            }
        }

        function applyParticipationAfterStart(participation) {
            console.log(participation);
            if (participation.initializationState === "FINISHED" &&
                participation.results.length &&
                participation.exercise.ended) {
                // quiz has ended and results are available
                vm.submission = participation.results[0].submission;

                // update submission time
                updateSubmissionTime();
                transferInformationToQuizExercise(participation.exercise);
                applySubmission();
                showResult(participation.results);
            }
        }

        /**
         * Transfer additional information (explanations, correct answers) from
         * the given full quiz exercise to vm.quizExercise
         *
         * @param fullQuizExercise {object} the quizExercise containing additional information
         */
        function transferInformationToQuizExercise(fullQuizExercise) {
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

                // disable automatic websocket reconnect
                JhiWebsocketService.disableReconnect();

                // assign user score (limit decimal places to 2)
                vm.userScore = vm.submission.scoreInPoints ? Math.round(vm.submission.scoreInPoints * 100) / 100 : 0;

                // create dictionary with scores for each question
                vm.questionScores = {};
                vm.submission.submittedAnswers.forEach(function (submittedAnswer) {
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
         * update the value for adjustedSubmissionDate in vm.submission
         */
        function updateSubmissionTime() {
            if (vm.submission.submissionDate) {
                vm.submission.adjustedSubmissionDate = moment(vm.submission.submissionDate).subtract(timeDifference, "seconds").toDate();
                if (Math.abs(moment(vm.submission.adjustedSubmissionDate).diff(moment(), "seconds")) < 2) {
                    vm.justSaved = true;
                    timeoutJustSaved();
                }
            }
        }

        /**
         * Callback function for handling response after saving submission to server
         * @param response The response data from the server
         */
        function onSaveSuccess(response) {
            if (!response) {
                // TODO: Include reason why saving failed
                alert("Saving Answers failed.");
                vm.unsavedChanges = true;
                vm.isSubmitting = false;
                if (outstandingWebsocketResponses > 0) {
                    outstandingWebsocketResponses--;
                }
                if (outstandingWebsocketResponses === 0) {
                    vm.isSaving = false;
                }
                return;
            }
            if (response.submitted) {
                outstandingWebsocketResponses = 0;
                vm.isSaving = false;
                vm.unsavedChanges = false;
                vm.isSubmitting = false;
                vm.submission = response;
                updateSubmissionTime();
                applySubmission();
            } else if (outstandingWebsocketResponses === 0) {
                vm.isSaving = false;
                vm.unsavedChanges = false;
                vm.submission = response;
                updateSubmissionTime();
                applySubmission();
            } else {
                outstandingWebsocketResponses--;
                if (outstandingWebsocketResponses === 0) {
                    vm.isSaving = false;
                    vm.unsavedChanges = false;
                    if (response) {
                        vm.submission.submissionDate = response.submissionDate;
                        updateSubmissionTime();
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
                    if (vm.disconnected || !submissionChannel) {
                        // TODO: Create REST Endpoint as a fallback option
                        alert("Cannot Submit while disconnected. Don't worry, answers that were saved while you were still connected will be submitted automatically when the quiz ends.");
                        vm.isSubmitting = false;
                        return;
                    }
                    // send submission through websocket with "submitted = true"
                    JhiWebsocketService.send(submissionChannel, {
                        submittedAnswers: vm.submission.submittedAnswers,
                        submitted: true
                    });
                    break;
            }
        }

        /**
         * Callback function for handling response after submitting for practice
         * @param response
         */
        function onSubmitPracticeSuccess(response) {
            // TODO: Update endpoint to return result instead of submission
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
            vm.isSubmitting = false;
            vm.submission = response.submission;
            applySubmission();
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
