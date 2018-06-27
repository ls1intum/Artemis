import { Component, OnDestroy, OnInit } from '@angular/core';
import { JhiWebsocketService } from '../../shared';
import * as moment from 'moment';
import * as _ from 'lodash';
import { QuizExercise, QuizExerciseService } from '../../entities/quiz-exercise';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { QuizSubmissionService } from '../../entities/quiz-submission';
import { ExerciseParticipationService, Participation } from '../../entities/participation';
import { Result } from 'app/entities/result';
import { DragAndDropQuestion } from 'app/entities/drag-and-drop-question';
import { MultipleChoiceQuestion } from 'app/entities/multiple-choice-question';

@Component({
    selector: 'jhi-quiz',
    templateUrl: './quiz.component.html',
    providers: [ExerciseParticipationService]
})
export class QuizComponent implements OnInit, OnDestroy {

    private subscription: Subscription;
    private subscriptionData: Subscription;

    timeDifference = 0;
    outstandingWebsocketResponses = 0;

    runningTimeouts = [];

    isSubmitting = false;
    isSaving = false;
    lastSavedTimeText = 'never';
    justSaved = false;
    waitingForQuizStart = false;

    remainingTimeText = '?';
    remainingTimeSeconds = 0;
    timeUntilStart: any = 0;
    disconnected = true;
    unsavedChanges = false;

    sendWebsocket = null;
    showingResult = false;
    userScore: any = '?';

    mode;
    submission: any = {};
    quizExercise;
    totalScore;
    selectedAnswerOptions = {};
    dragAndDropMappings;
    result;
    questionScores = {};
    id;
    interval: any;

    /**
     * Websocket channels
     */
    submissionChannel: string;
    participationChannel: string;
    quizExerciseChannel: string;
    onConnected: () => void;
    onDisconnected: () => void;

    /**
     * debounced function to reset 'justSubmitted', so that time since last submission is displayed again when no submission has been made for at least 2 seconds
     * @type {Function}
     */
    timeoutJustSaved = _.debounce(() => {
        this.justSaved = false;
    }, 2000);

    constructor(private jhiWebsocketService: JhiWebsocketService,
                private quizExerciseService: QuizExerciseService,
                private exerciseParticipationService: ExerciseParticipationService,
                private route: ActivatedRoute,
                private jhiAlertService: JhiAlertService,
                private quizSubmissionService: QuizSubmissionService) {}

    ngOnInit() {
        // set correct mode
        this.subscriptionData = this.route.data.subscribe(data => {
            this.mode = data.mode;
            this.subscription = this.route.params.subscribe(params => {
                this.id = params['id'];
                // init according to mode
                switch (this.mode) {
                    case 'practice':
                        this.initPracticeMode();
                        break;
                    case 'preview':
                        this.initPreview();
                        break;
                    case 'solution':
                        this.initShowSolution();
                        break;
                    case 'default':
                        this.init();
                        break;
                }
            });
        });
        // update displayed times in UI regularly
        this.interval = setInterval(() => {
            this.updateDisplayedTimes();
        }, 100);
    }

    ngOnDestroy() {
        clearInterval(this.interval);
        /**
         * unsubscribe from all subscribed websocket channels when page is closed
         */
        this.runningTimeouts.forEach(function(timeout) {
            clearTimeout(timeout);
        });

        // disable automatic websocket reconnect
        this.jhiWebsocketService.disableReconnect();

        if (this.submissionChannel) {
            this.jhiWebsocketService.unsubscribe('/user' + this.submissionChannel);
        }
        if (this.participationChannel) {
            this.jhiWebsocketService.unsubscribe(this.participationChannel);
        }
        if (this.quizExerciseChannel) {
            this.jhiWebsocketService.unsubscribe(this.quizExerciseChannel);
        }
        if (this.onConnected) {
            this.jhiWebsocketService.unbind('connect', this.onConnected);
        }
        if (this.onDisconnected) {
            this.jhiWebsocketService.unbind('disconnect', this.onDisconnected);
        }
        this.subscription.unsubscribe();
        this.subscriptionData.unsubscribe();
    }

    /**
     * loads latest submission from server and sets up socket connection
     */
    init() {
        // listen to connect / disconnect events
        this.onConnected = () => {
            this.disconnected = false;
            if (this.unsavedChanges && this.sendWebsocket) {
                this.sendWebsocket(this.submission);
            }
        };
        this.jhiWebsocketService.bind('connect', () => {
            this.onConnected();
        });
        this.onDisconnected = () => {
            this.disconnected = true;
            if (this.outstandingWebsocketResponses > 0) {
                this.outstandingWebsocketResponses = 0;
                this.isSaving = false;
                this.unsavedChanges = true;
            }
        };
        this.jhiWebsocketService.bind('disconnect', () => {
            this.onDisconnected();
        });

        this.subscribeToWebsocketChannels();

        // load the quiz (and existing submission if quiz has started)
        this.exerciseParticipationService.find(1, this.id).subscribe(
            (participation: Participation) => {
                this.applyParticipationFull(participation);
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }

    /**
     * loads quizExercise and starts practice mode
     */
    initPracticeMode() {
        this.quizExerciseService.findForStudent(this.id).subscribe(
            (res: HttpResponse<QuizExercise>) => {
                if (res.body.isOpenForPractice) {
                    this.startQuizPreviewOrPractice(res.body);
                } else {
                    alert('Error: This quiz is not open for practice!');
                }
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    /**
     * loads quiz exercise and starts preview mode
     */
    initPreview() {
        this.quizExerciseService.find(this.id).subscribe(
            (res: HttpResponse<QuizExercise>) => {
                this.startQuizPreviewOrPractice(res.body);
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    initShowSolution() {
        this.quizExerciseService.find(this.id).subscribe(
            (res: HttpResponse<QuizExercise>) => {
                this.quizExercise = res.body;
                this.initQuiz();
                this.showingResult = true;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    /**
     * Start the given quiz in practice or preview mode
     *
     * @param quizExercise {object} the quizExercise to start
     */
    startQuizPreviewOrPractice(quizExercise) {
        // init quiz
        this.quizExercise = quizExercise;
        this.initQuiz();

        // randomize order
        this.randomizeOrder(this.quizExercise);

        // init empty submission
        this.submission = {};

        // adjust end date
        this.quizExercise.adjustedDueDate = moment().add(this.quizExercise.duration, 'seconds');

        // auto submit when time is up
        this.runningTimeouts.push(setTimeout( () => { this.onSubmit(); }, quizExercise.duration * 1000));
    }

    /**
     * subscribe to any outstanding websocket channels
     */
    subscribeToWebsocketChannels() {
        if (!this.submissionChannel) {
            this.submissionChannel = '/topic/quizExercise/' + this.id + '/submission';

            // submission channel => react to new submissions
            this.jhiWebsocketService.subscribe('/user' + this.submissionChannel);
            this.jhiWebsocketService.receive('/user' + this.submissionChannel).subscribe(
                payload => {
                    this.onSaveSuccess(payload);
                }, error => {}
            );

            // save answers (submissions) through websocket
            this.sendWebsocket = data => {
                this.outstandingWebsocketResponses++;
                this.jhiWebsocketService.send(this.submissionChannel, data);
            };
        }

        if (!this.participationChannel) {
            this.participationChannel = '/user/topic/quizExercise/' + this.id + '/participation';

            // participation channel => react to new results
            this.jhiWebsocketService.subscribe(this.participationChannel);
            this.jhiWebsocketService.receive(this.participationChannel).subscribe(
                payload => {
                    if (this.waitingForQuizStart) {
                        // only apply completely if quiz is hasn't started to prevent jumping ui during participation
                        this.applyParticipationFull(payload);
                    } else {
                        // update quizExercise and results / submission
                        this.applyParticipationAfterStart(payload);
                    }
                }, error => {}
            );
        }

        if (!this.quizExerciseChannel) {
            this.quizExerciseChannel = '/topic/quizExercise/' + this.id;

            // quizExercise channel => react to changes made to quizExercise (e.g. start date)
            this.jhiWebsocketService.subscribe(this.quizExerciseChannel);
            this.jhiWebsocketService.receive(this.quizExerciseChannel).subscribe(
                payload => {
                    if (this.waitingForQuizStart) {
                        this.applyQuizFull(payload);
                    }
                }, error => {}
            );
        }
    }

    /**
     * updates all displayed (relative) times in the UI
     */
    updateDisplayedTimes() {
        // update remaining time
        if (this.quizExercise && this.quizExercise.adjustedDueDate) {
            const endDate = this.quizExercise.adjustedDueDate;
            if (endDate.isAfter(moment())) {
                // quiz is still running => calculate remaining seconds and generate text based on that
                this.remainingTimeSeconds = endDate.diff(moment(), 'seconds');
                this.remainingTimeText = this.relativeTimeText(this.remainingTimeSeconds);
            } else {
                // quiz is over => set remaining seconds to negative, to deactivate 'Submit' button
                this.remainingTimeSeconds = -1;
                this.remainingTimeText = 'Quiz has ended!';
            }
        } else {
            // remaining time is unknown => Set remaining seconds to 0, to keep 'Submit' button enabled
            this.remainingTimeSeconds = 0;
            this.remainingTimeText = '?';
        }

        // update submission time
        if (this.submission && this.submission.adjustedSubmissionDate) {
            // exact value is not important => use default relative time from moment for better readability and less distraction
            this.lastSavedTimeText = moment(this.submission.adjustedSubmissionDate).fromNow();
        }

        // update time until start
        if (this.quizExercise && this.quizExercise.adjustedReleaseDate) {
            if (this.quizExercise.adjustedReleaseDate.isAfter(moment())) {
                this.timeUntilStart = this.relativeTimeText(this.quizExercise.adjustedReleaseDate.diff(moment(), 'seconds'));
            } else {
                this.timeUntilStart = 'Now';
            }
        } else {
            this.timeUntilStart = '';
        }
    }

    /**
     * Express the given timespan as humanized text
     *
     * @param remainingTimeSeconds {number} the amount of seconds to display
     * @return {string} humanized text for the given amount of seconds
     */
    relativeTimeText(remainingTimeSeconds) {
        if (remainingTimeSeconds > 210) {
            return Math.ceil(remainingTimeSeconds / 60) + ' min';
        } else if (remainingTimeSeconds > 59) {
            return Math.floor(remainingTimeSeconds / 60) + ' min ' + (remainingTimeSeconds % 60) + ' s';
        } else {
            return remainingTimeSeconds + ' s';
        }
    }

    /**
     * Initialize the selections / mappings for each question with an empty array
     */
    initQuiz() {
        // calculate score
        this.totalScore = this.quizExercise.questions ? this.quizExercise.questions.reduce(function(score, question) {
            return score + question.score;
        }, 0) : 0;

        // prepare selection arrays for each question
        this.selectedAnswerOptions = {};
        this.dragAndDropMappings = {};

        if (this.quizExercise.questions) {
            this.quizExercise.questions.forEach(question => {
                switch (question.type) {
                    case 'multiple-choice':
                        // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        this.selectedAnswerOptions[question.id] = [];
                        break;
                    case 'drag-and-drop':
                        // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        this.dragAndDropMappings[question.id] = [];
                        break;
                    default:
                        console.error('Unknown question type: ' + question.type);
                }
            }, this);
        }
    }

    /**
     * applies the data from the model to the UI (reverse of applySelection):
     *
     * Sets the checkmarks (selected answers) for all questions according to the submission data
     * this needs to be done when we get new submission data, e.g. through the websocket connection
     */
    applySubmission() {
        // create dictionaries (key: questionID, value: Array of selected answerOptions / mappings)
        // for the submittedAnswers to hand the selected options / mappings in individual arrays to the question components
        this.selectedAnswerOptions = {};
        this.dragAndDropMappings = {};

        if (this.quizExercise.questions) {
            // iterate through all questions of this quiz
            this.quizExercise.questions.forEach(question => {
                // find the submitted answer that belongs to this question
                const submittedAnswer = this.submission.submittedAnswers.find(function(answer) {
                    return answer.question.id === question.id;
                });
                switch (question.type) {
                    case 'multiple-choice':
                        // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        this.selectedAnswerOptions[question.id] = submittedAnswer ? submittedAnswer.selectedOptions : [];
                        break;
                    case 'drag-and-drop':
                        // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        this.dragAndDropMappings[question.id] = submittedAnswer ? submittedAnswer.mappings : [];
                        break;
                    default:
                        console.error('Unknown question type: ' + question.type);
                }
            }, this);
        }
    }

    /**
     * updates the model according to UI state (reverse of applySubmission):
     *
     * Creates the submission from the user's selection
     * this needs to be done when we want to send the submission
     * either for saving (through websocket)
     * or for submitting (through REST call)
     */
    applySelection() {
        // convert the selection dictionary (key: questionID, value: Array of selected answerOptions / mappings)
        // into an array of submittedAnswer objects and save it as the submittedAnswers of the submission
        this.submission.submittedAnswers = [];

        // for multiple-choice questions
        Object.keys(this.selectedAnswerOptions).forEach(questionID => {
            // find the question object for the given question id
            const question = this.quizExercise.questions.find(function(selectedQuestion) {
                return selectedQuestion.id === Number(questionID);
            });
            if (!question) {
                console.error('question not found for ID: ' + questionID);
                return;
            }
            // generate the submittedAnswer object
            this.submission.submittedAnswers.push({
                question,
                selectedOptions: this.selectedAnswerOptions[questionID],
                type: question.type
            });
        }, this);

        // for drag-and-drop questions
        Object.keys(this.dragAndDropMappings).forEach(questionID => {
            // find the question object for the given question id
            const question = this.quizExercise.questions.find(function(localQuestion) {
                return localQuestion.id === Number(questionID);
            });
            if (!question) {
                console.error('question not found for ID: ' + questionID);
                return;
            }
            // generate the submittedAnswer object
            this.submission.submittedAnswers.push({
                question,
                mappings: this.dragAndDropMappings[questionID],
                type: question.type
            });
        }, this);
    }

    /**
     * Apply the data of the participation, replacing all old data
     */
    applyParticipationFull(participation) {
        this.applyQuizFull(participation.exercise);

        // apply submission if it exists
        if (participation.results.length) {
            this.submission = participation.results[0].submission;

            // update submission time
            this.updateSubmissionTime();

            // show submission answers in UI
            this.applySubmission();

            if (participation.results[0].resultString && this.quizExercise.ended) {
                // quiz has ended and results are available
                this.showResult(participation.results);
            }
        } else {
            this.submission = {};
        }
    }

    /**
     * apply the data of the quiz, replacing all old data and enabling reconnect if necessary
     * @param quizExercise
     */
    applyQuizFull(quizExercise) {
        this.quizExercise = quizExercise;
        this.initQuiz();

        // check if quiz has started
        if (this.quizExercise.started) {
            // quiz has started
            this.waitingForQuizStart = false;

            // update timeDifference
            this.quizExercise.adjustedDueDate = moment().add(this.quizExercise.remainingTime, 'seconds');
            this.timeDifference = moment(this.quizExercise.dueDate).diff(this.quizExercise.adjustedDueDate, 'seconds');

            // check if quiz hasn't ended
            if (!this.quizExercise.ended) {
                // enable automatic websocket reconnect
                this.jhiWebsocketService.enableReconnect();

                // apply randomized order where necessary
                this.randomizeOrder(this.quizExercise);

                // alert user 5 seconds after quiz has ended (in case websocket didn't work)
                this.runningTimeouts.push(
                    setTimeout(() => {
                        if (this.disconnected && !this.showingResult) {
                            alert('Loading results failed. Please wait a few seconds and refresh the page manually.');
                        }
                    }, (this.quizExercise.remainingTime + 5) * 1000)
                );
            }
        } else {
            // quiz hasn't started yet
            this.waitingForQuizStart = true;

            // enable automatic websocket reconnect
            this.jhiWebsocketService.enableReconnect();

            if (this.quizExercise.isPlannedToStart) {
                // synchronize time with server
                this.quizExercise.releaseDate = moment(this.quizExercise.releaseDate);
                this.quizExercise.adjustedReleaseDate = moment().add(this.quizExercise.timeUntilPlannedStart, 'seconds');
            }
        }
    }

    applyParticipationAfterStart(participation) {
        if (participation.results.length &&
            participation.results[0].resultString &&
            participation.exercise.ended) {
            // quiz has ended and results are available
            this.submission = participation.results[0].submission;

            // update submission time
            this.updateSubmissionTime();
            this.transferInformationToQuizExercise(participation.exercise);
            this.applySubmission();
            this.showResult(participation.results);
        }
    }

    /**
     * Transfer additional information (explanations, correct answers) from
     * the given full quiz exercise to quizExercise
     *
     * @param fullQuizExercise {object} the quizExercise containing additional information
     */
    transferInformationToQuizExercise(fullQuizExercise) {
        this.quizExercise.questions.forEach(function(question) {
            // find updated question
            const fullQuestion = fullQuizExercise.questions.find(function(localQuestion) {
                return question.id === localQuestion.id;
            });
            if (fullQuestion) {
                question.explanation = fullQuestion.explanation;

                switch (question.type) {
                    case 'multiple-choice':
                        question.answerOptions.forEach(function(answerOption) {
                            // find updated answerOption
                            const fullAnswerOption = fullQuestion.answerOptions.find(function(option) {
                                return answerOption.id === option.id;
                            });
                            if (fullAnswerOption) {
                                answerOption.explanation = fullAnswerOption.explanation;
                                answerOption.isCorrect = fullAnswerOption.isCorrect;
                            }
                        });
                        break;
                    case 'drag-and-drop':
                        question.correctMappings = fullQuestion.correctMappings;
                        break;
                }
            }
        }, this);
    }

    /**
     * Display results of quiz
     * @param results
     */
    showResult(results: Result[]) {
        this.result = results[0];
        if (this.result) {
            this.showingResult = true;

            // disable automatic websocket reconnect
            this.jhiWebsocketService.disableReconnect();

            // assign user score (limit decimal places to 2)
            this.userScore = this.submission.scoreInPoints ? Math.round(this.submission.scoreInPoints * 100) / 100 : 0;

            // create dictionary with scores for each question
            this.questionScores = {};
            this.submission.submittedAnswers.forEach(submittedAnswer => {
                // limit decimal places to 2
                this.questionScores[submittedAnswer.question.id] = Math.round(submittedAnswer.scoreInPoints * 100) / 100;
            }, this);
        }
    }

    /**
     * Randomize the order of the questions
     * (and answerOptions or dragItems within each question)
     * if randomizeOrder is true
     *
     * @param quizExercise {object} the quizExercise to randomize elements in
     */
    randomizeOrder(quizExercise: QuizExercise) {
        if (quizExercise.questions) {
            // shuffle questions
            if (quizExercise.randomizeQuestionOrder) {
                this.shuffle(quizExercise.questions);
            }

            // shuffle answerOptions / dragItems within questions
            quizExercise.questions.forEach(question => {
                if (question.randomizeOrder) {
                    switch (question.type) {
                        case 'multiple-choice':
                            this.shuffle((question as MultipleChoiceQuestion).answerOptions);
                            break;
                        case 'drag-and-drop':
                            this.shuffle((question as DragAndDropQuestion).dragItems);
                            break;
                    }
                }
            }, this);
        }
    }

    /**
     * Shuffles array in place.
     * @param {Array} items An array containing the items.
     */
    shuffle(items) {
        for (let i = items.length - 1; i > 0; i--) {
            const pickedIndex = Math.floor(Math.random() * (i + 1));
            const picked = items[pickedIndex];
            items[pickedIndex] = items[i];
            items[i] = picked;
        }
    }

    /**
     * Callback method to be triggered when the user (de-)selects answers
     */
    onSelectionChanged() {
        this.applySelection();
        if (this.sendWebsocket) {
            if (!this.disconnected) {
                this.isSaving = true;
                this.sendWebsocket(this.submission);
            } else {
                this.unsavedChanges = true;
            }
        }
    }

    /**
     * update the value for adjustedSubmissionDate in submission
     */
    updateSubmissionTime() {
        if (this.submission.submissionDate) {
            this.submission.adjustedSubmissionDate = moment(this.submission.submissionDate).subtract(this.timeDifference, 'seconds').toDate();
            if (Math.abs(moment(this.submission.adjustedSubmissionDate).diff(moment(), 'seconds')) < 2) {
                this.justSaved = true;
                this.timeoutJustSaved();
            }
        }
    }

    /**
     * Callback function for handling response after saving submission to server
     * @param response The response data from the server
     */
    onSaveSuccess(response) {
        if (!response) {
            // TODO OLD: Include reason why saving failed
            alert('Saving Answers failed.');
            this.unsavedChanges = true;
            this.isSubmitting = false;
            if (this.outstandingWebsocketResponses > 0) {
                this.outstandingWebsocketResponses--;
            }
            if (this.outstandingWebsocketResponses === 0) {
                this.isSaving = false;
            }
            return;
        }
        if (response.submitted) {
            this.outstandingWebsocketResponses = 0;
            this.isSaving = false;
            this.unsavedChanges = false;
            this.isSubmitting = false;
            this.submission = response;
            this.updateSubmissionTime();
            this.applySubmission();
        } else if (this.outstandingWebsocketResponses === 0) {
            this.isSaving = false;
            this.unsavedChanges = false;
            this.submission = response;
            this.updateSubmissionTime();
            this.applySubmission();
        } else {
            this.outstandingWebsocketResponses--;
            if (this.outstandingWebsocketResponses === 0) {
                this.isSaving = false;
                this.unsavedChanges = false;
                if (response) {
                    this.submission.submissionDate = response.submissionDate;
                    this.updateSubmissionTime();
                }
            }
        }
    }

    /**
     * This function is called when the user clicks the 'Submit' button
     */
    onSubmit() {
        this.applySelection();
        this.isSubmitting = true;
        switch (this.mode) {
            case 'practice':
                if (!this.submission.id) {
                    this.quizSubmissionService.submitForPractice(this.submission, 1, this.id).subscribe(
                        (res: HttpResponse<Result>) => {
                            this.onSubmitPracticeOrPreviewSuccess(res.body);
                        },
                        (res: HttpErrorResponse) => this.onSubmitError(res.message)
                    );
                }
                break;
            case 'preview':
                if (!this.submission.id) {
                    this.quizSubmissionService.submitForPreview(this.submission, 1, this.id).subscribe(
                        (res: HttpResponse<Result>) => {
                            this.onSubmitPracticeOrPreviewSuccess(res.body);
                        },
                        (res: HttpErrorResponse) => this.onSubmitError(res.message)
                    );
                }
                break;
            case 'default':
                if (this.disconnected || !this.submissionChannel) {
                    alert('Cannot Submit while disconnected. Don\'t worry, answers that were saved' +
                    'while you were still connected will be submitted automatically when the quiz ends.');
                    this.isSubmitting = false;
                    return;
                }
                // send submission through websocket with 'submitted = true'
                this.jhiWebsocketService.send(this.submissionChannel, {
                    submittedAnswers: this.submission.submittedAnswers,
                    submitted: true
                });
                break;
        }
    }

    /**
     * Callback function for handling response after submitting for practice or preview
     * @param response
     */
    onSubmitPracticeOrPreviewSuccess(result: Result) {
        this.isSubmitting = false;
        this.submission = result.submission;
        this.applySubmission();
        this.showResult([result]);
    }

    /**
     * Callback function for handling error when submitting
     * @param error
     */
    onSubmitError(error) {
        console.error(error);
        alert('Submitting was not possible. Please try again later. If your answers have been saved, you can also wait until the quiz has finished.');
        this.isSubmitting = false;
    }
}
