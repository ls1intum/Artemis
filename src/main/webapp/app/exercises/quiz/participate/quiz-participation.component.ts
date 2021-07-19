import { Component, OnDestroy, OnInit, QueryList, ViewChildren } from '@angular/core';
import * as moment from 'moment';
import * as _ from 'lodash';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { Result } from 'app/entities/result.model';
import { MultipleChoiceQuestionComponent } from 'app/exercises/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { DragAndDropQuestionComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { ShortAnswerQuestionComponent } from 'app/exercises/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { TranslateService } from '@ngx-translate/core';
import * as smoothscroll from 'smoothscroll-polyfill';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { DeviceDetectorService } from 'ngx-device-detector';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ShortAnswerSubmittedAnswer } from 'app/entities/quiz/short-answer-submitted-answer.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { QuizParticipationService } from 'app/exercises/quiz/participate/quiz-participation.service';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { DragAndDropSubmittedAnswer } from 'app/entities/quiz/drag-and-drop-submitted-answer.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { MultipleChoiceSubmittedAnswer } from 'app/entities/quiz/multiple-choice-submitted-answer.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { ArtemisQuizService } from 'app/shared/quiz/quiz.service';
import * as Sentry from '@sentry/browser';
import { round } from 'app/shared/util/utils';
import { onError } from 'app/shared/util/global.utils';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';

@Component({
    selector: 'jhi-quiz',
    templateUrl: './quiz-participation.component.html',
    providers: [ParticipationService],
    styleUrls: ['./quiz-participation.component.scss'],
})
export class QuizParticipationComponent implements OnInit, OnDestroy {
    // make constants available to html for comparison
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;
    readonly ButtonSize = ButtonSize;
    readonly ButtonType = ButtonType;
    readonly round = round;

    @ViewChildren(MultipleChoiceQuestionComponent)
    mcQuestionComponents: QueryList<MultipleChoiceQuestionComponent>;

    @ViewChildren(DragAndDropQuestionComponent)
    dndQuestionComponents: QueryList<DragAndDropQuestionComponent>;

    @ViewChildren(ShortAnswerQuestionComponent)
    shortAnswerQuestionComponents: QueryList<ShortAnswerQuestionComponent>;

    private subscription: Subscription;
    private subscriptionData: Subscription;

    // Difference between server and client time
    timeDifference = 0;

    runningTimeouts = new Array<any>(); // actually the function type setTimeout(): (handler: any, timeout?: any, ...args: any[]): number

    isSubmitting = false;
    // isSaving = false;
    lastSavedTimeText = '';
    justSaved = false;
    waitingForQuizStart = false;
    refreshingQuiz = false;

    remainingTimeText = '?';
    remainingTimeSeconds = 0;
    timeUntilStart = '0';
    disconnected = false;
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
    courseId: number;
    interval: any;
    quizStarted = false;

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

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private quizExerciseService: QuizExerciseService,
        private participationService: ParticipationService,
        private participationWebsocketService: ParticipationWebsocketService,
        private route: ActivatedRoute,
        private jhiAlertService: JhiAlertService,
        private quizParticipationService: QuizParticipationService,
        private translateService: TranslateService,
        private deviceService: DeviceDetectorService,
        private quizService: ArtemisQuizService,
    ) {
        smoothscroll.polyfill();
    }

    ngOnInit() {
        // set correct mode
        this.subscriptionData = this.route.data.subscribe((data) => {
            this.mode = data.mode;
            this.subscription = this.route.params.subscribe((params) => {
                this.quizId = Number(params['exerciseId']);
                this.courseId = Number(params['courseId']);
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
                    case 'live':
                        this.initLiveMode();
                        break;
                }
            });
        });
        // update displayed times in UI regularly
        this.interval = setInterval(() => {
            this.updateDisplayedTimes();
        }, UI_RELOAD_TIME);
    }

    ngOnDestroy() {
        clearInterval(this.interval);
        /**
         * unsubscribe from all subscribed websocket channels when page is closed
         */
        this.runningTimeouts.forEach((timeout) => {
            clearTimeout(timeout);
        });

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
        if (this.subscription) {
            this.subscription.unsubscribe();
        }
        if (this.subscriptionData) {
            this.subscriptionData.unsubscribe();
        }
    }

    /**
     * loads latest submission from server and sets up socket connection
     */
    initLiveMode() {
        // listen to connect / disconnect events
        this.onConnected = () => {
            if (this.disconnected) {
                // if the disconnect happened during the live quiz and there are unsaved changes, we trigger a selection changed event to save the submission on the server
                if (this.unsavedChanges && this.sendWebsocket) {
                    this.onSelectionChanged();
                }
                // if the quiz was not yet started, we might have missed the quiz start => refresh
                if (this.quizExercise && !this.quizExercise.started) {
                    this.refreshQuiz(true);
                } else if (this.quizExercise && this.quizExercise.adjustedDueDate && this.quizExercise.adjustedDueDate.isBefore(moment())) {
                    // if the quiz has ended, we might have missed to load the results => refresh
                    this.refreshQuiz(true);
                }
            }
            this.disconnected = false;
        };
        this.jhiWebsocketService.bind('connect', () => {
            this.onConnected();
        });
        this.onDisconnected = () => {
            this.disconnected = true;
        };
        this.jhiWebsocketService.bind('disconnect', () => {
            this.onDisconnected();
        });

        this.subscribeToWebsocketChannels();

        // load the quiz (and existing submission if quiz has started)
        this.participationService.findParticipation(this.quizId).subscribe(
            (response: HttpResponse<StudentParticipation>) => {
                this.updateParticipationFromServer(response.body!);
            },
            (error: HttpErrorResponse) => onError(this.jhiAlertService, error),
        );
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
            (error: HttpErrorResponse) => onError(this.jhiAlertService, error),
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
            (error: HttpErrorResponse) => onError(this.jhiAlertService, error),
        );
    }

    initShowSolution() {
        this.quizExerciseService.find(this.quizId).subscribe(
            (res: HttpResponse<QuizExercise>) => {
                this.quizExercise = res.body!;
                this.initQuiz();
                this.showingResult = true;
            },
            (error: HttpErrorResponse) => onError(this.jhiAlertService, error),
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
        this.quizService.randomizeOrder(this.quizExercise);

        // init empty submission
        this.submission = new QuizSubmission();

        // adjust end date
        this.quizExercise.adjustedDueDate = moment().add(this.quizExercise.duration, 'seconds');

        // auto submit when time is up
        this.runningTimeouts.push(
            setTimeout(() => {
                this.onSubmit();
            }, quizExercise.duration! * 1000),
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
                (payload) => {
                    if (payload.error) {
                        this.onSaveError(payload.error);
                    }
                },
                (error) => {
                    this.onSaveError(error);
                },
            );

            // save answers (submissions) through websocket
            this.sendWebsocket = (submission: QuizSubmission) => {
                this.jhiWebsocketService.send(this.submissionChannel, submission);
            };
        }

        if (!this.participationChannel) {
            this.participationChannel = '/user/topic/exercise/' + this.quizId + '/participation';
            // TODO: subscribe for new results instead if this is what we are actually interested in
            // participation channel => react to new results
            this.jhiWebsocketService.subscribe(this.participationChannel);
            this.jhiWebsocketService.receive(this.participationChannel).subscribe((changedParticipation: StudentParticipation) => {
                if (changedParticipation && this.quizExercise && changedParticipation.exercise!.id === this.quizExercise.id) {
                    if (this.waitingForQuizStart) {
                        // only apply completely if quiz hasn't started to prevent jumping ui during participation
                        this.updateParticipationFromServer(changedParticipation);
                    } else {
                        // update quizExercise and results / submission
                        this.showQuizResultAfterQuizEnd(changedParticipation);
                    }
                }
            });
        }

        if (!this.quizExerciseChannel) {
            this.quizExerciseChannel = '/topic/courses/' + this.courseId + '/quizExercises';

            // quizExercise channel => react to changes made to quizExercise (e.g. start date)
            this.jhiWebsocketService.subscribe(this.quizExerciseChannel);
            this.jhiWebsocketService.receive(this.quizExerciseChannel).subscribe(
                (quiz) => {
                    if (this.waitingForQuizStart && this.quizId === quiz.id) {
                        this.applyQuizFull(quiz);
                    }
                },
                () => {},
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
                // Check if websocket has updated the quiz exercise and check that following block is only executed once
                if (!this.quizExercise.started && !this.quizStarted) {
                    this.quizStarted = true;
                    // Refresh quiz after 5 seconds when client did not receive websocket message to start the quiz
                    setTimeout(() => {
                        // Check again if websocket has updated the quiz exercise within the 5 seconds
                        if (!this.quizExercise.started) {
                            this.refreshQuiz(true);
                        }
                    }, 5000);
                }
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
            ? this.quizExercise.quizQuestions.reduce((score, question) => {
                  return score + question.points!;
              }, 0)
            : 0;

        // prepare selection arrays for each question
        this.selectedAnswerOptions = new Map<number, AnswerOption[]>();
        this.dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
        this.shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

        if (this.quizExercise.quizQuestions) {
            this.quizExercise.quizQuestions.forEach((question) => {
                if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                    // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.selectedAnswerOptions.set(question.id!, []);
                } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                    // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.dragAndDropMappings.set(question.id!, []);
                } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                    // add the array of submitted texts to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.shortAnswerSubmittedTexts.set(question.id!, []);
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
            this.quizExercise.quizQuestions.forEach((question) => {
                // find the submitted answer that belongs to this question, only when submitted answers already exist
                const submittedAnswer = this.submission.submittedAnswers?.find((answer) => {
                    return answer.quizQuestion!.id === question.id;
                });

                if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                    // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.selectedAnswerOptions.set(question.id!, (submittedAnswer as MultipleChoiceSubmittedAnswer)?.selectedOptions || []);
                } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                    // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.dragAndDropMappings.set(question.id!, (submittedAnswer as DragAndDropSubmittedAnswer)?.mappings || []);
                } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                    // add the array of submitted texts to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.shortAnswerSubmittedTexts.set(question.id!, (submittedAnswer as ShortAnswerSubmittedAnswer)?.submittedTexts || []);
                } else {
                    console.error('Unknown question type: ' + question);
                }
            }, this);
        }
    }

    /**
     * updates the model according to UI state (reverse of applySubmission):
     * Creates the submission from the user's selection
     * this needs to be done when we want to send the submission either for saving (through websocket) or for submitting (through REST call)
     */
    applySelection() {
        // convert the selection dictionary (key: questionID, value: Array of selected answerOptions / mappings)
        // into an array of submittedAnswer objects and save it as the submittedAnswers of the submission
        this.submission.submittedAnswers = [];

        // for multiple-choice questions
        this.selectedAnswerOptions.forEach((answerOptions, questionId) => {
            // find the question object for the given question id
            const question = this.quizExercise.quizQuestions?.find((selectedQuestion) => {
                return selectedQuestion.id === questionId;
            });
            if (!question) {
                console.error('question not found for ID: ' + questionId);
                return;
            }
            // generate the submittedAnswer object
            const mcSubmittedAnswer = new MultipleChoiceSubmittedAnswer();
            mcSubmittedAnswer.quizQuestion = question;
            mcSubmittedAnswer.selectedOptions = answerOptions;
            this.submission.submittedAnswers!.push(mcSubmittedAnswer);
        }, this);

        // for drag-and-drop questions
        this.dragAndDropMappings.forEach((mappings, questionId) => {
            // find the question object for the given question id
            const question = this.quizExercise.quizQuestions?.find((localQuestion) => {
                return localQuestion.id === questionId;
            });
            if (!question) {
                console.error('question not found for ID: ' + questionId);
                return;
            }
            // generate the submittedAnswer object
            const dndSubmittedAnswer = new DragAndDropSubmittedAnswer();
            dndSubmittedAnswer.quizQuestion = question;
            dndSubmittedAnswer.mappings = mappings;
            this.submission.submittedAnswers!.push(dndSubmittedAnswer);
        }, this);
        // for short-answer questions
        this.shortAnswerSubmittedTexts.forEach((submittedTexts, questionId) => {
            // find the question object for the given question id
            const question = this.quizExercise.quizQuestions?.find((localQuestion) => {
                return localQuestion.id === questionId;
            });
            if (!question) {
                console.error('question not found for ID: ' + questionId);
                return;
            }
            // generate the submittedAnswer object
            const shortAnswerSubmittedAnswer = new ShortAnswerSubmittedAnswer();
            shortAnswerSubmittedAnswer.quizQuestion = question;
            shortAnswerSubmittedAnswer.submittedTexts = submittedTexts;
            this.submission.submittedAnswers!.push(shortAnswerSubmittedAnswer);
        }, this);
    }

    /**
     * Apply the data of the participation, replacing all old data
     */
    updateParticipationFromServer(participation: StudentParticipation) {
        if (participation) {
            this.applyQuizFull(participation.exercise as QuizExercise);
        }

        // apply submission if it exists
        if (participation?.results?.length) {
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
                this.quizService.randomizeOrder(this.quizExercise);
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
    showQuizResultAfterQuizEnd(participation: StudentParticipation) {
        const quizExercise = participation.exercise as QuizExercise;
        if (participation.results?.length && participation.results[0].resultString && quizExercise.ended) {
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
     * Transfer additional information (explanations, correct answers) from the given full quiz exercise to quizExercise.
     * This method is typically invoked after the quiz has ended and makes sure that the (random) order of the quiz
     * questions and answer options for the particular user is respected
     *
     * @param fullQuizExerciseFromServer {object} the quizExercise containing additional information
     */
    transferInformationToQuizExercise(fullQuizExerciseFromServer: QuizExercise) {
        this.quizExercise.quizQuestions!.forEach((clientQuestion) => {
            // find updated question
            const fullQuestionFromServer = fullQuizExerciseFromServer.quizQuestions?.find((fullQuestion) => {
                return clientQuestion.id === fullQuestion.id;
            });
            if (fullQuestionFromServer) {
                clientQuestion.explanation = fullQuestionFromServer.explanation;

                if (clientQuestion.type === QuizQuestionType.MULTIPLE_CHOICE) {
                    const mcClientQuestion = clientQuestion as MultipleChoiceQuestion;
                    const mcFullQuestionFromServer = fullQuestionFromServer as MultipleChoiceQuestion;

                    const answerOptions = mcClientQuestion.answerOptions!;
                    answerOptions.forEach((clientAnswerOption) => {
                        // find updated answerOption
                        const fullAnswerOptionFromServer = mcFullQuestionFromServer.answerOptions!.find((option) => {
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
                    Sentry.captureException(new Error('Unknown question type: ' + clientQuestion));
                }
            }
        }, this);

        // make sure that a possible explanation is updated correctly in all sub components
        this.mcQuestionComponents.forEach((mcQuestionComponent) => {
            mcQuestionComponent.watchCollection();
        });
        this.dndQuestionComponents.forEach((dndQuestionComponent) => {
            dndQuestionComponent.watchCollection();
        });
        this.shortAnswerQuestionComponents.forEach((shortAnswerQuestionComponent) => {
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
            this.userScore = this.submission.scoreInPoints ? round(this.submission.scoreInPoints, 2) : 0;

            // create dictionary with scores for each question
            this.questionScores = {};
            this.submission.submittedAnswers!.forEach((submittedAnswer) => {
                // limit decimal places to 2
                this.questionScores[submittedAnswer.quizQuestion!.id!] = round(submittedAnswer.scoreInPoints!, 2);
            }, this);
        }
    }

    /**
     * Callback method to be triggered when the user changes any of the answers in the quiz (in sub components based on the question type)
     */
    onSelectionChanged() {
        this.applySelection();
        if (this.sendWebsocket) {
            if (!this.disconnected) {
                // this.isSaving = true;
                this.submission.submissionDate = moment().add(this.timeDifference, 'seconds');
                this.sendWebsocket(this.submission);
                this.unsavedChanges = false;
                this.updateSubmissionTime();
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
     * Callback function for handling quiz submission after saving submission to server
     * @param error a potential error during save
     */
    onSaveError(error: string) {
        if (error) {
            const errorMessage = 'Saving answers failed: ' + error;
            // TODO: this is a workaround to avoid translation not found issues. Provide proper translations
            const jhiAlert = this.jhiAlertService.error(errorMessage);
            jhiAlert.msg = errorMessage;
            this.unsavedChanges = true;
            this.isSubmitting = false;
        }
    }

    /**
     * Checks if the student has interacted with each question of the quiz:
     * - for a Multiple Choice Questions it checks if an answer option was selected
     * - for a Drag and Drop Questions it checks if at least one mapping has been made
     * - for a Short Answer Questions it checks if at least one field has been clicked in
     * @return {boolean} true when student interacted with every question, false when not with every questions has an interaction
     */
    areAllQuestionsAnswered(): boolean {
        if (!this.quizExercise.quizQuestions) {
            return true;
        }

        for (const question of this.quizExercise.quizQuestions) {
            if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                const options = this.selectedAnswerOptions.get(question.id!);
                if (options && options.length === 0) {
                    return false;
                }
            } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                const mappings = this.dragAndDropMappings.get(question.id!);
                if (mappings && mappings.length === 0) {
                    return false;
                }
            } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                const submittedTexts = this.shortAnswerSubmittedTexts.get(question.id!);
                if (submittedTexts && submittedTexts.length === 0) {
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
                        this.quizParticipationService.submitForPractice(this.submission, this.quizId).subscribe(
                            (response: HttpResponse<Result>) => {
                                this.onSubmitPracticeOrPreviewSuccess(response.body!);
                            },
                            (error: HttpErrorResponse) => this.onSubmitError(error),
                        );
                    }
                    break;
                case 'preview':
                    if (!this.submission.id) {
                        this.quizParticipationService.submitForPreview(this.submission, this.quizId).subscribe(
                            (response: HttpResponse<Result>) => {
                                this.onSubmitPracticeOrPreviewSuccess(response.body!);
                            },
                            (error: HttpErrorResponse) => this.onSubmitError(error),
                        );
                    }
                    break;
                case 'live':
                    // copy submission and send it through websocket with 'submitted = true'
                    const quizSubmission = new QuizSubmission();
                    quizSubmission.submittedAnswers = this.submission.submittedAnswers;
                    this.quizParticipationService.submitForLiveMode(quizSubmission, this.quizId).subscribe(
                        (response: HttpResponse<QuizSubmission>) => {
                            this.submission = response.body!;
                            this.isSubmitting = false;
                            this.updateSubmissionTime();
                            this.applySubmission();
                        },
                        (error: HttpErrorResponse) => this.onSubmitError(error),
                    );
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
    onSubmitError(error: HttpErrorResponse) {
        const errorMessage = 'Submitting the quiz was not possible. ' + error.headers?.get('X-artemisApp-message') || error.message;
        // TODO: this is a workaround to avoid translation not found issues. Provide proper translations
        const jhiAlert = this.jhiAlertService.error(errorMessage);
        jhiAlert.msg = errorMessage;
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

    /**
     * Refresh quiz
     */
    refreshQuiz(refresh = false) {
        this.refreshingQuiz = refresh;
        this.quizExerciseService.findForStudent(this.quizId).subscribe(
            (res: HttpResponse<QuizExercise>) => {
                const quizExercise = res.body!;
                if (quizExercise.started) {
                    this.quizExercise = quizExercise;
                    this.initLiveMode();
                }
                setTimeout(() => (this.refreshingQuiz = false), 500); // ensure min animation duration
            },
            () => {
                // error case
                setTimeout(() => (this.refreshingQuiz = false), 500); // ensure min animation duration
            },
        );
    }
}
