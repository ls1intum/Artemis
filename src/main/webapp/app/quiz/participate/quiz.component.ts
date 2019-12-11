import { Component, OnDestroy, OnInit, QueryList, ViewChildren } from '@angular/core';
import * as moment from 'moment';
import * as _ from 'lodash';
import { QuizExercise, QuizExerciseService } from '../../entities/quiz-exercise';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { QuizSubmission, QuizSubmissionService } from '../../entities/quiz-submission';
import { ParticipationService, ParticipationWebsocketService } from '../../entities/participation';
import { Result } from 'app/entities/result';
import { DragAndDropQuestion } from 'app/entities/drag-and-drop-question';
import { MultipleChoiceQuestion } from 'app/entities/multiple-choice-question';
import { ShortAnswerQuestion } from 'app/entities/short-answer-question';
import { MultipleChoiceSubmittedAnswer } from 'app/entities/multiple-choice-submitted-answer';
import { DragAndDropSubmittedAnswer } from 'app/entities/drag-and-drop-submitted-answer';
import { ShortAnswerSubmittedAnswer } from 'app/entities/short-answer-submitted-answer';
import { QuizQuestionType } from 'app/entities/quiz-question';
import { MultipleChoiceQuestionComponent } from 'app/quiz/participate/multiple-choice-question/multiple-choice-question.component';
import { DragAndDropQuestionComponent } from 'app/quiz/participate/drag-and-drop-question/drag-and-drop-question.component';
import { ShortAnswerQuestionComponent } from 'app/quiz/participate/short-answer-question/short-answer-question.component';
import { DragAndDropMapping } from 'app/entities/drag-and-drop-mapping';
import { AnswerOption } from 'app/entities/answer-option';
import { ShortAnswerSubmittedText } from 'app/entities/short-answer-submitted-text';
import { TranslateService } from '@ngx-translate/core';
import * as smoothscroll from 'smoothscroll-polyfill';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { DeviceDetectorService } from 'ngx-device-detector';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';

@Component({
    selector: 'jhi-quiz',
    templateUrl: './quiz.component.html',
    providers: [ParticipationService],
    styleUrls: ['./quiz.component.scss'],
})
export class QuizComponent implements OnInit, OnDestroy {
    // make constants available to html for comparison
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;
    readonly ButtonSize = ButtonSize;
    readonly ButtonType = ButtonType;

    @ViewChildren(MultipleChoiceQuestionComponent)
    mcQuestionComponents: QueryList<MultipleChoiceQuestionComponent>;

    @ViewChildren(DragAndDropQuestionComponent)
    dndQuestionComponents: QueryList<DragAndDropQuestionComponent>;

    @ViewChildren(ShortAnswerQuestionComponent)
    shortAnswerQuestionComponents: QueryList<ShortAnswerQuestionComponent>;

    private subscription: Subscription;
    private subscriptionData: Subscription;

    timeDifference = 0;
    outstandingWebsocketResponses = 0;

    runningTimeouts = new Array<any>(); // actually the function type setTimeout(): (handler: any, timeout?: any, ...args: any[]): number

    isSubmitting = false;
    isSaving = false;
    lastSavedTimeText = '';
    justSaved = false;
    waitingForQuizStart = false;

    remainingTimeText = '?';
    remainingTimeSeconds = 0;
    timeUntilStart = '0';
    disconnected = true;
    unsavedChanges = false;

    sendWebsocket: (submission: QuizSubmission) => void;
    showingResult = false;
    userScore: number;

    mode: string;
    submission = new QuizSubmission();
    quizExercise: QuizExercise;
    totalScore: number;
    selectedAnswerOptions = new Map<number, AnswerOption[]>();
    dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
    shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();
    result: Result;
    questionScores = {};
    quizId: number;
    interval: any;

    /**
     * Websocket channels
     */
    submissionChannel: string;
    participationChannel: Subscription;
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

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private quizExerciseService: QuizExerciseService,
        private participationService: ParticipationService,
        private participationWebsocketService: ParticipationWebsocketService,
        private route: ActivatedRoute,
        private jhiAlertService: JhiAlertService,
        private quizSubmissionService: QuizSubmissionService,
        private translateService: TranslateService,
        private deviceService: DeviceDetectorService,
    ) {
        smoothscroll.polyfill();
    }

    ngOnInit() {
        // set correct mode
        this.subscriptionData = this.route.data.subscribe(data => {
            this.mode = data.mode;
            this.subscription = this.route.params.subscribe(params => {
                this.quizId = params['id'];
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

        // at the moment, this is always enabled
        // disable automatic websocket reconnect
        // this.jhiWebsocketService.disableReconnect();

        if (this.submissionChannel) {
            this.jhiWebsocketService.unsubscribe('/user' + this.submissionChannel);
        }
        if (this.participationChannel) {
            this.participationChannel.unsubscribe();
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
        this.participationService.findParticipation(this.quizId).subscribe(
            (response: HttpResponse<StudentParticipation>) => {
                this.applyParticipationFull(response.body!);
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    /**
     * loads quizExercise and starts practice mode
     */
    initPracticeMode() {
        this.quizExerciseService.findForStudent(this.quizId).subscribe(
            (res: HttpResponse<QuizExercise>) => {
                if (res.body && res.body.isOpenForPractice) {
                    this.startQuizPreviewOrPractice(res.body);
                } else {
                    alert('Error: This quiz is not open for practice!');
                }
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
    }

    /**
     * loads quiz exercise and starts preview mode
     */
    initPreview() {
        this.quizExerciseService.find(this.quizId).subscribe(
            (res: HttpResponse<QuizExercise>) => {
                this.startQuizPreviewOrPractice(res.body!);
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
    }

    initShowSolution() {
        this.quizExerciseService.find(this.quizId).subscribe(
            (res: HttpResponse<QuizExercise>) => {
                this.quizExercise = res.body!;
                this.initQuiz();
                this.showingResult = true;
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
    }

    /**
     * Start the given quiz in practice or preview mode
     *
     * @param quizExercise {object} the quizExercise to start
     */
    startQuizPreviewOrPractice(quizExercise: QuizExercise) {
        // init quiz
        this.quizExercise = quizExercise;
        this.initQuiz();

        // randomize order
        this.randomizeOrder(this.quizExercise);

        // init empty submission
        this.submission = new QuizSubmission();

        // adjust end date
        this.quizExercise.adjustedDueDate = moment().add(this.quizExercise.duration, 'seconds');

        // auto submit when time is up
        this.runningTimeouts.push(
            setTimeout(() => {
                this.onSubmit();
            }, quizExercise.duration * 1000),
        );
    }

    /**
     * subscribe to any outstanding websocket channels
     */
    subscribeToWebsocketChannels() {
        if (!this.submissionChannel) {
            this.submissionChannel = '/topic/quizExercise/' + this.quizId + '/submission';

            // submission channel => react to new submissions
            this.jhiWebsocketService.subscribe('/user' + this.submissionChannel);
            this.jhiWebsocketService.receive('/user' + this.submissionChannel).subscribe(
                payload => {
                    if (payload === 'the quiz is not active') {
                        this.onSaveSuccess(null, payload);
                    } else if (payload === 'you have already submitted the quiz') {
                        this.onSaveSuccess(null, payload);
                    } else {
                        this.onSaveSuccess(payload as QuizSubmission, null);
                    }
                },
                error => {
                    this.onSubmitError(error);
                },
            );

            // save answers (submissions) through websocket
            this.sendWebsocket = (submission: QuizSubmission) => {
                this.outstandingWebsocketResponses++;
                this.jhiWebsocketService.send(this.submissionChannel, submission);
            };
        }

        if (!this.participationChannel) {
            this.participationWebsocketService.addExerciseForNewParticipation(this.quizId);
            this.participationChannel = this.participationWebsocketService.subscribeForParticipationChanges().subscribe((changedParticipation: StudentParticipation) => {
                if (changedParticipation && this.quizExercise && changedParticipation.exercise.id === this.quizExercise.id) {
                    if (this.waitingForQuizStart) {
                        // only apply completely if quiz hasn't started to prevent jumping ui during participation
                        this.applyParticipationFull(changedParticipation);
                    } else {
                        // update quizExercise and results / submission
                        this.applyParticipationAfterQuizEnd(changedParticipation);
                    }
                }
            });
        }

        if (!this.quizExerciseChannel) {
            this.quizExerciseChannel = '/topic/quizExercise/' + this.quizId;

            // quizExercise channel => react to changes made to quizExercise (e.g. start date)
            this.jhiWebsocketService.subscribe(this.quizExerciseChannel);
            this.jhiWebsocketService.receive(this.quizExerciseChannel).subscribe(
                payload => {
                    if (this.waitingForQuizStart) {
                        this.applyQuizFull(payload);
                    }
                },
                error => {},
            );
        }
    }

    /**
     * updates all displayed (relative) times in the UI
     */
    updateDisplayedTimes() {
        const translationBasePath = 'showStatistic.';
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
                this.remainingTimeText = this.translateService.instant(translationBasePath + 'quizhasEnded');
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
                this.timeUntilStart = this.translateService.instant(translationBasePath + 'now');
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
    relativeTimeText(remainingTimeSeconds: number) {
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
        this.totalScore = this.quizExercise.quizQuestions
            ? this.quizExercise.quizQuestions.reduce(function(score, question) {
                  return score + question.score;
              }, 0)
            : 0;

        // prepare selection arrays for each question
        this.selectedAnswerOptions = new Map<number, AnswerOption[]>();
        this.dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
        this.shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

        if (this.quizExercise.quizQuestions) {
            this.quizExercise.quizQuestions.forEach(question => {
                if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                    // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.selectedAnswerOptions[question.id] = [];
                } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                    // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.dragAndDropMappings[question.id] = [];
                } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                    // add the array of submitted texts to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.shortAnswerSubmittedTexts[question.id] = [];
                } else {
                    console.error('Unknown question type: ' + question);
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
        this.selectedAnswerOptions = new Map<number, AnswerOption[]>();
        this.dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
        this.shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

        if (this.quizExercise.quizQuestions) {
            // iterate through all questions of this quiz
            this.quizExercise.quizQuestions.forEach(question => {
                // find the submitted answer that belongs to this question, only when submitted answers already exist
                const submittedAnswer = this.submission.submittedAnswers
                    ? this.submission.submittedAnswers.find(answer => {
                          return answer.quizQuestion.id === question.id;
                      })
                    : null;

                if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                    // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    if (submittedAnswer) {
                        const selectedOptions = (submittedAnswer as MultipleChoiceSubmittedAnswer).selectedOptions;
                        this.selectedAnswerOptions[question.id] = selectedOptions ? selectedOptions : [];
                    } else {
                        // not found, set to empty array
                        this.selectedAnswerOptions[question.id] = [];
                    }
                } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                    // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    if (submittedAnswer) {
                        const mappings = (submittedAnswer as DragAndDropSubmittedAnswer).mappings;
                        this.dragAndDropMappings[question.id] = mappings ? mappings : [];
                    } else {
                        // not found, set to empty array
                        this.dragAndDropMappings[question.id] = [];
                    }
                } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                    // add the array of submitted texts to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    if (submittedAnswer) {
                        const submittedTexts = (submittedAnswer as ShortAnswerSubmittedAnswer).submittedTexts;
                        this.shortAnswerSubmittedTexts[question.id] = submittedTexts ? submittedTexts : [];
                    } else {
                        // not found, set to empty array
                        this.shortAnswerSubmittedTexts[question.id] = [];
                    }
                } else {
                    console.error('Unknown question type: ' + question);
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
            const question = this.quizExercise.quizQuestions.find(function(selectedQuestion) {
                return selectedQuestion.id === Number(questionID);
            });
            if (!question) {
                console.error('question not found for ID: ' + questionID);
                return;
            }
            // generate the submittedAnswer object
            const mcSubmittedAnswer = new MultipleChoiceSubmittedAnswer();
            mcSubmittedAnswer.quizQuestion = question;
            mcSubmittedAnswer.selectedOptions = this.selectedAnswerOptions[questionID];
            this.submission.submittedAnswers.push(mcSubmittedAnswer);
        }, this);

        // for drag-and-drop questions
        Object.keys(this.dragAndDropMappings).forEach(questionID => {
            // find the question object for the given question id
            const question = this.quizExercise.quizQuestions.find(function(localQuestion) {
                return localQuestion.id === Number(questionID);
            });
            if (!question) {
                console.error('question not found for ID: ' + questionID);
                return;
            }
            // generate the submittedAnswer object
            const dndSubmittedAnswer = new DragAndDropSubmittedAnswer();
            dndSubmittedAnswer.quizQuestion = question;
            dndSubmittedAnswer.mappings = this.dragAndDropMappings[questionID];
            this.submission.submittedAnswers.push(dndSubmittedAnswer);
        }, this);
        // for short-answer questions
        Object.keys(this.shortAnswerSubmittedTexts).forEach(questionID => {
            // find the question object for the given question id
            const question = this.quizExercise.quizQuestions.find(function(localQuestion) {
                return localQuestion.id === Number(questionID);
            });
            if (!question) {
                console.error('question not found for ID: ' + questionID);
                return;
            }
            // generate the submittedAnswer object
            const shortAnswerSubmittedAnswer = new ShortAnswerSubmittedAnswer();
            shortAnswerSubmittedAnswer.quizQuestion = question;
            shortAnswerSubmittedAnswer.submittedTexts = this.shortAnswerSubmittedTexts[questionID];
            this.submission.submittedAnswers.push(shortAnswerSubmittedAnswer);
        }, this);
    }

    /**
     * Apply the data of the participation, replacing all old data
     */
    applyParticipationFull(participation: StudentParticipation) {
        this.applyQuizFull(participation.exercise as QuizExercise);

        // apply submission if it exists
        if (participation.results.length) {
            this.submission = participation.results[0].submission as QuizSubmission;

            // update submission time
            this.updateSubmissionTime();

            // show submission answers in UI
            this.applySubmission();

            if (participation.results[0].resultString && this.quizExercise.ended) {
                // quiz has ended and results are available
                this.showResult(participation.results[0]);
            }
        } else {
            this.submission = new QuizSubmission();
        }
    }

    /**
     * apply the data of the quiz, replacing all old data and enabling reconnect if necessary
     * @param quizExercise
     */
    applyQuizFull(quizExercise: QuizExercise) {
        this.quizExercise = quizExercise;
        this.initQuiz();

        // check if quiz has started
        if (this.quizExercise.started) {
            // quiz has started
            this.waitingForQuizStart = false;

            // update timeDifference
            this.quizExercise.adjustedDueDate = moment().add(this.quizExercise.remainingTime, 'seconds');
            this.timeDifference = moment(this.quizExercise.dueDate!).diff(this.quizExercise.adjustedDueDate, 'seconds');

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
                    }, (this.quizExercise.remainingTime + 5) * 1000),
                );
            }
        } else {
            // quiz hasn't started yet
            this.waitingForQuizStart = true;

            // enable automatic websocket reconnect
            this.jhiWebsocketService.enableReconnect();

            if (this.quizExercise.isPlannedToStart) {
                // synchronize time with server
                this.quizExercise.releaseDate = moment(this.quizExercise.releaseDate!);
                this.quizExercise.adjustedReleaseDate = moment().add(this.quizExercise.timeUntilPlannedStart, 'seconds');
            }
        }
    }

    /*
     * This method only handles the update of the quiz after the quiz has ended
     */
    applyParticipationAfterQuizEnd(participation: StudentParticipation) {
        const quizExercise = participation.exercise as QuizExercise;
        if (participation.results.length && participation.results[0].resultString && quizExercise.ended) {
            // quiz has ended and results are available
            this.submission = participation.results[0].submission as QuizSubmission;

            // update submission time
            this.updateSubmissionTime();
            this.transferInformationToQuizExercise(quizExercise);
            this.applySubmission();
            this.showResult(participation.results[0]);
        }
    }

    /**
     * Transfer additional information (explanations, correct answers) from
     * the given full quiz exercise to quizExercise.
     * This method is typically invoked after the quiz has ended and makes sure that the (random) order of the quiz
     * questions and answer options for the particular user is respected
     *
     * @param fullQuizExerciseFromServer {object} the quizExercise containing additional information
     */
    transferInformationToQuizExercise(fullQuizExerciseFromServer: QuizExercise) {
        this.quizExercise.quizQuestions.forEach(function(clientQuestion) {
            // find updated question
            const fullQuestionFromServer = fullQuizExerciseFromServer.quizQuestions.find(function(fullQuestion) {
                return clientQuestion.id === fullQuestion.id;
            });
            if (fullQuestionFromServer) {
                clientQuestion.explanation = fullQuestionFromServer.explanation;

                if (clientQuestion.type === QuizQuestionType.MULTIPLE_CHOICE) {
                    const mcClientQuestion = clientQuestion as MultipleChoiceQuestion;
                    const mcFullQuestionFromServer = fullQuestionFromServer as MultipleChoiceQuestion;

                    const answerOptions = mcClientQuestion.answerOptions!;
                    answerOptions.forEach(function(clientAnswerOption) {
                        // find updated answerOption
                        const fullAnswerOptionFromServer = mcFullQuestionFromServer.answerOptions!.find(function(option) {
                            return clientAnswerOption.id === option.id;
                        });
                        if (fullAnswerOptionFromServer) {
                            clientAnswerOption.explanation = fullAnswerOptionFromServer.explanation;
                            clientAnswerOption.isCorrect = fullAnswerOptionFromServer.isCorrect;
                        }
                    });
                } else if (clientQuestion.type === QuizQuestionType.DRAG_AND_DROP) {
                    const dndClientQuestion = clientQuestion as DragAndDropQuestion;
                    const dndFullQuestionFromServer = fullQuestionFromServer as DragAndDropQuestion;

                    dndClientQuestion.correctMappings = dndFullQuestionFromServer.correctMappings;
                } else if (clientQuestion.type === QuizQuestionType.SHORT_ANSWER) {
                    const shortAnswerClientQuestion = clientQuestion as ShortAnswerQuestion;
                    const shortAnswerFullQuestionFromServer = fullQuestionFromServer as ShortAnswerQuestion;
                    shortAnswerClientQuestion.correctMappings = shortAnswerFullQuestionFromServer.correctMappings;
                } else {
                    console.log('Unknown question type ' + clientQuestion);
                }
            }
        }, this);

        // make sure that a possible explanation is updated correctly in all sub components
        this.mcQuestionComponents.forEach(function(mcQuestionComponent) {
            mcQuestionComponent.watchCollection();
        });
        this.dndQuestionComponents.forEach(function(dndQuestionComponent) {
            dndQuestionComponent.watchCollection();
        });
        this.shortAnswerQuestionComponents.forEach(function(shortAnswerQuestionComponent) {
            shortAnswerQuestionComponent.watchCollection();
        });
    }

    /**
     * Display results of the quiz for the user
     * @param result
     */
    showResult(result: Result) {
        this.result = result;
        if (this.result) {
            this.showingResult = true;

            // at the moment, this is always enabled
            // disable automatic websocket reconnect
            // this.jhiWebsocketService.disableReconnect();

            // assign user score (limit decimal places to 2)
            this.userScore = this.submission.scoreInPoints ? Math.round(this.submission.scoreInPoints * 100) / 100 : 0;

            // create dictionary with scores for each question
            this.questionScores = {};
            this.submission.submittedAnswers.forEach(submittedAnswer => {
                // limit decimal places to 2
                this.questionScores[submittedAnswer.quizQuestion.id] = Math.round(submittedAnswer.scoreInPoints * 100) / 100;
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
        if (quizExercise.quizQuestions) {
            // shuffle questions
            if (quizExercise.randomizeQuestionOrder) {
                this.shuffle(quizExercise.quizQuestions);
            }

            // shuffle answerOptions / dragItems within questions
            quizExercise.quizQuestions.forEach(question => {
                if (question.randomizeOrder) {
                    if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                        this.shuffle((question as MultipleChoiceQuestion).answerOptions!);
                    } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                        this.shuffle((question as DragAndDropQuestion).dragItems);
                    } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                    } else {
                        console.log('Unknown question type: ' + question);
                    }
                }
            }, this);
        }
    }

    /**
     * Shuffles array in place.
     * @param {Array} items An array containing the items.
     */
    shuffle<T>(items: T[]) {
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
            this.submission.adjustedSubmissionDate = moment(this.submission.submissionDate)
                .subtract(this.timeDifference, 'seconds')
                .toDate();
            if (Math.abs(moment(this.submission.adjustedSubmissionDate).diff(moment(), 'seconds')) < 2) {
                this.justSaved = true;
                this.timeoutJustSaved();
            }
        }
    }

    /**
     * Callback function for handling quiz submission after saving submission to server
     * @param quizSubmission The quiz submission data from the server
     * @param error a potential error during save
     */
    onSaveSuccess(quizSubmission: QuizSubmission | null, error: string | null) {
        if (!quizSubmission || error) {
            alert('Saving Answers failed: ' + error);
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
        if (quizSubmission.submitted) {
            this.outstandingWebsocketResponses = 0;
            this.isSaving = false;
            this.unsavedChanges = false;
            this.isSubmitting = false;
            this.submission = quizSubmission;
            this.updateSubmissionTime();
            this.applySubmission();
        } else if (this.outstandingWebsocketResponses === 0) {
            this.isSaving = false;
            this.unsavedChanges = false;
            this.submission = quizSubmission;
            this.updateSubmissionTime();
            this.applySubmission();
        } else {
            this.outstandingWebsocketResponses--;
            if (this.outstandingWebsocketResponses === 0) {
                this.isSaving = false;
                this.unsavedChanges = false;
                if (quizSubmission) {
                    this.submission.submissionDate = quizSubmission.submissionDate;
                    this.updateSubmissionTime();
                }
            }
        }
    }

    /**
     * Checks if the student has interacted with each question of the quiz
     * for a Multiple Choice Questions it checks if an answer option was selected
     * for a Drag and Drop Questions it checks if at least one mapping has been made
     * for a Short Answer Questions it checks if at least one field has been clicked in
     * @return {boolean} true when student interacted with every question, false when not with every questions has an interaction
     */
    areAllQuestionsAnswered(): boolean {
        for (const question of this.quizExercise.quizQuestions) {
            if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                if (this.selectedAnswerOptions[question.id] >= 0) {
                    return false;
                }
            } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                if (this.dragAndDropMappings[question.id] >= 0) {
                    return false;
                }
            } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                if (this.shortAnswerSubmittedTexts[question.id] >= 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * This function is called when the user clicks the 'Submit' button
     */
    onSubmit() {
        const translationBasePath = 'artemisApp.quizExercise.';
        this.applySelection();
        let confirmSubmit = true;

        if (this.remainingTimeSeconds > 15 && !this.areAllQuestionsAnswered()) {
            const warningText = this.translateService.instant(translationBasePath + 'submissionWarning');
            confirmSubmit = window.confirm(warningText);
        }
        if (confirmSubmit) {
            this.isSubmitting = true;
            switch (this.mode) {
                case 'practice':
                    if (!this.submission.id) {
                        this.quizSubmissionService.submitForPractice(this.submission, this.quizId).subscribe(
                            (response: HttpResponse<Result>) => {
                                this.onSubmitPracticeOrPreviewSuccess(response.body!);
                            },
                            (response: HttpErrorResponse) => this.onSubmitError(response.message),
                        );
                    }
                    break;
                case 'preview':
                    if (!this.submission.id) {
                        this.quizSubmissionService.submitForPreview(this.submission, this.quizId).subscribe(
                            (response: HttpResponse<Result>) => {
                                this.onSubmitPracticeOrPreviewSuccess(response.body!);
                            },
                            (response: HttpErrorResponse) => this.onSubmitError(response.message),
                        );
                    }
                    break;
                case 'default':
                    if (this.disconnected || !this.submissionChannel) {
                        alert(
                            "Cannot Submit while disconnected. Don't worry, answers that were saved" +
                                'while you were still connected will be submitted automatically when the quiz ends.',
                        );
                        this.isSubmitting = false;
                        return;
                    }
                    // copy submission and send it through websocket with 'submitted = true'
                    const quizSubmission = new QuizSubmission();
                    quizSubmission.submittedAnswers = this.submission.submittedAnswers;
                    quizSubmission.submitted = true;
                    this.jhiWebsocketService.send(this.submissionChannel, quizSubmission);
                    break;
            }
        }
    }

    /**
     * Callback function for handling response after submitting for practice or preview
     * @param result
     */
    onSubmitPracticeOrPreviewSuccess(result: Result) {
        this.isSubmitting = false;
        this.submission = result.submission as QuizSubmission;
        // make sure the additional information (explanations, correct answers) is available
        const quizExercise = (result.participation! as StudentParticipation).exercise as QuizExercise;
        this.transferInformationToQuizExercise(quizExercise);
        this.applySubmission();
        this.showResult(result);
    }

    /**
     * Callback function for handling error when submitting
     * @param error
     */
    onSubmitError(error: string) {
        console.error(error);
        alert('Submitting was not possible. Please try again later. If your answers have been saved, you can also wait until the quiz has finished.');
        this.isSubmitting = false;
    }

    /**
     * By clicking on the bubble of the progress navigation towards the corresponding question of the quiz is triggered
     * @param questionIndex
     */
    navigateToQuestion(questionIndex: number): void {
        document.getElementById('question' + questionIndex)!.scrollIntoView({
            behavior: 'smooth',
        });
    }

    /**
     * Determines if the current device is a mobile device
     */
    isMobile(): boolean {
        return this.deviceService.isMobile();
    }
}
