import { Component, OnDestroy, OnInit, QueryList, ViewChildren } from '@angular/core';
import dayjs from 'dayjs/esm';
import isMobile from 'ismobilejs-es5';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { Result } from 'app/entities/result.model';
import { MultipleChoiceQuestionComponent } from 'app/exercises/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { DragAndDropQuestionComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { ShortAnswerQuestionComponent } from 'app/exercises/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { TranslateService } from '@ngx-translate/core';
import * as smoothscroll from 'smoothscroll-polyfill';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ShortAnswerSubmittedAnswer } from 'app/entities/quiz/short-answer-submitted-answer.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { QuizParticipationService } from 'app/exercises/quiz/participate/quiz-participation.service';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { QuizBatch, QuizExercise, QuizMode } from 'app/entities/quiz/quiz-exercise.model';
import { DragAndDropSubmittedAnswer } from 'app/entities/quiz/drag-and-drop-submitted-answer.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { MultipleChoiceSubmittedAnswer } from 'app/entities/quiz/multiple-choice-submitted-answer.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { ArtemisQuizService } from 'app/shared/quiz/quiz.service';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { onError } from 'app/shared/util/global.utils';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { debounce } from 'lodash-es';
import { captureException } from '@sentry/browser';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { faCircleNotch, faSync } from '@fortawesome/free-solid-svg-icons';
import { ArtemisServerDateService } from 'app/shared/server-date.service';

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
    readonly QuizMode = QuizMode;
    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly getCourseFromExercise = getCourseFromExercise;

    @ViewChildren(MultipleChoiceQuestionComponent)
    mcQuestionComponents: QueryList<MultipleChoiceQuestionComponent>;

    @ViewChildren(DragAndDropQuestionComponent)
    dndQuestionComponents: QueryList<DragAndDropQuestionComponent>;

    @ViewChildren(ShortAnswerQuestionComponent)
    shortAnswerQuestionComponents: QueryList<ShortAnswerQuestionComponent>;

    private subscription: Subscription;
    private subscriptionData: Subscription;

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

    sendWebsocket?: (submission: QuizSubmission) => void;
    showingResult = false;
    userScore: number;

    mode: string;
    submission = new QuizSubmission();
    quizExercise: QuizExercise;
    quizBatch?: QuizBatch;
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
    startDate: dayjs.Dayjs | undefined;
    endDate: dayjs.Dayjs | undefined;
    password = '';
    previousRunning = false;
    isMobile = false;

    /**
     * Websocket channels
     */
    submissionChannel: string;
    participationChannel: string;
    quizExerciseChannel: string;
    quizBatchChannel: string;
    websocketSubscription?: Subscription;

    /**
     * debounced function to reset 'justSubmitted', so that time since last submission is displayed again when no submission has been made for at least 2 seconds
     * @type {Function}
     */
    timeoutJustSaved = debounce(() => {
        this.justSaved = false;
    }, 2000);

    // Icons
    faSync = faSync;
    faCircleNotch = faCircleNotch;

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private quizExerciseService: QuizExerciseService,
        private participationService: ParticipationService,
        private participationWebsocketService: ParticipationWebsocketService,
        private route: ActivatedRoute,
        private alertService: AlertService,
        private quizParticipationService: QuizParticipationService,
        private translateService: TranslateService,
        private quizService: ArtemisQuizService,
        private serverDateService: ArtemisServerDateService,
    ) {
        smoothscroll.polyfill();
    }

    ngOnInit() {
        this.isMobile = isMobile(window.navigator.userAgent).any;
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
            this.checkForQuizEnd();
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
        this.websocketSubscription?.unsubscribe();
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
        this.websocketSubscription = this.jhiWebsocketService.connectionState.subscribe((status) => {
            if (status.connected && this.disconnected) {
                // if the disconnect happened during the live quiz and there are unsaved changes, we trigger a selection changed event to save the submission on the server
                if (this.unsavedChanges && this.sendWebsocket) {
                    this.onSelectionChanged();
                }
                // if the quiz was not yet started, we might have missed the quiz start => refresh
                if (this.quizBatch && !this.quizBatch.started) {
                    this.refreshQuiz(true);
                } else if (this.quizBatch && this.endDate && this.endDate.isBefore(this.serverDateService.now())) {
                    // if the quiz has ended, we might have missed to load the results => refresh
                    this.refreshQuiz(true);
                }
            }
            this.disconnected = !status.connected;
        });

        this.subscribeToWebsocketChannels();

        // load the quiz (and existing submission if quiz has started)
        this.participationService.findParticipationForCurrentUser(this.quizId).subscribe({
            next: (response: HttpResponse<StudentParticipation>) => {
                this.updateParticipationFromServer(response.body!);
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * loads quizExercise and starts practice mode
     */
    initPracticeMode() {
        this.quizExerciseService.findForStudent(this.quizId).subscribe({
            next: (res: HttpResponse<QuizExercise>) => {
                if (res.body && res.body.isOpenForPractice) {
                    this.startQuizPreviewOrPractice(res.body);
                } else {
                    alert('Error: This quiz is not open for practice!');
                }
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * loads quiz exercise and starts preview mode
     */
    initPreview() {
        this.quizExerciseService.find(this.quizId).subscribe({
            next: (res: HttpResponse<QuizExercise>) => {
                this.startQuizPreviewOrPractice(res.body!);
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    initShowSolution() {
        this.quizExerciseService.find(this.quizId).subscribe({
            next: (res: HttpResponse<QuizExercise>) => {
                this.quizExercise = res.body!;
                this.initQuiz();
                this.showingResult = true;
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
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
        this.endDate = dayjs().add(this.quizExercise.duration!, 'seconds');

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
            this.jhiWebsocketService.receive('/user' + this.submissionChannel).subscribe({
                next: (payload) => {
                    if (payload.error) {
                        this.onSaveError(payload.error);
                    }
                },
                error: (error) => {
                    this.onSaveError(error);
                },
            });

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
            this.jhiWebsocketService.receive(this.quizExerciseChannel).subscribe((quiz) => {
                if (this.waitingForQuizStart && this.quizId === quiz.id) {
                    this.applyQuizFull(quiz);
                }
            });
        }

        if (this.quizBatch && !this.quizBatch.started) {
            const batchChannel = this.quizExerciseChannel + '/' + this.quizBatch.id;
            if (this.quizBatchChannel !== batchChannel) {
                this.quizBatchChannel = batchChannel;
                this.jhiWebsocketService.subscribe(this.quizBatchChannel);
                this.jhiWebsocketService.receive(this.quizBatchChannel).subscribe((quiz) => {
                    this.applyQuizFull(quiz);
                });
            }
        }
    }

    /**
     * updates all displayed (relative) times in the UI
     */
    updateDisplayedTimes() {
        const translationBasePath = 'showStatistic.';
        // update remaining time
        if (this.endDate) {
            const endDate = this.endDate;
            if (endDate.isAfter(this.serverDateService.now())) {
                // quiz is still running => calculate remaining seconds and generate text based on that
                this.remainingTimeSeconds = endDate.diff(this.serverDateService.now(), 'seconds');
                this.remainingTimeText = this.relativeTimeText(this.remainingTimeSeconds);
            } else {
                // quiz is over => set remaining seconds to negative, to deactivate 'Submit' button
                this.remainingTimeSeconds = -1;
                this.remainingTimeText = this.translateService.instant(translationBasePath + 'quizHasEnded');
            }
        } else {
            // remaining time is unknown => Set remaining seconds to 0, to keep 'Submit' button enabled
            this.remainingTimeSeconds = 0;
            this.remainingTimeText = '?';
        }

        // update submission time
        if (this.submission && this.submission.submissionDate) {
            // exact value is not important => use default relative time from dayjs for better readability and less distraction
            this.lastSavedTimeText = dayjs(this.submission.submissionDate).fromNow();
        }

        // update time until start
        if (this.quizBatch && this.startDate) {
            if (this.startDate.isAfter(this.serverDateService.now())) {
                this.timeUntilStart = this.relativeTimeText(this.startDate.diff(this.serverDateService.now(), 'seconds'));
            } else {
                this.timeUntilStart = this.translateService.instant(translationBasePath + 'now');
                // Check if websocket has updated the quiz exercise and check that following block is only executed once
                if (!this.quizBatch.started && !this.quizStarted) {
                    this.quizStarted = true;
                    if (this.quizExercise.quizMode === QuizMode.INDIVIDUAL) {
                        // there is not websocket notification for INDIVIDUAL mode so just load the quiz
                        this.refreshQuiz(true);
                    } else {
                        // Refresh quiz after 5 seconds when client did not receive websocket message to start the quiz
                        setTimeout(() => {
                            // Check again if websocket has updated the quiz exercise within the 5 seconds
                            if (!this.quizBatch || !this.quizBatch.started) {
                                this.refreshQuiz(true);
                            }
                        }, 5000);
                    }
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

    checkForQuizEnd() {
        const running = this.mode === 'live' && !!this.quizBatch && this.remainingTimeSeconds >= 0 && this.quizExercise?.quizMode !== QuizMode.SYNCHRONIZED;
        if (!running && this.previousRunning) {
            if (!this.submission.submitted && this.submission.submissionDate) {
                this.alertService.success('artemisApp.quizExercise.submitSuccess');
            }
        }
        this.previousRunning = running;
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

            if (participation.results[0].score !== undefined && this.quizExercise.quizEnded) {
                // quiz has ended and results are available
                this.showResult(participation.results[0]);
            }
        } else {
            this.submission = new QuizSubmission();
            this.initQuiz();
        }
    }

    /**
     * apply the data of the quiz, replacing all old data and enabling reconnect if necessary
     * @param quizExercise
     */
    applyQuizFull(quizExercise: QuizExercise) {
        this.quizExercise = quizExercise;
        this.initQuiz();

        this.quizBatch = this.quizExercise.quizBatches?.[0];
        if (this.quizExercise.quizEnded) {
            // quiz is done
            this.waitingForQuizStart = false;
        } else if (!this.quizBatch || !this.quizBatch.started) {
            // quiz hasn't started yet
            this.waitingForQuizStart = true;

            // enable automatic websocket reconnect
            this.jhiWebsocketService.enableReconnect();

            if (this.quizBatch && this.quizBatch.startTime) {
                // synchronize time with server
                this.startDate = dayjs(this.quizBatch.startTime ?? this.serverDateService.now());
            }
        } else {
            // quiz has started
            this.waitingForQuizStart = false;

            // update timeDifference
            this.startDate = dayjs(this.quizBatch.startTime ?? this.serverDateService.now());
            this.endDate = this.startDate.add(this.quizExercise.duration!, 'seconds');

            // check if quiz hasn't ended
            if (!this.quizBatch.ended) {
                // enable automatic websocket reconnect
                this.jhiWebsocketService.enableReconnect();

                // apply randomized order where necessary
                this.quizService.randomizeOrder(this.quizExercise);
            }
        }
    }

    /*
     * This method only handles the update of the quiz after the quiz has ended
     */
    showQuizResultAfterQuizEnd(participation: StudentParticipation) {
        const quizExercise = participation.exercise as QuizExercise;
        if (participation.results?.first()?.submission !== undefined && quizExercise.quizEnded) {
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
                    captureException(new Error('Unknown question type: ' + clientQuestion));
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

            const course = this.quizExercise.course || this.quizExercise?.exerciseGroup?.exam?.course;

            // assign user score (limit decimal places to 2)
            this.userScore = this.submission.scoreInPoints ? roundValueSpecifiedByCourseSettings(this.submission.scoreInPoints, course) : 0;

            // create dictionary with scores for each question
            this.questionScores = {};
            this.submission.submittedAnswers?.forEach((submittedAnswer) => {
                // limit decimal places
                this.questionScores[submittedAnswer.quizQuestion!.id!] = roundValueSpecifiedByCourseSettings(submittedAnswer.scoreInPoints!, course);
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
                this.submission.submissionDate = this.serverDateService.now();
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
            if (Math.abs(dayjs(this.submission.submissionDate).diff(this.serverDateService.now(), 'seconds')) < 2) {
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
            this.alertService.addAlert({
                type: AlertType.DANGER,
                message: errorMessage,
                disableTranslation: true,
            });
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
                        this.quizParticipationService.submitForPractice(this.submission, this.quizId).subscribe({
                            next: (response: HttpResponse<Result>) => {
                                this.onSubmitPracticeOrPreviewSuccess(response.body!);
                            },
                            error: (error: HttpErrorResponse) => this.onSubmitError(error),
                        });
                    }
                    break;
                case 'preview':
                    if (!this.submission.id) {
                        this.quizParticipationService.submitForPreview(this.submission, this.quizId).subscribe({
                            next: (response: HttpResponse<Result>) => {
                                this.onSubmitPracticeOrPreviewSuccess(response.body!);
                            },
                            error: (error: HttpErrorResponse) => this.onSubmitError(error),
                        });
                    }
                    break;
                case 'live':
                    // copy submission and send it through websocket with 'submitted = true'
                    const quizSubmission = new QuizSubmission();
                    quizSubmission.submittedAnswers = this.submission.submittedAnswers;
                    this.quizParticipationService.submitForLiveMode(quizSubmission, this.quizId).subscribe({
                        next: (response: HttpResponse<QuizSubmission>) => {
                            this.submission = response.body!;
                            this.isSubmitting = false;
                            this.updateSubmissionTime();
                            this.applySubmission();
                            if (this.quizExercise.quizMode !== QuizMode.SYNCHRONIZED) {
                                this.alertService.success('artemisApp.quizExercise.submitSuccess');
                            }
                        },
                        error: (error: HttpErrorResponse) => this.onSubmitError(error),
                    });
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
        this.alertService.addAlert({
            type: AlertType.DANGER,
            message: errorMessage,
            disableTranslation: true,
        });
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
     * Refresh quiz
     */
    refreshQuiz(refresh = false) {
        this.refreshingQuiz = refresh;
        this.quizExerciseService.findForStudent(this.quizId).subscribe({
            next: (res: HttpResponse<QuizExercise>) => {
                const quizExercise = res.body!;
                if (quizExercise.quizStarted) {
                    if (quizExercise.quizEnded) {
                        this.waitingForQuizStart = false;
                        this.endDate = dayjs();
                    }
                    this.quizExercise = quizExercise;
                    this.initQuiz();
                    this.initLiveMode();
                }
                setTimeout(() => (this.refreshingQuiz = false), 500); // ensure min animation duration
            },
            error: () => {
                setTimeout(() => (this.refreshingQuiz = false), 500); // ensure min animation duration
            },
        });
    }

    joinBatch() {
        this.quizExerciseService.join(this.quizId, this.password).subscribe({
            next: (res: HttpResponse<QuizBatch>) => {
                if (res.body) {
                    this.quizBatch = res.body;
                    if (this.quizBatch?.started) {
                        this.refreshQuiz();
                    } else {
                        this.subscribeToWebsocketChannels();
                    }
                }
            },
            error: (error: HttpErrorResponse) => {
                const errorMessage = 'Joining the quiz was not possible: ' + error.headers?.get('X-artemisApp-message') || error.message;
                this.alertService.addAlert({
                    type: AlertType.DANGER,
                    message: errorMessage,
                    disableTranslation: true,
                });
            },
        });
    }
}
