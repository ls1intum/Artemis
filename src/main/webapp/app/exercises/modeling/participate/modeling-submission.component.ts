import { HttpErrorResponse } from '@angular/common/http';
import { Component, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Selection, UMLElementType, UMLModel, UMLRelationshipType } from '@ls1intum/apollon';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ComplaintType } from 'app/entities/complaint.model';
import { Feedback, buildFeedbackTextForReview, checkSubsequentFeedbackInAssessment } from 'app/entities/feedback.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { getFirstResultWithComplaint, getLatestSubmissionResult } from 'app/entities/submission.model';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { hasExerciseDueDatePassed, participationStatus } from 'app/exercises/shared/exercise/exercise.utils';
import { addParticipationToResult, getUnreferencedFeedback } from 'app/exercises/shared/result/result.utils';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { AccountService } from 'app/core/auth/account.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { modelingTour } from 'app/guided-tour/tours/modeling-tour';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { ButtonType } from 'app/shared/components/button.component';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL, AUTOSAVE_TEAM_EXERCISE_INTERVAL } from 'app/shared/constants/exercise-exam-constants';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { stringifyIgnoringFields } from 'app/shared/util/utils';
import { Subject, Subscription } from 'rxjs';
import { omit } from 'lodash-es';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/core/util/alert.service';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { getNamesForAssessments } from '../assess/modeling-assessment.util';
import { faExclamationTriangle, faGripLines } from '@fortawesome/free-solid-svg-icons';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';

@Component({
    selector: 'jhi-modeling-submission',
    templateUrl: './modeling-submission.component.html',
    styleUrls: ['./modeling-submission.component.scss'],
})
export class ModelingSubmissionComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    readonly addParticipationToResult = addParticipationToResult;
    readonly buildFeedbackTextForReview = buildFeedbackTextForReview;

    @ViewChild(ModelingEditorComponent, { static: false })
    modelingEditor: ModelingEditorComponent;
    ButtonType = ButtonType;

    private subscription: Subscription;
    private resultUpdateListener: Subscription;

    participation: StudentParticipation;
    isOwnerOfParticipation: boolean;

    modelingExercise: ModelingExercise;
    course?: Course;
    result?: Result;
    resultWithComplaint?: Result;

    selectedEntities: string[];
    selectedRelationships: string[];

    submission: ModelingSubmission;

    assessmentResult?: Result;
    assessmentsNames: Map<string, Map<string, string>>;
    totalScore: number;

    umlModel: UMLModel; // input model for Apollon
    hasElements = false; // indicates if the current model has at least one element
    isSaving: boolean;
    isChanged: boolean;
    retryStarted = false;
    autoSaveInterval: number;
    autoSaveTimer: number;

    explanation: string; // current explanation on text editor

    automaticSubmissionWebsocketChannel: string;

    // indicates if the assessment due date is in the past. the assessment will not be loaded and displayed to the student if it is not.
    isAfterAssessmentDueDate: boolean;
    isLoading: boolean;
    isLate: boolean; // indicates if the submission is late
    ComplaintType = ComplaintType;
    examMode = false;

    // submission sync with team members
    teamSyncInterval: number;
    private submissionChange = new Subject<ModelingSubmission>();
    submissionObservable = this.submissionChange.asObservable();

    resizeOptions = { verticalResize: true };

    // Icons
    faGripLines = faGripLines;
    farListAlt = faListAlt;
    faExclamationTriangle = faExclamationTriangle;

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private apollonDiagramService: ApollonDiagramService,
        private modelingSubmissionService: ModelingSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private resultService: ResultService,
        private alertService: AlertService,
        private route: ActivatedRoute,
        private modalService: NgbModal,
        private translateService: TranslateService,
        private router: Router,
        private participationWebsocketService: ParticipationWebsocketService,
        private guidedTourService: GuidedTourService,
        private accountService: AccountService,
    ) {
        this.isSaving = false;
        this.autoSaveTimer = 0;
        this.isLoading = true;
    }

    ngOnInit(): void {
        this.subscription = this.route.params.subscribe((params) => {
            if (params['participationId']) {
                this.modelingSubmissionService.getLatestSubmissionForModelingEditor(params['participationId']).subscribe({
                    next: (modelingSubmission) => {
                        this.updateModelingSubmission(modelingSubmission);
                        if (this.modelingExercise.teamMode) {
                            this.setupSubmissionStreamForTeam();
                        } else {
                            this.setAutoSaveTimer();
                        }
                    },
                    error: (error) => {
                        if (error.status === 403) {
                            this.router.navigate(['accessdenied']);
                        }
                    },
                });
            }
        });
        window.scroll(0, 0);
    }

    // Updates component with the given modeling submission
    private updateModelingSubmission(modelingSubmission: ModelingSubmission) {
        if (!modelingSubmission) {
            this.alertService.error('artemisApp.apollonDiagram.submission.noSubmission');
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
        this.modelingExercise.participationStatus = participationStatus(this.modelingExercise);
        if (this.modelingExercise.diagramType == undefined) {
            this.modelingExercise.diagramType = UMLDiagramType.ClassDiagram;
        }
        // checks if the student started the exercise after the due date
        this.isLate =
            this.modelingExercise &&
            !!this.modelingExercise.dueDate &&
            !!this.participation.initializationDate &&
            dayjs(this.participation.initializationDate).isAfter(this.modelingExercise.dueDate);
        this.isAfterAssessmentDueDate = !this.modelingExercise.assessmentDueDate || dayjs().isAfter(this.modelingExercise.assessmentDueDate);
        if (this.submission.model) {
            this.umlModel = JSON.parse(this.submission.model);
            this.hasElements = this.umlModel.elements && this.umlModel.elements.length !== 0;
        }
        this.explanation = this.submission.explanationText ?? '';
        this.subscribeToWebsockets();
        if (getLatestSubmissionResult(this.submission) && this.isAfterAssessmentDueDate) {
            this.result = getLatestSubmissionResult(this.submission);
        }
        this.resultWithComplaint = getFirstResultWithComplaint(this.submission);
        if (this.submission.submitted && this.result && this.result.completionDate) {
            this.modelingAssessmentService.getAssessment(this.submission.id!).subscribe((assessmentResult: Result) => {
                this.assessmentResult = assessmentResult;
                this.prepareAssessmentData();
            });
        }
        this.isLoading = false;
        this.guidedTourService.enableTourForExercise(this.modelingExercise, modelingTour, true);
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
        this.jhiWebsocketService.subscribe(this.automaticSubmissionWebsocketChannel);
        this.jhiWebsocketService.receive(this.automaticSubmissionWebsocketChannel).subscribe((submission: ModelingSubmission) => {
            if (submission.submitted) {
                this.submission = submission;
                if (this.submission.model) {
                    this.umlModel = JSON.parse(this.submission.model);
                    this.hasElements = this.umlModel.elements && this.umlModel.elements.length !== 0;
                }
                if (getLatestSubmissionResult(this.submission) && getLatestSubmissionResult(this.submission)!.completionDate && this.isAfterAssessmentDueDate) {
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
        if (!this.participation || !this.participation.id) {
            return;
        }
        this.resultUpdateListener = this.participationWebsocketService.subscribeForLatestResultOfParticipation(this.participation.id, true).subscribe((newResult: Result) => {
            if (newResult && newResult.completionDate) {
                this.assessmentResult = newResult;
                this.assessmentResult = this.modelingAssessmentService.convertResult(newResult);
                this.prepareAssessmentData();
                this.alertService.info('artemisApp.modelingEditor.newAssessment');
            }
        });
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
        this.teamSyncInterval = window.setInterval(() => {
            this.isChanged = !this.canDeactivate();
            if (this.isChanged) {
                // make sure this.submission includes the newest content of the apollon editor
                this.updateSubmissionWithCurrentValues();
                // notify the team sync component to send this.submission to the server (and all online team members)
                this.submissionChange.next(this.submission);
            }
        }, AUTOSAVE_TEAM_EXERCISE_INTERVAL);
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
                error: (error: HttpErrorResponse) => this.onSaveError(error),
            });
        } else {
            this.modelingSubmissionService.create(this.submission, this.modelingExercise.id!).subscribe({
                next: (submission) => {
                    this.submission = submission.body!;
                    this.result = getLatestSubmissionResult(this.submission);
                    this.subscribeToAutomaticSubmissionWebsocket();
                    this.onSaveSuccess();
                },
                error: (error: HttpErrorResponse) => this.onSaveError(error),
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
                        this.hasElements = this.umlModel.elements && this.umlModel.elements.length !== 0;
                    }
                    this.submissionChange.next(this.submission);
                    this.participation = this.submission.participation as StudentParticipation;
                    this.participation.exercise = this.modelingExercise;
                    // reconnect so that the submission status is displayed correctly in the result.component
                    this.submission.participation!.submissions = [this.submission];
                    this.participationWebsocketService.addParticipation(this.participation, this.modelingExercise);
                    this.modelingExercise.studentParticipations = [this.participation];
                    this.modelingExercise.participationStatus = participationStatus(this.modelingExercise);
                    this.result = getLatestSubmissionResult(this.submission);
                    this.retryStarted = false;

                    if (this.isLate) {
                        this.alertService.warning('entity.action.submitDeadlineMissedAlert');
                    } else {
                        this.alertService.success('entity.action.submitSuccessfulAlert');
                    }

                    this.subscribeToWebsockets();
                    if (this.automaticSubmissionWebsocketChannel) {
                        this.jhiWebsocketService.unsubscribe(this.automaticSubmissionWebsocketChannel);
                    }
                    this.onSaveSuccess();
                },
                error: (error: HttpErrorResponse) => this.onSaveError(error),
            });
        } else {
            this.modelingSubmissionService.create(this.submission, this.modelingExercise.id!).subscribe({
                next: (response) => {
                    this.submission = response.body!;
                    this.submissionChange.next(this.submission);
                    this.participation = this.submission.participation as StudentParticipation;
                    this.participation.exercise = this.modelingExercise;
                    this.modelingExercise.studentParticipations = [this.participation];
                    this.modelingExercise.participationStatus = participationStatus(this.modelingExercise);
                    this.result = getLatestSubmissionResult(this.submission);
                    if (this.isLate) {
                        this.alertService.warning('artemisApp.modelingEditor.submitDeadlineMissed');
                    } else {
                        this.alertService.success('artemisApp.modelingEditor.submitSuccessful');
                    }
                    this.subscribeToAutomaticSubmissionWebsocket();
                    this.onSaveSuccess();
                },
                error: (error: HttpErrorResponse) => this.onSaveError(error),
            });
        }
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.isChanged = !this.canDeactivate();
    }

    private onSaveError(error?: HttpErrorResponse) {
        if (error) {
            console.error(error.message);
        }
        this.alertService.error('artemisApp.modelingEditor.error');
        this.isSaving = false;
    }

    onReceiveSubmissionFromTeam(submission: ModelingSubmission) {
        submission.participation!.exercise = this.modelingExercise;
        submission.participation!.submissions = [submission];
        this.updateModelingSubmission(submission);
    }

    private isModelEmpty(model?: string): boolean {
        const umlModel: UMLModel = model ? JSON.parse(model) : undefined;
        return !umlModel || !umlModel.elements || umlModel.elements.length === 0;
    }

    ngOnDestroy(): void {
        this.subscription?.unsubscribe();
        clearInterval(this.autoSaveInterval);
        clearInterval(this.teamSyncInterval);
        if (this.automaticSubmissionWebsocketChannel) {
            this.jhiWebsocketService.unsubscribe(this.automaticSubmissionWebsocketChannel);
        }
        if (this.resultUpdateListener) {
            this.resultUpdateListener.unsubscribe();
        }
    }

    /**
     * Check whether or not a assessmentResult exists and if, returns the unreferenced feedback of it
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
        this.hasElements = umlModel.elements && umlModel.elements.length !== 0;
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
        if (this.assessmentResult && this.assessmentResult.feedbacks && this.umlModel) {
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
        this.selectedEntities = selection.elements;
        for (const selectedEntity of this.selectedEntities) {
            this.selectedEntities.push(...this.getSelectedChildren(selectedEntity));
        }
        this.selectedRelationships = selection.relationships;
    }

    /**
     * Returns the elementIds of all the children of the element with the given elementId
     * or an empty list, if no children exist for this element.
     */
    private getSelectedChildren(elementId: string): string[] {
        if (!this.umlModel || !this.umlModel.elements) {
            return [];
        }
        return this.umlModel.elements.filter((element) => element.owner === elementId).map((element) => element.id);
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
            return model.elements.length > 0 && JSON.stringify(model) !== '';
        } else if (this.submission && this.submission.model) {
            const currentModel = JSON.parse(this.submission.model);
            const versionMatch = currentModel.version === model.version;
            const modelMatch = stringifyIgnoringFields(currentModel, 'size') === stringifyIgnoringFields(model, 'size');
            return versionMatch && !modelMatch;
        }
        return false;
    }

    // displays the alert for confirming leaving the page if there are unsaved changes
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification(event: any): void {
        if (!this.canDeactivate()) {
            event.returnValue = this.translateService.instant('pendingChanges');
        }
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
                return 'entity.action.submitNoDeadlineTooltip';
            } else if (this.isActive) {
                return 'entity.action.submitTooltip';
            } else {
                return 'entity.action.deadlineMissedTooltip';
            }
        }

        return 'entity.action.submitDeadlineMissedTooltip';
    }
}
