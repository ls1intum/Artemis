import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, computed, inject, input, signal, viewChild } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faCheck, faDownLeftAndUpRightToCenter, faExclamationTriangle, faTriangleExclamation, faUpRightAndDownLeftFromCenter, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { captureException } from '@sentry/angular';
import { type CollaborationUser, UMLDiagramType, UMLModel, collabColorFromName, importDiagram } from '@tumaet/apollon';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { Feedback, buildFeedbackTextForReview, checkSubsequentFeedbackInAssessment } from 'app/assessment/shared/entities/feedback.model';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { getUnreferencedFeedback } from 'app/exercise/result/result.utils';
import { getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation, isPracticeMode } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { SubmissionPatch } from 'app/exercise/shared/entities/submission/submission-patch.model';
import { getFirstResultWithComplaint, getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { TeamSubmissionSyncComponent } from 'app/exercise/team-submission-sync/team-submission-sync.component';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercise/util/exercise.utils';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { ModelingSubmissionService } from 'app/modeling/overview/modeling-submission/modeling-submission.service';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL, AUTOSAVE_TEAM_EXERCISE_INTERVAL } from 'app/foundation/constants/exercise-exam-constants';
import { ComponentCanDeactivate } from 'app/foundation/guard/can-deactivate.model';
import { ExerciseSubmission } from 'app/exercise/shared/exercise-submission.interface';
import { ModelingAssessmentPanelDirective } from 'app/modeling/manage/assess/modeling-assessment-panel.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { ResizeableContainerComponent } from 'app/shared-ui/resizeable-container/resizeable-container.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { LocaleConversionService } from 'app/foundation/service/locale-conversion.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { onError } from 'app/foundation/util/global.utils';
import { parseJson } from 'app/foundation/util/json.util';
import { stringifyIgnoringFields } from 'app/foundation/util/utils';
import dayjs from 'dayjs/esm';
import { omit } from 'lodash-es';
import { Subject, Subscription, TeardownLogic, of } from 'rxjs';
import { catchError, filter, skip, switchMap, tap } from 'rxjs/operators';
import { ModelingAssessmentComponent } from '../../manage/assess/modeling-assessment.component';
import { AssessmentNamesForModelId, getNamesForAssessments } from '../../manage/assess/modeling-assessment.util';
import { ApollonModelData, countModelElements, hasModelElements, isModelEmpty as isApollonModelEmpty } from '../../shared/apollon-model.util';
import { toSignal } from '@angular/core/rxjs-interop';
import { deepClone } from 'app/foundation/util/deep-clone.util';

const FEEDBACK_PREVIEW_HIGHLIGHT = 'var(--apollon-interactive-selection)';

@Component({
    selector: 'jhi-modeling-submission',
    templateUrl: './modeling-submission.component.html',
    styleUrls: ['./modeling-submission.component.scss'],
    imports: [
        ResizeableContainerComponent,
        ModelingEditorComponent,
        FaIconComponent,
        TeamSubmissionSyncComponent,
        ModelingAssessmentComponent,
        TranslateDirective,
        RatingComponent,
        ComplaintsStudentViewComponent,
        MarkdownDirective,
        ArtemisTranslatePipe,
        ModelingAssessmentPanelDirective,
        NgTemplateOutlet,
    ],
    host: { '(window:beforeunload)': 'unloadNotification($event)' },
})
export class ModelingSubmissionComponent implements OnInit, OnDestroy, ComponentCanDeactivate, ExerciseSubmission {
    private websocketService = inject(WebsocketService);
    private modelingSubmissionService = inject(ModelingSubmissionService);
    private modelingAssessmentService = inject(ModelingAssessmentService);
    private alertService = inject(AlertService);
    private route = inject(ActivatedRoute);
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private accountService = inject(AccountService);
    private translateService = inject(TranslateService);
    private localeConversionService = inject(LocaleConversionService);

    readonly buildFeedbackTextForReview = buildFeedbackTextForReview;

    readonly modelingEditor = viewChild(ModelingEditorComponent);
    readonly modelingAssessment = viewChild(ModelingAssessmentComponent);
    protected readonly faEnterFullscreen = faUpRightAndDownLeftFromCenter;
    protected readonly faExitFullscreen = faDownLeftAndUpRightToCenter;

    protected feedbackTone(feedback: Feedback): 'positive' | 'negative' | 'zero' {
        const credits = feedback.credits ?? 0;
        if (credits > 0) {
            return 'positive';
        }
        return credits < 0 ? 'negative' : 'zero';
    }

    protected feedbackToneIcon(feedback: Feedback): IconDefinition {
        const tone = this.feedbackTone(feedback);
        if (tone === 'positive') {
            return faCheck;
        }
        return tone === 'negative' ? faXmark : faTriangleExclamation;
    }

    protected feedbackPoints(feedback: Feedback): string {
        const credits = feedback.credits ?? 0;
        const formatted = this.localeConversionService.toLocaleString(credits, this.course()?.accuracyOfScores);
        const label = this.translateService.instant(`artemisApp.assessment.detail.points.${Math.abs(credits) === 1 ? 'one' : 'many'}`, {
            points: formatted,
        });
        return credits > 0 ? `+${label}` : label;
    }

    protected feedbackElementName(feedback: Feedback): string | undefined {
        const names = this.assessmentsNames();
        const referenceId = feedback.referenceId;
        const assessment = names && referenceId ? names[referenceId] : undefined;
        if (!assessment?.name) {
            return undefined;
        }
        const name = assessment.name.replace('::', ' › ');
        return assessment.type ? `${assessment.type} ${name}` : name;
    }

    participationId = input<number>();
    inputExercise = input<ModelingExercise>();
    inputSubmission = input<ModelingSubmission>();
    inputParticipation = input<StudentParticipation>();

    isExamSummary = input(false);
    displayHeader = input(true);
    isPrinting = input(false);
    expandProblemStatement = input(false);
    showProblemStatement = input(true);

    private subscription?: Subscription;
    private manualResultUpdateListener?: Subscription;
    private athenaResultUpdateListener?: Subscription;

    readonly participation = signal<StudentParticipation>(undefined!);
    readonly isOwnerOfParticipation = signal<boolean>(undefined!);

    readonly modelingExercise = signal<ModelingExercise>(undefined!);
    readonly course = signal<Course | undefined>(undefined);
    readonly result = signal<Result | undefined>(undefined);
    readonly resultWithComplaint = signal<Result | undefined>(undefined);

    readonly selectedElementIds = signal<string[]>([]);
    protected readonly previewedFeedbackReferenceId = signal<string | undefined>(undefined);
    protected readonly highlightedFeedbackElements = computed(() => {
        const referenceId = this.previewedFeedbackReferenceId();
        return referenceId ? new Map([[referenceId, FEEDBACK_PREVIEW_HIGHLIGHT]]) : undefined;
    });

    protected readonly apollonCollaborationUser = signal<CollaborationUser | undefined>(undefined);

    readonly submission = signal<ModelingSubmission>(undefined!);
    submissionId: number | undefined;
    resultId: number | undefined;
    readonly sortedSubmissionHistory = signal<ModelingSubmission[]>(undefined!);
    readonly sortedResultHistory = signal<Result[]>([]);

    readonly assessmentResult = signal<Result | undefined>(undefined);
    readonly assessmentsNames = signal<AssessmentNamesForModelId>({});
    totalScore = 0;

    readonly umlModel = signal<UMLModel>(undefined!);
    readonly hasElements = signal(false);
    readonly isSaving = signal(false);
    readonly isChanged = signal(false);
    readonly retryStarted = signal(false);
    autoSaveInterval?: number;
    private teamSyncInterval?: number;
    readonly autoSaveTimer = signal(0);

    explanation = '';

    automaticSubmissionSubscription?: Subscription;

    isAfterAssessmentDueDate = false;
    readonly isLoading = signal(true);
    readonly isLate = signal<boolean>(undefined!);
    ComplaintType = ComplaintType;
    readonly examMode = signal(false);

    private submissionChange = new Subject<ModelingSubmission>();
    protected submissionObservable = this.submissionChange.asObservable();
    protected submissionPatchObservable = new Subject<SubmissionPatch>();

    farListAlt = faListAlt;
    faExclamationTriangle = faExclamationTriangle;

    readonly isFeedbackView = signal(false);

    protected hasAssessmentToShow(): boolean {
        return !!this.assessmentResult()?.feedbacks?.length || !!this.result();
    }

    protected showComplaintSection(): boolean {
        return (
            !!this.result() &&
            !this.examMode() &&
            !this.isFeedbackView() &&
            !isPracticeMode(this.participation()) &&
            this.result()!.assessmentType !== AssessmentType.AUTOMATIC_ATHENA
        );
    }

    readonly canSubmitExercise = computed(() => this.shouldShowLiveEditor());

    protected shouldShowLiveEditor(): boolean {
        return (
            !!this.submission() &&
            (this.isActive || this.isLate()) &&
            !(this.result() && !this.isAutomaticResult) &&
            (!this.isLate() || !this.submission().submitted) &&
            !this.isFeedbackView()
        );
    }

    private routeParams = toSignal(this.route.params);

    private effectiveParticipationId = computed(() => {
        return this.participationId() ?? Number(this.routeParams()?.['participationId']);
    });

    ngOnInit(): void {
        this.initializeApollonCollaborationUser();

        if (this.inputValuesArePresent()) {
            this.setupComponentWithInputValues();
        } else {
            this.route.params
                .pipe(
                    switchMap((params) => {
                        this.submissionId = Number(params['submissionId']) || undefined;
                        this.resultId = Number(params['resultId']) || undefined;
                        this.isFeedbackView.set(!!this.submissionId);

                        if (this.effectiveParticipationId() && this.isFeedbackView()) {
                            return this.fetchSubmissionHistory().pipe(switchMap(() => this.fetchLatestSubmission()));
                        }
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
                    error: (error) => {
                        onError(this.alertService, error);
                    },
                });
        }

        const isDisplayedOnExamSummaryPage = !this.displayHeader() && this.effectiveParticipationId() !== undefined;
        if (!isDisplayedOnExamSummaryPage) {
            window.scroll(0, 0);
        }
    }

    private initializeApollonCollaborationUser(): void {
        void this.accountService.identity().then((user: User | undefined) => {
            if (!user) {
                captureException('Modeling team exercise: no user identity available for Apollon collaboration.');
                return;
            }
            this.apollonCollaborationUser.set(this.buildApollonCollaborationUser(user));
        });
    }

    private buildApollonCollaborationUser(user: User): CollaborationUser {
        const name = user.name || user.login || 'User';
        return { id: user.login, name, color: collabColorFromName(name), imageUrl: this.accountService.getImageUrl() };
    }

    private setupMode(): void {
        if (this.modelingExercise().teamMode) {
            this.setupSubmissionStreamForTeam();
        } else {
            this.setAutoSaveTimer();
        }
    }

    private fetchLatestSubmission() {
        return this.modelingSubmissionService.getLatestSubmissionForModelingEditor(this.effectiveParticipationId()).pipe(
            catchError((error: HttpErrorResponse) => {
                onError(this.alertService, error);
                return of(null);
            }),
        );
    }

    private fetchSubmissionHistory() {
        return this.modelingSubmissionService.getSubmissionsWithResultsForParticipation(this.effectiveParticipationId()).pipe(
            catchError((error: HttpErrorResponse) => {
                onError(this.alertService, error);
                return of([]);
            }),
            tap((submissions: ModelingSubmission[]) => {
                this.sortedSubmissionHistory.set(
                    submissions.sort((a, b) => {
                        const latestResultA = this.sortResultsByCompletionDate(a.results ?? [])[0];
                        const latestResultB = this.sortResultsByCompletionDate(b.results ?? [])[0];

                        const dateA = latestResultA?.completionDate ? dayjs(latestResultA.completionDate).valueOf() : 0;
                        const dateB = latestResultB?.completionDate ? dayjs(latestResultB.completionDate).valueOf() : 0;

                        return dateA - dateB;
                    }),
                );
                const sortedResultHistory = this.sortedSubmissionHistory()
                    .map((submission) => {
                        let latestResult: Result | undefined;

                        if (submission?.results && submission.results.length > 0) {
                            const sortedResults = [...submission.results].sort((a, b) => {
                                const dateA = a.completionDate ? dayjs(a.completionDate).valueOf() : 0;
                                const dateB = b.completionDate ? dayjs(b.completionDate).valueOf() : 0;
                                return dateB - dateA;
                            });
                            latestResult = sortedResults[0];
                        }

                        if (latestResult) {
                            latestResult.submission = submission;
                        }
                        return latestResult;
                    })
                    .filter((result): result is Result => !!result);
                this.sortedResultHistory.set(sortedResultHistory);
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
        return !!(this.inputExercise() || this.inputSubmission() || this.inputParticipation());
    }

    private setupComponentWithInputValues() {
        const inputExercise = this.inputExercise();
        if (inputExercise) {
            this.modelingExercise.set(inputExercise);
        }
        const inputSubmission = this.inputSubmission();
        if (inputSubmission) {
            this.submission.set(inputSubmission);
        }
        const inputParticipation = this.inputParticipation();
        if (inputParticipation) {
            this.participation.set(inputParticipation);
        }

        this.updateModelAndExplanation();
    }

    /** Clears state because Angular reuses this component across participation IDs. */
    private resetSubmissionScopedState(): void {
        this.result.set(undefined);
        this.assessmentResult.set(undefined);
        this.resultWithComplaint.set(undefined);
        this.assessmentsNames.set({});
        this.totalScore = 0;
        this.selectedElementIds.set([]);
        this.previewedFeedbackReferenceId.set(undefined);
        this.retryStarted.set(false);
        this.isChanged.set(false);
        this.isSaving.set(false);
    }

    private updateModelingSubmission(modelingSubmission: ModelingSubmission): void {
        if (!modelingSubmission) {
            this.alertService.error('artemisApp.apollonDiagram.submission.noSubmission');
        }

        this.resetSubmissionScopedState();

        if (this.isFeedbackView() && this.submissionId && this.sortedSubmissionHistory()) {
            const matchingSubmission = this.sortedSubmissionHistory().find((submission) => submission.id === this.submissionId);

            if (matchingSubmission) {
                modelingSubmission = matchingSubmission;
            } else {
                captureException(`Submission with ID ${this.submissionId} not found in sorted history results.`);
            }
        }

        this.submission.set(modelingSubmission);

        if (getLatestSubmissionResult(modelingSubmission) && !(this.isFeedbackView() && this.resultId)) {
            modelingSubmission.results = [getLatestSubmissionResult(modelingSubmission)!];
        }
        this.participation.set(modelingSubmission.participation as StudentParticipation);
        this.isOwnerOfParticipation.set(this.accountService.isOwnerOfParticipation(this.participation()));

        this.participation().submissions = [omit(modelingSubmission, 'participation')];

        this.modelingExercise.set(this.participation().exercise as ModelingExercise);
        this.course.set(getCourseFromExercise(this.modelingExercise()));
        this.modelingExercise().studentParticipations = [this.participation()];
        this.examMode.set(!!this.modelingExercise().exerciseGroup);
        if (this.modelingExercise().diagramType == undefined) {
            this.modelingExercise().diagramType = UMLDiagramType.ClassDiagram;
        }
        this.isLate.set(
            this.modelingExercise() &&
                !!this.modelingExercise().dueDate &&
                !!this.participation().initializationDate &&
                !this.participation().testRun &&
                dayjs(this.participation().initializationDate).isAfter(getExerciseDueDate(this.modelingExercise(), this.participation())),
        );

        this.isAfterAssessmentDueDate = !this.modelingExercise().assessmentDueDate || dayjs().isAfter(this.modelingExercise().assessmentDueDate);

        this.updateModelAndExplanation();

        this.subscribeToWebsockets();
        if ((getLatestSubmissionResult(this.submission()) && this.isAfterAssessmentDueDate) || this.isFeedbackView()) {
            this.result.set(getLatestSubmissionResult(this.submission()));
            if (this.isFeedbackView() && this.submissionId) {
                if (this.resultId) {
                    this.result.set(this.submission().results?.find((result) => result.id === this.resultId));
                } else {
                    this.result.set(this.sortedResultHistory().find((result) => result.submission?.id === this.submissionId));
                }
            }
        }
        this.resultWithComplaint.set(getFirstResultWithComplaint(this.submission()));
        const result = this.result();
        if (this.submission().submitted && result && result.completionDate) {
            if (result.feedbacks && result.feedbacks.length > 0) {
                this.assessmentResult.set(this.modelingAssessmentService.convertResult(result));
                this.prepareAssessmentData();
            } else if (!this.isAutomaticResult && this.isFeedbackView() && this.resultId && this.submissionId) {
                this.modelingAssessmentService.getAssessment(this.submissionId, this.resultId).subscribe({
                    next: (assessmentResult: Result) => {
                        this.assessmentResult.set(assessmentResult);
                        this.prepareAssessmentData();
                    },
                    error: (error: HttpErrorResponse) => {
                        this.isLoading.set(false);
                        this.assessmentResult.set(undefined);
                        onError(this.alertService, error);
                    },
                });
            } else {
                this.assessmentResult.set(this.modelingAssessmentService.convertResult(result));
                this.prepareAssessmentData();
            }
        }
        this.isLoading.set(false);
    }

    private updateModelAndExplanation(): void {
        if (this.submission().model) {
            this.umlModel.set(importDiagram(parseJson(this.submission().model!)));
            this.hasElements.set(hasModelElements(this.umlModel()));
        } else {
            this.umlModel.set(undefined!);
            this.hasElements.set(false);
        }
        this.explanation = this.submission().explanationText ?? '';
    }

    private refreshNonCollaborativeEditorFromSavedSubmission(): void {
        const model = this.submission().model;
        // Importing a persisted team snapshot can discard newer edits in the live Yjs document.
        if (this.modelingExercise().teamMode || !model) {
            return;
        }
        this.umlModel.set(importDiagram(parseJson(model)));
        this.hasElements.set(hasModelElements(this.umlModel()));
    }

    private subscribeToWebsockets(): void {
        this.automaticSubmissionSubscription?.unsubscribe();
        this.automaticSubmissionSubscription = undefined;
        this.manualResultUpdateListener?.unsubscribe();
        this.manualResultUpdateListener = undefined;
        this.athenaResultUpdateListener?.unsubscribe();
        this.athenaResultUpdateListener = undefined;

        if (this.submission() && this.submission().id) {
            if (this.submission().submitted) {
                this.subscribeToNewResultsWebsocket();
            } else {
                this.subscribeToAutomaticSubmissionWebsocket();
            }
        }
    }

    private subscribeToAutomaticSubmissionWebsocket(): void {
        if (!this.submission() || !this.submission().id) {
            return;
        }
        this.automaticSubmissionSubscription?.unsubscribe();
        this.automaticSubmissionSubscription = this.websocketService
            .subscribe<ModelingSubmission>('/user/topic/modelingSubmission/' + this.submission().id)
            .subscribe((submission: ModelingSubmission) => {
                if (submission.submitted) {
                    this.submission.set(submission);
                    this.refreshNonCollaborativeEditorFromSavedSubmission();
                    const latestResult = getLatestSubmissionResult(this.submission());
                    if (latestResult && latestResult.completionDate && (this.isAfterAssessmentDueDate || latestResult.assessmentType === AssessmentType.AUTOMATIC_ATHENA)) {
                        this.modelingAssessmentService.getAssessment(this.submission().id!).subscribe((assessmentResult: Result) => {
                            this.assessmentResult.set(assessmentResult);
                            this.prepareAssessmentData();
                        });
                    }
                    this.alertService.info('artemisApp.modelingEditor.autoSubmit');
                }
            });
    }

    private subscribeToNewResultsWebsocket(): void {
        if (!this.participation()?.id) {
            return;
        }

        const resultStream$ = this.participationWebsocketService.subscribeForLatestResultOfParticipation(this.participation().id!, true);

        this.manualResultUpdateListener = resultStream$
            .pipe(
                filter((result): result is Result => !!result),
                filter((result) => !result.assessmentType || result.assessmentType !== AssessmentType.AUTOMATIC_ATHENA),
            )
            .subscribe(this.handleManualAssessment.bind(this));

        this.athenaResultUpdateListener = resultStream$
            .pipe(
                skip(1),
                filter((result): result is Result => !!result),
                filter((result) => result.assessmentType === AssessmentType.AUTOMATIC_ATHENA),
            )
            .subscribe(this.handleAthenaAssessment.bind(this));
    }

    private handleManualAssessment(result: Result): void {
        if (!result.completionDate) {
            return;
        }
        this.assessmentResult.set(this.modelingAssessmentService.convertResult(result));
        this.prepareAssessmentData();
        this.alertService.info('artemisApp.modelingEditor.newAssessment');
    }

    private handleAthenaAssessment(result: Result): void {
        if (result.completionDate) {
            this.assessmentResult.set(this.modelingAssessmentService.convertResult(result));
            this.result.set(this.assessmentResult());
            this.prepareAssessmentData();

            if (result.successful) {
                this.alertService.success('artemisApp.exercise.athenaFeedbackSuccessful', { title: this.modelingExercise()?.title ?? '' });
            }
        } else if (result.successful === false) {
            this.alertService.error('artemisApp.exercise.athenaFeedbackFailed');
        }
    }

    private setAutoSaveTimer(): void {
        clearInterval(this.autoSaveInterval);
        this.autoSaveTimer.set(0);
        this.autoSaveInterval = window.setInterval(() => {
            this.autoSaveTimer.update((timer) => timer + 1);
            this.isChanged.set(!this.canDeactivate());
            if (this.autoSaveTimer() >= AUTOSAVE_EXERCISE_INTERVAL && this.isChanged()) {
                this.saveDiagram();
            }
        }, AUTOSAVE_CHECK_INTERVAL);
    }

    private setupSubmissionStreamForTeam(): void {
        clearInterval(this.teamSyncInterval);
        const teamSyncInterval = window.setInterval(() => {
            this.isChanged.set(!this.canDeactivate());
            if (this.isChanged()) {
                this.updateSubmissionWithCurrentValues();
                this.submissionChange.next(this.submission());
            }
        }, AUTOSAVE_TEAM_EXERCISE_INTERVAL);
        this.teamSyncInterval = teamSyncInterval;

        this.cleanup(() => clearInterval(teamSyncInterval));
    }

    onModelPatch(patch: string) {
        if (this.modelingExercise().teamMode) {
            const submissionPatch = new SubmissionPatch(patch);
            submissionPatch.participation = this.participation();
            if (submissionPatch.participation?.exercise) {
                submissionPatch.participation.exercise.studentParticipations = [];
            }
            this.submissionPatchObservable.next(deepClone(submissionPatch));
        }
    }

    private cleanup(teardown: TeardownLogic) {
        this.subscription ??= new Subscription();
        this.subscription.add(teardown);
    }

    saveDiagram(): void {
        if (this.isSaving()) {
            return;
        }
        this.updateSubmissionWithCurrentValues();
        this.isSaving.set(true);
        this.autoSaveTimer.set(0);

        if (this.submission().id) {
            this.modelingSubmissionService.update(this.submission(), this.modelingExercise().id!).subscribe({
                next: (response) => {
                    this.submission.set(response.body!);
                    this.submission().participation!.submissions = [this.submission()];
                    this.participationWebsocketService.addParticipation(this.submission().participation as StudentParticipation, this.modelingExercise());
                    this.result.set(getLatestSubmissionResult(this.submission()));
                    this.onSaveSuccess();
                },
                error: () => this.onSaveError(),
            });
        } else {
            this.modelingSubmissionService.create(this.submission(), this.modelingExercise().id!).subscribe({
                next: (submission) => {
                    this.submission.set(submission.body!);
                    this.result.set(getLatestSubmissionResult(this.submission()));
                    this.subscribeToAutomaticSubmissionWebsocket();
                    this.onSaveSuccess();
                },
                error: () => this.onSaveError(),
            });
        }
    }

    submitExercise(): void {
        if (this.isSaving()) {
            return;
        }
        this.updateSubmissionWithCurrentValues();
        if (this.isModelEmpty(this.submission().model)) {
            this.alertService.warning('artemisApp.modelingEditor.empty');
            return;
        }
        this.isSaving.set(true);
        this.autoSaveTimer.set(0);
        if (this.submission().id) {
            this.modelingSubmissionService.update(this.submission(), this.modelingExercise().id!).subscribe({
                next: (response) => {
                    this.submission.set(response.body!);
                    this.refreshNonCollaborativeEditorFromSavedSubmission();
                    this.submissionChange.next(this.submission());
                    this.participation.set(this.submission().participation as StudentParticipation);
                    this.participation().exercise = this.modelingExercise();
                    this.submission().participation!.submissions = [this.submission()];
                    this.participationWebsocketService.addParticipation(this.participation(), this.modelingExercise());
                    this.modelingExercise().studentParticipations = [this.participation()];
                    this.result.set(getLatestSubmissionResult(this.submission()));
                    this.retryStarted.set(false);

                    if (this.isLate()) {
                        this.alertService.warning('entity.action.submitDueDateMissedAlert');
                    } else {
                        this.alertService.success('entity.action.submitSuccessfulAlert');
                    }

                    this.subscribeToWebsockets();
                    this.automaticSubmissionSubscription?.unsubscribe();
                    this.onSaveSuccess();
                },
                error: () => this.onSaveError(),
            });
        } else {
            this.modelingSubmissionService.create(this.submission(), this.modelingExercise().id!).subscribe({
                next: (response) => {
                    this.submission.set(response.body!);
                    this.submissionChange.next(this.submission());
                    this.participation.set(this.submission().participation as StudentParticipation);
                    this.participation().exercise = this.modelingExercise();
                    this.modelingExercise().studentParticipations = [this.participation()];
                    this.result.set(getLatestSubmissionResult(this.submission()));
                    if (this.isLate()) {
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
        this.isSaving.set(false);
        this.isChanged.set(!this.canDeactivate());
    }

    private onSaveError() {
        this.alertService.error('artemisApp.modelingEditor.error');
        this.isSaving.set(false);
    }

    onReceiveSubmissionPatchFromTeam(submissionPatch: SubmissionPatch) {
        this.modelingEditor()?.importPatch(submissionPatch.patch);
    }

    onTeamSyncReconnected() {
        this.modelingEditor()?.resynchronizeCollaborationAfterReconnect();
    }

    private isModelEmpty(model?: string): boolean {
        if (!model) {
            return true;
        }
        const umlModel = parseJson<ApollonModelData>(model);
        return isApollonModelEmpty(umlModel);
    }

    ngOnDestroy(): void {
        this.subscription?.unsubscribe();
        clearInterval(this.autoSaveInterval);

        this.automaticSubmissionSubscription?.unsubscribe();
        this.manualResultUpdateListener?.unsubscribe();
        this.athenaResultUpdateListener?.unsubscribe();
    }

    get unreferencedFeedback(): Feedback[] | undefined {
        const feedbacks = this.assessmentResult()?.feedbacks;
        if (feedbacks) {
            checkSubsequentFeedbackInAssessment(feedbacks);
            return getUnreferencedFeedback(feedbacks);
        }
        return undefined;
    }

    get referencedFeedback(): Feedback[] | undefined {
        const feedbacks = this.assessmentResult()?.feedbacks;
        if (feedbacks) {
            checkSubsequentFeedbackInAssessment(feedbacks);
            return feedbacks.filter((feedbackElement) => feedbackElement.reference != undefined);
        }
        return undefined;
    }

    get isAutomaticResult(): boolean {
        return this.result()?.assessmentType === AssessmentType.AUTOMATIC_ATHENA;
    }

    get hasAthenaResultForLatestSubmission(): boolean {
        const latestResult = getLatestSubmissionResult(this.submission());
        return latestResult?.assessmentType === AssessmentType.AUTOMATIC_ATHENA;
    }

    updateSubmissionWithCurrentValues(): void {
        if (!this.submission()) {
            this.submission.set(new ModelingSubmission());
        }
        this.submission().explanationText = this.explanation;
        const modelingEditor = this.modelingEditor();
        if (!modelingEditor || !modelingEditor.getCurrentModel()) {
            return;
        }
        const umlModel = modelingEditor.getCurrentModel();
        this.hasElements.set(hasModelElements(umlModel));
        const diagramJson = JSON.stringify(umlModel);
        if (this.submission() && diagramJson) {
            this.submission().model = diagramJson;
        }
    }

    private prepareAssessmentData(): void {
        this.initializeAssessmentInfo();
    }

    private initializeAssessmentInfo(): void {
        const assessmentResult = this.assessmentResult();
        if (assessmentResult?.feedbacks && this.umlModel()) {
            this.assessmentsNames.set(getNamesForAssessments(assessmentResult, this.umlModel()));
            let totalScore = 0;
            for (const feedback of assessmentResult.feedbacks) {
                totalScore += feedback.credits!;
            }
            this.totalScore = totalScore;
        }
    }

    onSelectedElementIdsChanged(selectedElementIds: string[]) {
        this.selectedElementIds.set(selectedElementIds);
    }

    isFeedbackForSelection(feedback: Feedback): boolean {
        const selected = this.selectedElementIds();
        return selected.length > 0 && !!feedback.referenceId && selected.includes(feedback.referenceId);
    }

    previewFeedbackTarget(feedback: Feedback): void {
        this.previewedFeedbackReferenceId.set(feedback.referenceId);
    }

    clearFeedbackPreview(): void {
        this.previewedFeedbackReferenceId.set(undefined);
    }

    showFeedbackOnDiagram(feedback: Feedback): void {
        if (!feedback.referenceId) {
            return;
        }
        this.modelingAssessment()?.revealAssessment(feedback.referenceId);
    }

    canDeactivate(): boolean {
        const modelingEditor = this.modelingEditor();
        if (!modelingEditor || !modelingEditor.isApollonEditorMounted) {
            return true;
        }
        const model: UMLModel = modelingEditor.getCurrentModel();
        const explanationIsUpToDate = this.explanation === (this.submission().explanationText ?? '');
        return !this.modelHasUnsavedChanges(model) && explanationIsUpToDate;
    }

    unloadNotification(event: BeforeUnloadEvent) {
        if (!this.canDeactivate()) {
            event.preventDefault();
            return this.translateService.instant('pendingChanges');
        }
        return true;
    }

    private modelHasUnsavedChanges(model: UMLModel): boolean {
        const submissionModel = this.submission()?.model;
        if (!submissionModel) {
            return model.nodes.length > 0 && JSON.stringify(model) !== '';
        } else {
            const currentModel = parseJson<ApollonModelData>(submissionModel);
            const versionMatch = currentModel.version === model.version;
            const modelMatch = stringifyIgnoringFields(currentModel, 'size') === stringifyIgnoringFields(model, 'size');
            return versionMatch && !modelMatch;
        }
    }

    calculateNumberOfModelElements(): number {
        const submissionModel = this.submission()?.model;
        if (submissionModel) {
            const umlModel = parseJson<ApollonModelData>(submissionModel);
            return countModelElements(umlModel);
        }
        return 0;
    }

    get isActive(): boolean {
        return this.modelingExercise() && !this.examMode() && (!hasExerciseDueDatePassed(this.modelingExercise(), this.participation()) || !!this.participation()?.testRun);
    }

    protected readonly hasExerciseDueDatePassed = hasExerciseDueDatePassed;
}
