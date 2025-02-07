import { HttpErrorResponse } from '@angular/common/http';
import { Component, Input, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Patch, Selection, UMLDiagramType, UMLElementType, UMLModel, UMLRelationshipType } from '@ls1intum/apollon';
import { WebsocketService } from 'app/core/websocket/websocket.service';
import { ComplaintType } from 'app/entities/complaint.model';
import { Feedback, buildFeedbackTextForReview, checkSubsequentFeedbackInAssessment } from 'app/entities/feedback.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { getFirstResultWithComplaint, getLatestSubmissionResult } from 'app/entities/submission.model';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { HeaderParticipationPageComponent } from 'app/exercises/shared/exercise-headers/header-participation-page.component';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercises/shared/exercise/exercise.utils';
import { addParticipationToResult, getUnreferencedFeedback } from 'app/exercises/shared/result/result.utils';
import { AccountService } from 'app/core/auth/account.service';
import { TeamSubmissionSyncComponent } from 'app/exercises/shared/team-submission-sync/team-submission-sync.component';
import { TeamParticipateInfoBoxComponent } from 'app/exercises/shared/team/team-participate/team-participate-info-box.component';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { modelingTour } from 'app/guided-tour/tours/modeling-tour';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { ButtonType } from 'app/shared/components/button.component';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL, AUTOSAVE_TEAM_EXERCISE_INTERVAL } from 'app/shared/constants/exercise-exam-constants';
import { faTimeline } from '@fortawesome/free-solid-svg-icons';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { stringifyIgnoringFields } from 'app/shared/util/utils';
import { Subject, Subscription, TeardownLogic } from 'rxjs';
import { omit } from 'lodash-es';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/core/util/alert.service';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { AssessmentNamesForModelId, getNamesForAssessments } from '../assess/modeling-assessment.util';
import { faExclamationTriangle, faGripLines } from '@fortawesome/free-solid-svg-icons';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { SubmissionPatch } from 'app/entities/submission-patch.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { catchError, filter, skip, switchMap, tap } from 'rxjs/operators';
import { onError } from 'app/shared/util/global.utils';
import { of } from 'rxjs';
import { ButtonComponent } from 'app/shared/components/button.component';
import { RequestFeedbackButtonComponent } from 'app/overview/exercise-details/request-feedback-button/request-feedback-button.component';
import { ResultHistoryComponent } from 'app/overview/result-history/result-history.component';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { FullscreenComponent } from 'app/shared/fullscreen/fullscreen.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ModelingAssessmentComponent } from '../assess/modeling-assessment.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DecimalPipe, NgClass } from '@angular/common';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AdditionalFeedbackComponent } from 'app/shared/additional-feedback/additional-feedback.component';
import { ComplaintsStudentViewComponent } from 'app/complaints/complaints-for-students/complaints-student-view.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { captureException } from '@sentry/angular';
import { RatingComponent } from 'app/exercises/shared/rating/rating.component';

@Component({
    selector: 'jhi-modeling-submission',
    templateUrl: './modeling-submission.component.html',
    styleUrls: ['./modeling-submission.component.scss'],
    imports: [
        HeaderParticipationPageComponent,
        ButtonComponent,
        RouterLink,
        RequestFeedbackButtonComponent,
        ResultHistoryComponent,
        ResizeableContainerComponent,
        TeamParticipateInfoBoxComponent,
        FullscreenComponent,
        ModelingEditorComponent,
        FaIconComponent,
        TeamSubmissionSyncComponent,
        ModelingAssessmentComponent,
        TranslateDirective,
        NgClass,
        NgbTooltip,
        AdditionalFeedbackComponent,
        RatingComponent,
        ComplaintsStudentViewComponent,
        DecimalPipe,
        ArtemisTranslatePipe,
        HtmlForMarkdownPipe,
    ],
})
export class ModelingSubmissionComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    private websocketService = inject(WebsocketService);
    private modelingSubmissionService = inject(ModelingSubmissionService);
    private modelingAssessmentService = inject(ModelingAssessmentService);
    private alertService = inject(AlertService);
    private route = inject(ActivatedRoute);
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private guidedTourService = inject(GuidedTourService);
    private accountService = inject(AccountService);

    readonly addParticipationToResult = addParticipationToResult;
    readonly buildFeedbackTextForReview = buildFeedbackTextForReview;
    readonly ButtonType = ButtonType;

    @ViewChild(ModelingEditorComponent, { static: false }) modelingEditor: ModelingEditorComponent;

    @Input() participationId?: number;
    @Input() inputExercise?: ModelingExercise;
    @Input() inputSubmission?: ModelingSubmission;
    @Input() inputParticipation?: StudentParticipation;

    @Input() isExamSummary = false;
    @Input() displayHeader = true;
    @Input() isPrinting = false;
    @Input() expandProblemStatement = false;

    private subscription: Subscription;
    private manualResultUpdateListener: Subscription;
    private athenaResultUpdateListener: Subscription;

    participation: StudentParticipation;
    isOwnerOfParticipation: boolean;

    modelingExercise: ModelingExercise;
    modelingParticipationHeader: StudentParticipation;
    modelingExerciseHeader: ModelingExercise;
    course?: Course;
    result?: Result;
    resultWithComplaint?: Result;

    selectedEntities: string[];
    selectedRelationships: string[];

    submission: ModelingSubmission;
    submissionId: number | undefined;
    sortedSubmissionHistory: ModelingSubmission[];
    sortedResultHistory: Result[];

    assessmentResult?: Result;
    assessmentsNames: AssessmentNamesForModelId = {};
    totalScore: number;

    umlModel: UMLModel; // input model for Apollon
    hasElements = false; // indicates if the current model has at least one element
    isSaving = false;
    isChanged: boolean;
    retryStarted = false;
    autoSaveInterval: number;
    autoSaveTimer = 0;

    explanation: string; // current explanation on text editor

    automaticSubmissionWebsocketChannel: string;

    // indicates if the assessment due date is in the past. the assessment will not be loaded and displayed to the student if it is not.
    isAfterAssessmentDueDate: boolean;
    isLoading = true;
    isLate: boolean; // indicates if the submission is late
    isGeneratingFeedback: boolean;
    ComplaintType = ComplaintType;
    examMode = false;

    // submission sync with team members
    private submissionChange = new Subject<ModelingSubmission>();
    protected submissionObservable = this.submissionChange.asObservable();
    protected submissionPatchObservable = new Subject<SubmissionPatch>();

    // private modelingEditorInitialized = new ReplaySubject<void>();
    resizeOptions = { verticalResize: true };

    // Icons
    faGripLines = faGripLines;
    farListAlt = faListAlt;
    faExclamationTriangle = faExclamationTriangle;
    faTimeline = faTimeline;

    // mode
    isFeedbackView = false;
    showResultHistory = false;

    ngOnInit(): void {
        if (this.inputValuesArePresent()) {
            this.setupComponentWithInputValues();
        } else {
            this.route.params
                .pipe(
                    switchMap((params) => {
                        this.participationId = params['participationId'] ?? this.participationId;
                        this.submissionId = Number(params['submissionId']) || undefined;
                        this.isFeedbackView = !!this.submissionId;

                        // If participationId exists and feedback view is needed, fetch history results first
                        if (this.participationId && this.isFeedbackView) {
                            return this.fetchSubmissionHistory().pipe(switchMap(() => this.fetchLatestSubmission()));
                        }
                        // Otherwise, directly fetch the latest submission
                        return this.fetchLatestSubmission();
                    }),
                )
                .subscribe({
                    next: (modelingSubmission) => {
                        if (modelingSubmission) {
                            this.updateModelingSubmission(modelingSubmission);
                            this.setupMode();
                        }
                    },
                    error: (error) => onError(this.alertService, error),
                });
        }

        const isDisplayedOnExamSummaryPage = !this.displayHeader && this.participationId !== undefined;
        if (!isDisplayedOnExamSummaryPage) {
            window.scroll(0, 0);
        }
    }

    private setupMode(): void {
        if (this.modelingExercise.teamMode) {
            this.setupSubmissionStreamForTeam();
        } else {
            this.setAutoSaveTimer();
        }
    }

    private fetchLatestSubmission() {
        return this.modelingSubmissionService.getLatestSubmissionForModelingEditor(this.participationId!).pipe(
            catchError((error: HttpErrorResponse) => {
                onError(this.alertService, error);
                return of(null); // Return null on error
            }),
        );
    }

    // Fetch the results and sort them
    // Fetch the submissions and sort them by the latest result's completionDate in descending order
    private fetchSubmissionHistory() {
        return this.modelingSubmissionService.getSubmissionsWithResultsForParticipation(this.participationId!).pipe(
            catchError((error: HttpErrorResponse) => {
                onError(this.alertService, error);
                return of([]);
            }),
            tap((submissions: ModelingSubmission[]) => {
                this.sortedSubmissionHistory = submissions.sort((a, b) => {
                    const latestResultA = this.sortResultsByCompletionDate(a.results ?? [])[0];
                    const latestResultB = this.sortResultsByCompletionDate(b.results ?? [])[0];

                    // Use the latest result's completionDate for comparison
                    const dateA = latestResultA?.completionDate ? dayjs(latestResultA.completionDate).valueOf() : 0;
                    const dateB = latestResultB?.completionDate ? dayjs(latestResultB.completionDate).valueOf() : 0;

                    return dateA - dateB;
                });
                this.sortedResultHistory = this.sortedSubmissionHistory
                    .map((submission) => {
                        let latestResult: Result | undefined; // Initialize latestResult

                        if (submission?.results && submission.results.length > 0) {
                            // Sort results inline to find the latest one
                            const sortedResults = [...submission.results].sort((a, b) => {
                                const dateA = a.completionDate ? dayjs(a.completionDate).valueOf() : 0;
                                const dateB = b.completionDate ? dayjs(b.completionDate).valueOf() : 0;
                                return dateB - dateA; // Descending order (latest date first)
                            });
                            latestResult = sortedResults[0]; // Get the first element after sorting
                        }

                        if (latestResult) {
                            latestResult.participation = submission.participation; // Attach participation if result exists
                        }
                        return latestResult; // Return the latest result (or undefined if no results)
                    })
                    .filter((result): result is Result => !!result);
            }),
        );
    }

    private sortResultsByCompletionDate(results: Result[]): Result[] {
        return results.sort((a, b) => {
            const dateA = a.completionDate ? dayjs(a.completionDate).valueOf() : 0;
            const dateB = b.completionDate ? dayjs(b.completionDate).valueOf() : 0;
            return dateB - dateA;
        });
    }

    private inputValuesArePresent(): boolean {
        return !!(this.inputExercise || this.inputSubmission || this.inputParticipation);
    }

    /**
     * Uses values directly passed to this component instead of subscribing to a participation to save resources
     *
     * <i>e.g. used within {@link ExamResultSummaryComponent} and the respective {@link ModelingExamSummaryComponent}
     * as directly after the exam no grading is present and only the student solution shall be displayed </i>
     * @private
     */
    private setupComponentWithInputValues() {
        if (this.inputExercise) {
            this.modelingExercise = this.inputExercise;
        }
        if (this.inputSubmission) {
            this.submission = this.inputSubmission;
        }
        if (this.inputParticipation) {
            this.participation = this.inputParticipation;
        }

        this.updateModelAndExplanation();
    }

    /**
     * Updates the modeling submission with the given modeling submission.
     */
    private updateModelingSubmission(modelingSubmission: ModelingSubmission): void {
        if (!modelingSubmission) {
            this.alertService.error('artemisApp.apollonDiagram.submission.noSubmission');
        }

        // In the header we always want to display the latest submission, even when we are viewing a specific submission
        this.modelingParticipationHeader = modelingSubmission.participation as StudentParticipation;
        this.modelingParticipationHeader.submissions = [<ModelingSubmission>omit(modelingSubmission, 'participation')];
        this.modelingExerciseHeader = this.modelingParticipationHeader.exercise as ModelingExercise;
        this.modelingExerciseHeader.studentParticipations = [this.participation];

        // If isFeedbackView is true and submissionId is present, we want to find the corresponding submission and not get the latest one
        if (this.isFeedbackView && this.submissionId && this.sortedSubmissionHistory) {
            const matchingSubmission = this.sortedSubmissionHistory.find((submission) => submission.id === this.submissionId);

            if (matchingSubmission) {
                modelingSubmission = matchingSubmission;
            } else {
                captureException(`Submission with ID ${this.submissionId} not found in sorted history results.`);
            }
        }

        this.submission = modelingSubmission;

        // reconnect participation <--> result
        if (getLatestSubmissionResult(modelingSubmission)) {
            modelingSubmission.participation!.results = [getLatestSubmissionResult(modelingSubmission)!];
        }
        this.participation = modelingSubmission.participation as StudentParticipation;
        this.isOwnerOfParticipation = this.accountService.isOwnerOfParticipation(this.participation);

        // reconnect participation <--> submission
        this.participation.submissions = [<ModelingSubmission>omit(modelingSubmission, 'participation')];

        this.modelingExercise = this.participation.exercise as ModelingExercise;
        this.course = getCourseFromExercise(this.modelingExercise);
        this.modelingExercise.studentParticipations = [this.participation];
        this.examMode = !!this.modelingExercise.exerciseGroup;
        if (this.modelingExercise.diagramType == undefined) {
            this.modelingExercise.diagramType = UMLDiagramType.ClassDiagram;
        }
        // checks if the student started the exercise after the due date
        this.isLate =
            this.modelingExercise &&
            !!this.modelingExercise.dueDate &&
            !!this.participation.initializationDate &&
            dayjs(this.participation.initializationDate).isAfter(getExerciseDueDate(this.modelingExercise, this.participation));

        this.isAfterAssessmentDueDate = !this.modelingExercise.assessmentDueDate || dayjs().isAfter(this.modelingExercise.assessmentDueDate);

        this.updateModelAndExplanation();

        this.subscribeToWebsockets();
        if ((getLatestSubmissionResult(this.submission) && this.isAfterAssessmentDueDate) || this.isFeedbackView) {
            this.result = getLatestSubmissionResult(this.submission);
            if (this.isFeedbackView && this.submissionId) {
                this.result = this.sortedSubmissionHistory.find((submission) => submission.id === this.submissionId)?.latestResult;
            }
        }
        this.resultWithComplaint = getFirstResultWithComplaint(this.submission);
        if (this.submission.submitted && this.result && this.result.completionDate) {
            if (!this.isFeedbackView) {
                this.modelingAssessmentService.getAssessment(this.submission.id!).subscribe((assessmentResult: Result) => {
                    this.assessmentResult = assessmentResult;
                    this.prepareAssessmentData();
                });
            } else if (this.result) {
                this.assessmentResult = this.modelingAssessmentService.convertResult(this.result!);
                this.prepareAssessmentData();
            }
        }
        this.isLoading = false;
        this.guidedTourService.enableTourForExercise(this.modelingExercise, modelingTour, true);
    }

    private updateModelAndExplanation(): void {
        if (this.submission.model) {
            this.umlModel = JSON.parse(this.submission.model);
            this.hasElements = this.umlModel.elements && Object.values(this.umlModel.elements).length !== 0;
        }
        this.explanation = this.submission.explanationText ?? '';
    }

    /**
     * If the submission is submitted, subscribe to new results for the participation.
     * Otherwise, subscribe to the automatic submission (which happens when the submission is un-submitted and the exercise due date is over).
     */
    private subscribeToWebsockets(): void {
        if (this.submission && this.submission.id) {
            if (this.submission.submitted) {
                this.subscribeToNewResultsWebsocket();
            } else {
                this.subscribeToAutomaticSubmissionWebsocket();
            }
        }
    }

    /**
     * Subscribes to the websocket channel for automatic submissions. In the server the AutomaticSubmissionService regularly checks for unsubmitted submissions, if the
     * corresponding exercise has finished. If it has, the submission is automatically submitted and sent over this websocket channel. Here we listen to the channel and update the
     * view accordingly.
     */
    private subscribeToAutomaticSubmissionWebsocket(): void {
        if (!this.submission || !this.submission.id) {
            return;
        }
        this.automaticSubmissionWebsocketChannel = '/user/topic/modelingSubmission/' + this.submission.id;
        this.websocketService.subscribe(this.automaticSubmissionWebsocketChannel);
        this.websocketService.receive(this.automaticSubmissionWebsocketChannel).subscribe((submission: ModelingSubmission) => {
            if (submission.submitted) {
                this.submission = submission;
                if (this.submission.model) {
                    this.umlModel = JSON.parse(this.submission.model);
                    this.hasElements = this.umlModel.elements && Object.values(this.umlModel.elements).length !== 0;
                }
                const latestResult = getLatestSubmissionResult(this.submission);
                if (latestResult && latestResult.completionDate && (this.isAfterAssessmentDueDate || latestResult.assessmentType === AssessmentType.AUTOMATIC_ATHENA)) {
                    this.modelingAssessmentService.getAssessment(this.submission.id!).subscribe((assessmentResult: Result) => {
                        this.assessmentResult = assessmentResult;
                        this.prepareAssessmentData();
                    });
                }
                this.alertService.info('artemisApp.modelingEditor.autoSubmit');
            }
        });
    }

    /**
     * Subscribes to the websocket channel for new results. When an assessment is submitted the new result is sent over this websocket channel. Here we listen to the channel
     * and show the new assessment information to the student.
     */
    private subscribeToNewResultsWebsocket(): void {
        if (!this.participation?.id) {
            return;
        }

        const resultStream$ = this.participationWebsocketService.subscribeForLatestResultOfParticipation(this.participation.id, true);

        // Handle initial results (no skip)
        this.manualResultUpdateListener = resultStream$
            .pipe(
                filter((result): result is Result => !!result),
                filter((result) => !result.assessmentType || result.assessmentType !== AssessmentType.AUTOMATIC_ATHENA),
            )
            .subscribe(this.handleManualAssessment.bind(this));

        // Handle Athena results (with skip)
        this.athenaResultUpdateListener = resultStream$
            .pipe(
                skip(1),
                filter((result): result is Result => !!result),
                filter((result) => result.assessmentType === AssessmentType.AUTOMATIC_ATHENA),
            )
            .subscribe(this.handleAthenaAssessment.bind(this));
    }

    /**
     * Handles manual assessments (non-Athena). Converts the result, prepares the assessment data, and informs the user of a new assessment.
     * @param result - The result of the assessment.
     */
    private handleManualAssessment(result: Result): void {
        if (!result.completionDate) {
            return;
        }
        this.assessmentResult = this.modelingAssessmentService.convertResult(result);
        this.prepareAssessmentData();
        this.alertService.info('artemisApp.modelingEditor.newAssessment');
    }

    /**
     * Handles Athena assessments. Converts the result, prepares the assessment data, and provides feedback based on the result's success or failure.
     * @param result - The result of the Athena assessment.
     */
    private handleAthenaAssessment(result: Result): void {
        if (result.completionDate) {
            this.assessmentResult = this.modelingAssessmentService.convertResult(result);
            this.prepareAssessmentData();

            if (result.successful) {
                this.alertService.success('artemisApp.exercise.athenaFeedbackSuccessful');
            }
        } else if (result.successful === false) {
            this.alertService.error('artemisApp.exercise.athenaFeedbackFailed');
        }

        this.isGeneratingFeedback = false;
    }

    /**
     * This function sets and starts an auto-save timer that automatically saves changes
     * to the model after at most 60 seconds.
     */
    private setAutoSaveTimer(): void {
        this.autoSaveTimer = 0;
        // auto save of submission if there are changes
        this.autoSaveInterval = window.setInterval(() => {
            this.autoSaveTimer++;
            this.isChanged = !this.canDeactivate();
            if (this.autoSaveTimer >= AUTOSAVE_EXERCISE_INTERVAL && this.isChanged) {
                this.saveDiagram();
            }
        }, AUTOSAVE_CHECK_INTERVAL);
    }

    /**
     * Check every 2 seconds, if the user made changes for the submission in a team exercise: if yes, send it to the sever
     */
    private setupSubmissionStreamForTeam(): void {
        const teamSyncInterval = window.setInterval(() => {
            this.isChanged = !this.canDeactivate();
            if (this.isChanged) {
                // make sure this.submission includes the newest content of the apollon editor
                this.updateSubmissionWithCurrentValues();
                // notify the team sync component to send this.submission to the server (and all online team members)
                this.submissionChange.next(this.submission);
            }
        }, AUTOSAVE_TEAM_EXERCISE_INTERVAL);

        this.cleanup(() => clearInterval(teamSyncInterval));
    }

    /**
     * Emits submission patches when receiving patches from the modeling editor.
     * These patches need to be synced with other team members in team exercises.
     * The observable through which the patches are emitted is passed to the team sync
     * component, who then sends the patches to the server and other team members.
     * @param patch The patch to update the submission with.
     */
    onModelPatch(patch: Patch) {
        if (this.modelingExercise.teamMode) {
            const submissionPatch = new SubmissionPatch(patch);
            submissionPatch.participation = this.participation;
            if (submissionPatch.participation?.exercise) {
                submissionPatch.participation.exercise.studentParticipations = [];
            }
            this.submissionPatchObservable.next(Object.assign({}, submissionPatch));
        }
    }

    /**
     * Runs given cleanup logic when the component is destroyed.
     * @param teardown The cleanup logic to run when the component is destroyed.
     * @private
     */
    private cleanup(teardown: TeardownLogic) {
        this.subscription ??= new Subscription();
        this.subscription.add(teardown);
    }

    saveDiagram(): void {
        if (this.isSaving) {
            // don't execute the function if it is already currently executing
            return;
        }
        this.updateSubmissionWithCurrentValues();
        this.isSaving = true;
        this.autoSaveTimer = 0;

        if (this.submission.id) {
            this.modelingSubmissionService.update(this.submission, this.modelingExercise.id!).subscribe({
                next: (response) => {
                    this.submission = response.body!;
                    // reconnect so that the submission status is displayed correctly in the result.component
                    this.submission.participation!.submissions = [this.submission];
                    this.participationWebsocketService.addParticipation(this.submission.participation as StudentParticipation, this.modelingExercise);
                    this.result = getLatestSubmissionResult(this.submission);
                    this.onSaveSuccess();
                },
                error: () => this.onSaveError(),
            });
        } else {
            this.modelingSubmissionService.create(this.submission, this.modelingExercise.id!).subscribe({
                next: (submission) => {
                    this.submission = submission.body!;
                    this.result = getLatestSubmissionResult(this.submission);
                    this.subscribeToAutomaticSubmissionWebsocket();
                    this.onSaveSuccess();
                },
                error: () => this.onSaveError(),
            });
        }
    }

    submit(): void {
        if (this.isSaving) {
            // don't execute the function if it is already currently executing
            return;
        }
        this.updateSubmissionWithCurrentValues();
        if (this.isModelEmpty(this.submission.model)) {
            this.alertService.warning('artemisApp.modelingEditor.empty');
            return;
        }
        this.isSaving = true;
        this.autoSaveTimer = 0;
        if (this.submission.id) {
            this.modelingSubmissionService.update(this.submission, this.modelingExercise.id!).subscribe({
                next: (response) => {
                    this.submission = response.body!;
                    if (this.submission.model) {
                        this.umlModel = JSON.parse(this.submission.model);
                        this.hasElements = this.umlModel.elements && Object.values(this.umlModel.elements).length !== 0;
                    }
                    this.submissionChange.next(this.submission);
                    this.participation = this.submission.participation as StudentParticipation;
                    this.participation.exercise = this.modelingExercise;
                    this.modelingParticipationHeader = this.submission.participation as StudentParticipation;
                    // reconnect so that the submission status is displayed correctly in the result.component
                    this.submission.participation!.submissions = [this.submission];
                    this.participationWebsocketService.addParticipation(this.participation, this.modelingExercise);
                    this.modelingExercise.studentParticipations = [this.participation];
                    this.modelingExerciseHeader.studentParticipations = [this.participation];
                    this.result = getLatestSubmissionResult(this.submission);
                    this.retryStarted = false;

                    if (this.isLate) {
                        this.alertService.warning('entity.action.submitDueDateMissedAlert');
                    } else {
                        this.alertService.success('entity.action.submitSuccessfulAlert');
                    }

                    this.subscribeToWebsockets();
                    if (this.automaticSubmissionWebsocketChannel) {
                        this.websocketService.unsubscribe(this.automaticSubmissionWebsocketChannel);
                    }
                    this.onSaveSuccess();
                },
                error: () => this.onSaveError(),
            });
        } else {
            this.modelingSubmissionService.create(this.submission, this.modelingExercise.id!).subscribe({
                next: (response) => {
                    this.submission = response.body!;
                    this.submissionChange.next(this.submission);
                    this.participation = this.submission.participation as StudentParticipation;
                    this.participation.exercise = this.modelingExercise;
                    this.modelingExercise.studentParticipations = [this.participation];
                    this.result = getLatestSubmissionResult(this.submission);
                    if (this.isLate) {
                        this.alertService.warning('artemisApp.modelingEditor.submitDueDateMissed');
                    } else {
                        this.alertService.success('artemisApp.modelingEditor.submitSuccessful');
                    }
                    this.subscribeToAutomaticSubmissionWebsocket();
                    this.onSaveSuccess();
                },
                error: () => this.onSaveError(),
            });
        }
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.isChanged = !this.canDeactivate();
    }

    private onSaveError() {
        this.alertService.error('artemisApp.modelingEditor.error');
        this.isSaving = false;
    }

    onReceiveSubmissionFromTeam(submission: ModelingSubmission) {
        submission.participation!.exercise = this.modelingExercise;
        submission.participation!.submissions = [submission];
        this.updateModelingSubmission(submission);
    }

    /**
     * This is called when the team sync component receives
     * patches from the server. Updates the modeling editor with the received patch.
     * @param submissionPatch
     */
    onReceiveSubmissionPatchFromTeam(submissionPatch: SubmissionPatch) {
        this.modelingEditor.importPatch(submissionPatch.patch);
    }

    private isModelEmpty(model?: string): boolean {
        const umlModel: UMLModel = model ? JSON.parse(model) : undefined;
        return !umlModel || !umlModel.elements || Object.values(umlModel.elements).length === 0;
    }

    ngOnDestroy(): void {
        this.subscription?.unsubscribe();
        clearInterval(this.autoSaveInterval);

        if (this.automaticSubmissionWebsocketChannel) {
            this.websocketService.unsubscribe(this.automaticSubmissionWebsocketChannel);
        }
        if (this.manualResultUpdateListener) {
            this.manualResultUpdateListener.unsubscribe();
        }
        if (this.athenaResultUpdateListener) {
            this.athenaResultUpdateListener.unsubscribe();
        }
    }

    /**
     * Check whether a assessmentResult exists and if, returns the unreferenced feedback of it
     */
    get unreferencedFeedback(): Feedback[] | undefined {
        if (this.assessmentResult?.feedbacks) {
            checkSubsequentFeedbackInAssessment(this.assessmentResult.feedbacks);
            return getUnreferencedFeedback(this.assessmentResult.feedbacks);
        }
        return undefined;
    }

    /**
     * Find "Referenced Feedback" item for Result, if it exists.
     */
    get referencedFeedback(): Feedback[] | undefined {
        if (this.assessmentResult?.feedbacks) {
            checkSubsequentFeedbackInAssessment(this.assessmentResult.feedbacks);
            return this.assessmentResult?.feedbacks?.filter((feedbackElement) => feedbackElement.reference != undefined);
        }
        return undefined;
    }

    /*
     * Check if the latest submission has an Athena result
     */
    get hasAthenaResultForLatestSubmission(): boolean {
        const latestResult = getLatestSubmissionResult(this.submission);
        return latestResult?.assessmentType === AssessmentType.AUTOMATIC_ATHENA;
    }

    /**
     * Updates the model of the submission with the current Apollon model state
     * and the explanation text of submission with current explanation if explanation is defined
     */
    updateSubmissionWithCurrentValues(): void {
        if (!this.submission) {
            this.submission = new ModelingSubmission();
        }
        this.submission.explanationText = this.explanation;
        if (!this.modelingEditor || !this.modelingEditor.getCurrentModel()) {
            return;
        }
        const umlModel = this.modelingEditor.getCurrentModel();
        this.hasElements = umlModel.elements && Object.values(umlModel.elements).length !== 0;
        const diagramJson = JSON.stringify(umlModel);
        if (this.submission && diagramJson) {
            this.submission.model = diagramJson;
        }
    }

    /**
     * Prepare assessment data for displaying the assessment information to the student.
     */
    private prepareAssessmentData(): void {
        this.initializeAssessmentInfo();
    }

    /**
     * Retrieves names for displaying the assessment and calculates the total score
     */
    private initializeAssessmentInfo(): void {
        if (this.assessmentResult?.feedbacks && this.umlModel) {
            this.assessmentsNames = getNamesForAssessments(this.assessmentResult, this.umlModel);
            let totalScore = 0;
            for (const feedback of this.assessmentResult.feedbacks) {
                totalScore += feedback.credits!;
            }
            this.totalScore = totalScore;
        }
    }

    /**
     * Handles changes of the model element selection in Apollon. This is used for displaying
     * only the feedback of the selected model elements.
     * @param selection the new selection
     */
    onSelectionChanged(selection: Selection) {
        this.selectedEntities = Object.entries(selection.elements)
            .filter(([, selected]) => selected)
            .map(([elementId]) => elementId);
        for (const selectedEntity of this.selectedEntities) {
            this.selectedEntities.push(...this.getSelectedChildren(selectedEntity));
        }
        this.selectedRelationships = Object.entries(selection.relationships)
            .filter(([, selected]) => selected)
            .map(([elementId]) => elementId);
    }

    /**
     * Returns the elementIds of all the children of the element with the given elementId
     * or an empty list, if no children exist for this element.
     */
    private getSelectedChildren(elementId: string): string[] {
        if (!this.umlModel || !this.umlModel.elements) {
            return [];
        }
        return Object.values(this.umlModel.elements)
            .filter((element) => element.owner === elementId)
            .map((element) => element.id);
    }

    /**
     * Checks whether a model element in the modeling editor is selected.
     */
    shouldBeDisplayed(feedback: Feedback): boolean {
        if ((!this.selectedEntities || this.selectedEntities.length === 0) && (!this.selectedRelationships || this.selectedRelationships.length === 0)) {
            return true;
        }
        const referencedModelType = feedback.referenceType! as UMLElementType;
        if (referencedModelType in UMLRelationshipType) {
            return this.selectedRelationships.indexOf(feedback.referenceId!) > -1;
        } else {
            return this.selectedEntities.indexOf(feedback.referenceId!) > -1;
        }
    }

    canDeactivate(): boolean {
        if (!this.modelingEditor || !this.modelingEditor.isApollonEditorMounted) {
            return true;
        }
        const model: UMLModel = this.modelingEditor.getCurrentModel();
        const explanationIsUpToDate = this.explanation === (this.submission.explanationText ?? '');
        return !this.modelHasUnsavedChanges(model) && explanationIsUpToDate;
    }

    /**
     * Checks whether there are pending changes in the current model. Returns true if there are unsaved changes, false otherwise.
     */
    private modelHasUnsavedChanges(model: UMLModel): boolean {
        if (!this.submission || !this.submission.model) {
            return Object.values(model.elements).length > 0 && JSON.stringify(model) !== '';
        } else if (this.submission && this.submission.model) {
            const currentModel = JSON.parse(this.submission.model);
            const versionMatch = currentModel.version === model.version;
            const modelMatch = stringifyIgnoringFields(currentModel, 'size') === stringifyIgnoringFields(model, 'size');
            return versionMatch && !modelMatch;
        }
        return false;
    }

    /**
     * counts the number of model elements
     * is used in the submit() function
     */
    calculateNumberOfModelElements(): number {
        if (this.submission && this.submission.model) {
            const umlModel = JSON.parse(this.submission.model);
            return umlModel.elements.length + umlModel.relationships.length;
        }
        return 0;
    }

    /**
     * The exercise is still active if it's due date hasn't passed yet.
     */
    get isActive(): boolean {
        return this.modelingExercise && !this.examMode && !hasExerciseDueDatePassed(this.modelingExercise, this.participation);
    }

    get submitButtonTooltip(): string {
        if (!this.isLate) {
            if (this.isActive && !this.modelingExercise.dueDate) {
                return 'entity.action.submitNoDueDateTooltip';
            } else if (this.isActive) {
                return 'entity.action.submitTooltip';
            } else {
                return 'entity.action.dueDateMissedTooltip';
            }
        }

        return 'entity.action.submitDueDateMissedTooltip';
    }

    protected readonly hasExerciseDueDatePassed = hasExerciseDueDatePassed;
}
