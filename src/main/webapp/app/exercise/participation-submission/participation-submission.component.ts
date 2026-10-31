import { Component, DestroyRef, OnInit, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { Subject, Subscription, combineLatest, of } from 'rxjs';
import { catchError, map, startWith, switchMap, take } from 'rxjs/operators';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import dayjs from 'dayjs/esm';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { TranslateService } from '@ngx-translate/core';
import { ButtonSize } from 'app/shared-ui/components/buttons/button/button.component';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { FileUploadAssessmentService } from 'app/fileupload/manage/assess/file-upload-assessment.service';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/programming/manage/assess/manual-result/programming-assessment-manual-result.service';
import { QuizAssessmentApi } from 'app/openapi/api/quiz-assessment-api';
import { HttpErrorResponse } from '@angular/common/http';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercise/util/exercise.utils';
import { faTrash } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ResultComponent } from '../result/result.component';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { CellTemplateRef, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared-ui/table-view/table-view';
import { AlertService } from 'app/foundation/service/alert.service';
import { ProgrammingSubmissionService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/programming/shared/services/programming-submission.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { AccountService } from 'app/core/auth/account.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/instructor/programming-exercise-instructor-trigger-build-button.component';

@Component({
    selector: 'jhi-participation-submission',
    templateUrl: './participation-submission.component.html',
    imports: [
        TranslateDirective,
        TableViewComponent,
        ResultComponent,
        DeleteButtonDirective,
        FaIconComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
        ProgrammingExerciseInstructorTriggerBuildButtonComponent,
    ],
})
export class ParticipationSubmissionComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private submissionService = inject(SubmissionService);
    private translateService = inject(TranslateService);
    private participationService = inject(ParticipationService);
    private exerciseService = inject(ExerciseService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private fileUploadAssessmentService = inject(FileUploadAssessmentService);
    private modelingAssessmentsService = inject(ModelingAssessmentService);
    private textAssessmentService = inject(TextAssessmentService);
    private programmingAssessmentService = inject(ProgrammingAssessmentManualResultService);
    private quizAssessmentApi = inject(QuizAssessmentApi);
    private eventManager = inject(EventManager);
    private alertService = inject(AlertService);
    private programmingSubmissionService = inject(ProgrammingSubmissionService);
    private profileService = inject(ProfileService);
    private destroyRef = inject(DestroyRef);
    private accountService = inject(AccountService);

    readonly ParticipationType = ParticipationType;
    readonly buttonSizeSmall = ButtonSize.SMALL;
    readonly actionTypeEmpty = ActionType.NoButtonTextDelete;

    // These two variables are used to emit errors to the delete dialog
    protected dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    private _participationId = 0;

    readonly exerciseStatusBadge = signal('bg-success');
    readonly isTmpOrSolutionProgrParticipation = signal(false);
    readonly exercise = signal<Exercise | undefined>(undefined);
    readonly programmingExercise = computed<ProgrammingExercise | undefined>(() => {
        const exercise = this.exercise();
        return exercise?.type === ExerciseType.PROGRAMMING ? exercise : undefined;
    });
    readonly participation = signal<Participation | undefined>(undefined);
    readonly dueDate = signal<dayjs.Dayjs | undefined>(undefined);
    readonly submissions = signal<Submission[]>([]);
    readonly isLoading = signal(true);
    readonly resultIdToBuildJobIdMap = signal<{ [key: string]: string } | undefined>(undefined);

    private readonly buildState = signal<ProgrammingSubmissionStateObj | undefined>(undefined);
    private readonly liveSubmissions = signal<ProgrammingSubmission[]>([]);
    private readonly deletedSubmissionIds = signal<ReadonlySet<number>>(new Set());
    private readonly buildParticipationId = computed(() => this.participation()?.id);
    private readonly programmingExerciseId = computed(() => (this.exercise()?.type === ExerciseType.PROGRAMMING ? this.exercise()?.id : undefined));
    readonly showBuildProgressBar = this.profileService.isProfileActive(PROFILE_LOCALCI);
    readonly ProgrammingSubmissionState = ProgrammingSubmissionState;
    readonly activeBuild = computed(() => {
        const state = this.buildState();
        return state?.submissionState === ProgrammingSubmissionState.IS_QUEUED || state?.submissionState === ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION
            ? state
            : undefined;
    });

    readonly displayedSubmissions = computed(() => {
        const submissions = this.submissions();
        const loadedIds = new Set(submissions.map((submission) => submission.id));
        const deletedIds = this.deletedSubmissionIds();
        // Retain every received submission until participation changes; loaded rows supply authoritative results.
        // The table sorts its input in place. Keep both source arrays untouched.
        return [...this.liveSubmissions().filter((submission) => !loadedIds.has(submission.id)), ...submissions].filter(
            (submission) => submission.id === undefined || !deletedIds.has(submission.id),
        );
    });

    eventSubscriber?: Subscription;

    readonly faTrash = faTrash;

    // Template refs for cell renderers
    readonly submissionDateTemplate = viewChild<CellTemplateRef<Submission>>('submissionDateTemplate');
    readonly resultCountTemplate = viewChild<CellTemplateRef<Submission>>('resultCountTemplate');
    readonly resultsTemplate = viewChild<CellTemplateRef<Submission>>('resultsTemplate');
    readonly commitHashTemplate = viewChild<CellTemplateRef<Submission>>('commitHashTemplate');
    readonly assessmentTypeTemplate = viewChild<CellTemplateRef<Submission>>('assessmentTypeTemplate');
    readonly assessorTemplate = viewChild<CellTemplateRef<Submission>>('assessorTemplate');
    readonly completionDateTemplate = viewChild<CellTemplateRef<Submission>>('completionDateTemplate');

    readonly tableOptions: TableViewOptions = {
        lazy: false,
        striped: true,
        // paginated: false,
        showSearch: false,
        initialSortField: 'submissionDate',
        initialSortOrder: -1,
    };

    readonly columns = computed<ColumnDef<Submission>[]>(() => {
        const isTmpOrSolution = this.isTmpOrSolutionProgrParticipation();
        const participation = this.participation();

        const cols: ColumnDef<Submission>[] = [
            { headerKey: 'artemisApp.participation.participationSubmission.submissionType', field: 'type', width: '90px' },
            {
                headerKey: 'artemisApp.participation.participationSubmission.submissionDate',
                field: 'submissionDate',
                sort: true,
                sortComparator: (rowA, rowB) => (rowA.submissionDate ? dayjs(rowA.submissionDate).valueOf() : 0) - (rowB.submissionDate ? dayjs(rowB.submissionDate).valueOf() : 0),
                width: '180px',
                templateRef: this.submissionDateTemplate(),
            },
            {
                headerIcon: 'pi pi-hashtag',
                headerTooltip: 'artemisApp.participation.participationSubmission.resultCount',
                field: 'results',
                width: '50px',
                templateRef: this.resultCountTemplate(),
            },
            {
                headerKey: 'artemisApp.participation.participationSubmission.results',
                field: 'results',
                width: '300px',
                templateRef: this.resultsTemplate(),
            },
        ];

        if (this.accountService.isAdmin()) {
            cols.unshift({ header: 'Id', field: 'id' });
        }

        if (isTmpOrSolution || participation?.type === ParticipationType.PROGRAMMING) {
            cols.push({
                headerKey: 'artemisApp.programmingSubmission.commitHash',
                templateRef: this.commitHashTemplate(),
            });
        }

        cols.push(
            {
                headerKey: 'artemisApp.result.assessmentType',
                field: 'results',
                templateRef: this.assessmentTypeTemplate(),
            },
            {
                headerKey: 'artemisApp.participation.participationSubmission.assessor',
                field: 'results',
                templateRef: this.assessorTemplate(),
            },
            {
                headerKey: 'artemisApp.exercise.completionDate',
                // A distinct sort key avoids the other columns that also render the results collection.
                field: 'completionDate',
                sort: true,
                sortComparator: (rowA, rowB) => this.latestCompletionTimestamp(rowA) - this.latestCompletionTimestamp(rowB),
                width: '180px',
                templateRef: this.completionDateTemplate(),
            },
        );

        return cols;
    });

    private latestCompletionTimestamp(submission: Submission): number {
        return (
            submission.results?.reduce((latest, result) => {
                // This endpoint can leave nested result dates as ISO strings; other loaders convert them to Dayjs.
                const timestamp = result.completionDate ? dayjs(result.completionDate).valueOf() : 0;
                return Number.isFinite(timestamp) ? Math.max(latest, timestamp) : latest;
            }, 0) ?? 0
        );
    }

    constructor() {
        this.destroyRef.onDestroy(() => this.eventSubscriber?.unsubscribe());
        effect((onCleanup) => {
            const participationId = this.buildParticipationId();
            const exerciseId = this.programmingExerciseId();
            this.buildState.set(undefined);
            this.liveSubmissions.set([]);
            this.deletedSubmissionIds.set(new Set());
            if (participationId === undefined || exerciseId === undefined) {
                return;
            }

            // Reloads replace the participation object; subscribe only when its id or the programming exercise changes.
            untracked(() => {
                const subscription = this.programmingSubmissionService
                    .getLatestPendingSubmissionByParticipationId(participationId, exerciseId, false)
                    .pipe(
                        switchMap((state) => {
                            if (this.showBuildProgressBar && state.submissionState === ProgrammingSubmissionState.IS_QUEUED) {
                                return this.programmingSubmissionService.fetchQueueReleaseDateEstimationByParticipationId(participationId).pipe(
                                    map((estimatedCompletionDate) =>
                                        cloneWith(state, { buildTimingInfo: { buildStartDate: state.submission?.submissionDate, estimatedCompletionDate } }),
                                    ),
                                    startWith(state),
                                    catchError(() => of(state)),
                                );
                            }
                            return of(state);
                        }),
                    )
                    .subscribe((state) => {
                        const hadPendingSubmission = this.buildState()?.submission !== undefined;
                        this.buildState.set(state);
                        const liveSubmission = state.submission;
                        if (liveSubmission?.id !== undefined && !this.deletedSubmissionIds().has(liveSubmission.id)) {
                            this.liveSubmissions.update((submissions) => [liveSubmission, ...submissions.filter((submission) => submission.id !== liveSubmission.id)]);
                        }
                        // The shared service clears pending state only after receiving the matching result.
                        if (hadPendingSubmission && state.submissionState === ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION) {
                            this.setupPage();
                        }
                    });
                onCleanup(() => {
                    subscription.unsubscribe();
                    this.programmingSubmissionService.unsubscribeForLatestSubmissionOfParticipation(participationId);
                });
            });
        });
    }

    /**
     * Initialize component by setting up page and subscribe to eventManager
     */
    ngOnInit() {
        this.setupPage();
        this.eventSubscriber = this.eventManager.subscribe('submissionsModification', () => this.setupPage());
    }

    /**
     * Set up page by loading participation and all submissions
     */
    setupPage() {
        this.isLoading.set(true);

        // If no query parameters are set, this.route.queryParams will be undefined so we need a fallback dummy observable
        combineLatest([this.route.params, this.route.queryParams ?? of(undefined)])
            .pipe(take(1))
            .subscribe(([params, queryParams]) => {
                this._participationId = +params['participationId'];
                if (queryParams?.['isTmpOrSolutionProgrParticipation'] != undefined) {
                    this.isTmpOrSolutionProgrParticipation.set(queryParams['isTmpOrSolutionProgrParticipation'] === 'true');
                }
                this.participationService.getBuildJobIdsForResultsOfParticipation(this._participationId).subscribe((buildJobIdMap) => {
                    this.resultIdToBuildJobIdMap.set(buildJobIdMap);
                    if (this.isTmpOrSolutionProgrParticipation()) {
                        // Find programming exercise of template and solution programming participation
                        this.programmingExerciseService.findWithTemplateAndSolutionParticipation(params['exerciseId'], true).subscribe((exerciseResponse) => {
                            const exercise = exerciseResponse.body!;
                            this.exercise.set(exercise);
                            this.exerciseStatusBadge.set(dayjs().isAfter(dayjs(exercise.dueDate)) ? 'bg-danger' : 'bg-success');
                            const templateParticipation = exercise.templateParticipation;
                            const solutionParticipation = exercise.solutionParticipation;

                            let submissions: ProgrammingSubmission[] | undefined;
                            // Check if requested participationId belongs to the template or solution participation
                            if (this._participationId === templateParticipation?.id) {
                                this.participation.set(templateParticipation);
                                // This is needed to access the exercise in the result details
                                templateParticipation.programmingExercise = exercise;
                                submissions = templateParticipation.submissions;
                            } else if (this._participationId === solutionParticipation?.id) {
                                this.participation.set(solutionParticipation);
                                // This is needed to access the exercise in the result details
                                solutionParticipation.programmingExercise = exercise;
                                submissions = solutionParticipation.submissions;
                            } else {
                                // Should not happen
                                this.alertService.error('artemisApp.participation.noParticipation');
                            }

                            if (submissions) {
                                submissions.forEach((submission: ProgrammingSubmission) => {
                                    submission.results?.forEach((result: Result) => {
                                        result.submission = submission;
                                        result.buildJobId = buildJobIdMap?.[result.id!];
                                    });
                                });
                                this.submissions.set(submissions);
                            }

                            this.isLoading.set(false);
                        });
                    } else {
                        // Get exercise for release and due dates
                        (params['examId'] ? this.exerciseService.findForExamManagement(params['exerciseId']) : this.exerciseService.findForStaff(params['exerciseId'])).subscribe(
                            (exerciseResponse) => {
                                this.exercise.set(exerciseResponse.body!);
                                this.updateStatusBadgeColor();
                            },
                        );
                        this.fetchParticipationAndSubmissionsForStudent();
                    }
                });
            });
    }

    fetchParticipationAndSubmissionsForStudent() {
        combineLatest([this.participationService.find(this._participationId), this.submissionService.findAllSubmissionsOfParticipation(this._participationId)])
            .pipe(
                map((res) => [res[0].body, res[1].body]),
                catchError(() => of(null)),
            )
            .subscribe((response) => {
                this.isLoading.set(false);
                if (!response) {
                    return;
                }

                const studentParticipation = response[0] as StudentParticipation;
                const submissions = response[1] as Submission[];
                if (studentParticipation) {
                    this.participation.set(studentParticipation);
                    this.updateStatusBadgeColor();
                }

                if (submissions) {
                    const buildJobIdMap = this.resultIdToBuildJobIdMap();
                    // set the submission to every result so it can be accessed via the result
                    // set the build log availability for every result
                    submissions.forEach((submission: Submission) => {
                        submission.results?.forEach((result: Result) => {
                            result.submission = submission;
                            result.buildJobId = buildJobIdMap?.[result.id!];
                        });
                    });
                    this.submissions.set(submissions);
                    const currentParticipation = this.participation();
                    if (currentParticipation) {
                        currentParticipation.submissions = submissions;
                    }
                }
            });
    }

    getName() {
        const participation = this.participation();
        if (participation?.type === ParticipationType.STUDENT || participation?.type === ParticipationType.PROGRAMMING) {
            return (participation as StudentParticipation).student?.name || (participation as StudentParticipation).team?.name;
        } else if (participation?.type === ParticipationType.SOLUTION) {
            return this.translateService.instant('artemisApp.participation.solutionParticipation');
        } else if (participation?.type === ParticipationType.TEMPLATE) {
            return this.translateService.instant('artemisApp.participation.templateParticipation');
        }
        return 'N/A';
    }

    private updateStatusBadgeColor() {
        const exercise = this.exercise();
        const participation = this.participation();
        let afterDueDate = false;

        if (exercise && participation) {
            this.dueDate.set(getExerciseDueDate(exercise, participation));
            afterDueDate = hasExerciseDueDatePassed(exercise, participation);
        } else if (exercise) {
            afterDueDate = dayjs().isAfter(dayjs(exercise.dueDate));
        }

        this.exerciseStatusBadge.set(afterDueDate ? 'bg-danger' : 'bg-success');
    }

    /**
     * Delete a submission from the server
     * @param submissionId - Id of submission that is deleted.
     */
    deleteSubmission(submissionId: number) {
        this.submissionService.delete(submissionId).subscribe({
            next: () => {
                this.deletedSubmissionIds.update((ids) => new Set([...ids, submissionId]));
                this.liveSubmissions.update((submissions) => submissions.filter((submission) => submission.id !== submissionId));
                this.eventManager.broadcast({
                    name: 'submissionsModification',
                    content: 'Deleted a submission',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    deleteResult(submission: Submission, result: Result) {
        const exercise = this.exercise();
        if (exercise && submission.id && result.id && this._participationId) {
            switch (exercise.type) {
                case ExerciseType.TEXT:
                    this.textAssessmentService.deleteAssessment(this._participationId, submission.id, result.id).subscribe({
                        next: () => this.updateResults(submission, result),
                        error: (error: HttpErrorResponse) => this.handleErrorResponse(error),
                    });
                    break;
                case ExerciseType.MODELING:
                    this.modelingAssessmentsService.deleteAssessment(this._participationId, submission.id, result.id).subscribe({
                        next: () => this.updateResults(submission, result),
                        error: (error: HttpErrorResponse) => this.handleErrorResponse(error),
                    });
                    break;
                case ExerciseType.FILE_UPLOAD:
                    this.fileUploadAssessmentService.deleteAssessment(this._participationId, submission.id, result.id).subscribe({
                        next: () => this.updateResults(submission, result),
                        error: (error: HttpErrorResponse) => this.handleErrorResponse(error),
                    });
                    break;
                case ExerciseType.PROGRAMMING:
                    this.programmingAssessmentService.deleteAssessment(this._participationId, submission.id, result.id).subscribe({
                        next: () => this.updateResults(submission, result),
                        error: (error: HttpErrorResponse) => this.handleErrorResponse(error),
                    });
                    break;
                case ExerciseType.QUIZ:
                    this.quizAssessmentApi.deleteAssessment(this._participationId, submission.id, result.id).subscribe({
                        next: () => this.updateResults(submission, result),
                        error: (error: HttpErrorResponse) => this.handleErrorResponse(error),
                    });
                    break;
            }
        }
    }

    viewBuildLogs(buildJobId: string): void {
        const url = `/api/localci/build-jobs/${buildJobId}/build-log`;
        window.open(url, '_blank');
    }

    private updateResults(submission: Submission, result: Result) {
        submission.results = submission.results?.filter((remainingResult) => remainingResult.id !== result.id);
        // Re-set the signal to trigger view update
        this.submissions.update((subs) => [...subs]);
        this.dialogErrorSource.next('');
    }

    private handleErrorResponse(error: HttpErrorResponse) {
        if (error.error?.message === 'error.hasComplaint') {
            this.dialogErrorSource.next(this.translateService.instant('artemisApp.result.delete.error.hasComplaint'));
        } else {
            this.dialogErrorSource.next(error.message);
        }
    }
}
