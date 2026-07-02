import { Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, combineLatest } from 'rxjs';
import { filter, skip } from 'rxjs/operators';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import dayjs from 'dayjs/esm';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ExampleSolutionInfo, ExerciseDetailsType, ExerciseService } from 'app/exercise/services/exercise.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { hasExerciseDueDatePassed } from 'app/exercise/util/exercise.utils';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { TeamAssignmentPayload } from 'app/exercise/shared/entities/team/team.model';
import { TeamService } from 'app/exercise/team/team.service';
import { LiveQuizParticipationStatus, QuizExercise, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { Submission, getAllResultsOfAllSubmissions, getFirstResultWithComplaintFromResults } from 'app/exercise/shared/entities/submission/submission.model';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { Complaint } from 'app/assessment/shared/entities/complaint.model';
import { SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { IconDefinition, faAngleDown, faAngleUp, faBook, faEye, faFileSignature, faListAlt, faSignal, faTable, faWrench } from '@fortawesome/free-solid-svg-icons';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { PlagiarismCaseInfo } from 'app/plagiarism/shared/entities/PlagiarismCaseInfo';
import { isCommunicationEnabled, isMessagingEnabled } from 'app/course/shared/entities/course.model';
import { ExerciseCacheService } from 'app/exercise/services/exercise-cache.service';
import { ScienceEventType } from 'app/foundation/science/science.model';
import { MODULE_FEATURE_ATHENA, MODULE_FEATURE_IRIS } from 'app/app.constants';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ExerciseHeaderComponent } from 'app/exercise/exercise-headers/exercise-header/exercise-header.component';
import { ScienceService } from 'app/foundation/science/science.service';
import { hasResults } from 'app/exercise/participation/participation.utils';
import { ExerciseSplitPanelComponent } from './exercise-split-panel/exercise-split-panel.component';
import { ParticipationMode } from 'app/exercise/exercise-headers/participation-mode-toggle/participation-mode-toggle.component';

interface InstructorActionItem {
    routerLink: string;
    icon?: IconDefinition;
    translation: string;
}
@Component({
    selector: 'jhi-course-exercise-details',
    templateUrl: './course-exercise-details.component.html',
    styleUrls: ['../course-overview/course-overview.scss', './course-exercise-details.component.scss'],
    providers: [ExerciseCacheService],
    imports: [ExerciseHeaderComponent, ExerciseSplitPanelComponent],
})
export class CourseExerciseDetailsComponent implements OnInit, OnDestroy {
    private exerciseService = inject(ExerciseService);
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private participationService = inject(ParticipationService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private profileService = inject(ProfileService);
    private alertService = inject(AlertService);
    private teamService = inject(TeamService);
    private quizExerciseService = inject(QuizExerciseService);
    private complaintService = inject(ComplaintService);
    private artemisMarkdown = inject(ArtemisMarkdownService);
    private readonly scienceService = inject(ScienceService);
    private irisSettingsService = inject(IrisSettingsService);
    private destroyRef = inject(DestroyRef);
    private courseStorageService = inject(CourseStorageService);

    protected readonly splitPanel = viewChild(ExerciseSplitPanelComponent);

    protected readonly submitExercise = () => this.splitPanel()?.submitExercise();
    protected readonly restartPractice = () => this.splitPanel()?.restartPractice() ?? false;
    protected readonly isSidebarCollapsed = signal(false);
    private readonly sidebarToggle = signal<(() => void) | undefined>(undefined);
    protected readonly showSidebarToggle = computed(() => !!this.sidebarToggle());
    protected readonly toggleSidebar = () => this.sidebarToggle()?.();

    readonly athenaEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATHENA);

    readonly AssessmentType = AssessmentType;
    readonly PlagiarismVerdict = PlagiarismVerdict;
    readonly QuizStatus = QuizStatus;
    readonly QUIZ_ENDED_STATUS: (QuizStatus | undefined)[] = [QuizStatus.OPEN_FOR_PRACTICE];
    readonly QUIZ_EDITABLE_STATUS: (QuizStatus | undefined)[] = [QuizStatus.VISIBLE, QuizStatus.INVISIBLE];
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;
    readonly TEXT = ExerciseType.TEXT;
    readonly FILE_UPLOAD = ExerciseType.FILE_UPLOAD;
    readonly MATH = ExerciseType.MATH;
    readonly dayjs = dayjs;
    readonly ChatServiceMode = ChatServiceMode;

    readonly isCommunicationEnabled = isCommunicationEnabled;
    readonly isMessagingEnabled = isMessagingEnabled;

    // Use signals for reactive state
    public learningPathMode = false;
    public exerciseId: number;

    // courseId is template-bound and written asynchronously (inside the route subscription), so it is backed by a
    // signal to schedule change detection. The public getter/setter preserves external assignment by the learning path parent.
    // The backing signal is honestly typed as number | undefined (its construction-time value is genuinely undefined);
    // the getter narrows with a single non-null assertion because courseId is always assigned before it is ever read.
    private readonly _courseId = signal<number | undefined>(undefined);
    public get courseId(): number {
        return this._courseId()!;
    }
    public set courseId(value: number) {
        this._courseId.set(value);
    }

    // Main exercise signal
    private readonly _exercise = signal<Exercise | undefined>(undefined);
    public get exercise(): Exercise | undefined {
        return this._exercise();
    }
    public set exercise(value: Exercise | undefined) {
        this._exercise.set(value);
    }

    // Student participations signal
    private readonly _studentParticipations = signal<StudentParticipation[]>([]);
    public get studentParticipations(): StudentParticipation[] {
        return this._studentParticipations();
    }
    public set studentParticipations(value: StudentParticipation[]) {
        this._studentParticipations.set(value);
    }

    // Computed signals for derived state
    readonly gradedStudentParticipation = computed(() => {
        return this.participationService.getSpecificStudentParticipation(this._studentParticipations(), false);
    });

    readonly practiceStudentParticipation = computed(() => {
        return this.participationService.getSpecificStudentParticipation(this._studentParticipations(), true);
    });

    readonly numberOfPracticeResults = computed(() => {
        return this.practiceStudentParticipation()?.submissions?.flatMap((submission) => submission.results)?.length ?? 0;
    });

    readonly participationMode = signal<ParticipationMode>('graded');

    // Display-only override for the quiz participation status badge, set from the live quiz view.
    readonly liveQuizStatus = signal<LiveQuizParticipationStatus | undefined>(undefined);

    readonly activeParticipation = computed(() => {
        return this.participationMode() === 'practice' ? (this.practiceStudentParticipation() ?? this.gradedStudentParticipation()) : this.gradedStudentParticipation();
    });

    setSidebarToggle(isCollapsed: boolean, toggleSidebar: () => void): void {
        this.isSidebarCollapsed.set(isCollapsed);
        this.sidebarToggle.set(toggleSidebar);
    }

    // Sorted results signal
    private readonly _sortedHistoryResults = signal<Result[]>([]);
    public get sortedHistoryResults(): Result[] {
        return this._sortedHistoryResults();
    }
    public set sortedHistoryResults(value: Result[]) {
        this._sortedHistoryResults.set(value);
    }

    // Read-only signal accessors for state only modified internally
    private readonly _resultWithComplaint = signal<Result | undefined>(undefined);
    readonly resultWithComplaint = this._resultWithComplaint.asReadonly();

    private readonly _latestRatedResult = signal<Result | undefined>(undefined);
    readonly latestRatedResult = this._latestRatedResult.asReadonly();

    private readonly _complaint = signal<Complaint | undefined>(undefined);
    readonly complaint = this._complaint.asReadonly();

    private readonly _showMoreResults = signal(false);
    readonly showMoreResults = this._showMoreResults.asReadonly();

    private readonly _resultsOfGradedStudentParticipation = signal<(Result | undefined)[]>([]);
    readonly resultsOfGradedStudentParticipation = this._resultsOfGradedStudentParticipation.asReadonly();

    private readonly _isAfterAssessmentDueDate = signal(false);
    readonly isAfterAssessmentDueDate = this._isAfterAssessmentDueDate.asReadonly();

    private readonly _allowComplaintsForAutomaticAssessments = signal(false);
    readonly allowComplaintsForAutomaticAssessments = this._allowComplaintsForAutomaticAssessments.asReadonly();

    private readonly _baseResource = signal('');
    readonly baseResource = this._baseResource.asReadonly();

    private readonly _submissionPolicy = signal<SubmissionPolicy | undefined>(undefined);
    readonly submissionPolicy = this._submissionPolicy.asReadonly();

    private readonly _plagiarismCaseInfo = signal<PlagiarismCaseInfo | undefined>(undefined);
    readonly plagiarismCaseInfo = this._plagiarismCaseInfo.asReadonly();

    private readonly _irisEnabled = signal(false);
    readonly irisEnabled = this._irisEnabled.asReadonly();

    private readonly _irisChatEnabled = signal(false);
    readonly irisChatEnabled = this._irisChatEnabled.asReadonly();

    private readonly _instructorActionItems = signal<InstructorActionItem[]>([]);
    readonly instructorActionItems = this._instructorActionItems.asReadonly();

    private readonly _exerciseIcon = signal<IconProp | undefined>(undefined);
    readonly exerciseIcon = this._exerciseIcon.asReadonly();

    private readonly _exampleSolutionInfo = signal<ExampleSolutionInfo | undefined>(undefined);
    readonly exampleSolutionInfo = this._exampleSolutionInfo.asReadonly();

    // Subscription tracking for methods called on each exercise load
    private participationUpdateListener?: Subscription;
    private teamAssignmentUpdateListener?: Subscription;

    // Icons
    faBook = faBook;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faListAlt = faListAlt;
    faAngleDown = faAngleDown;
    faAngleUp = faAngleUp;

    ngOnInit() {
        const courseIdParams$ = this.route.parent?.parent?.params;
        const exerciseIdParams$ = this.route.params;
        if (courseIdParams$) {
            combineLatest([courseIdParams$, exerciseIdParams$])
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe(([courseIdParams, exerciseIdParams]) => {
                    const didExerciseChange = this.exerciseId !== parseInt(exerciseIdParams.exerciseId, 10);
                    const didCourseChange = this.courseId !== parseInt(courseIdParams.courseId, 10);

                    // if learningPathMode is enabled these attributes will be set by the parent
                    if (!this.learningPathMode) {
                        this.exerciseId = parseInt(exerciseIdParams.exerciseId, 10);
                        this.courseId = parseInt(courseIdParams.courseId, 10);
                    }
                    if (didExerciseChange || didCourseChange) {
                        this.loadExercise();
                    }

                    this.scienceService.logEvent(ScienceEventType.EXERCISE__OPEN, this.exerciseId);
                });
        }
    }

    loadExercise() {
        this.participationMode.set('graded');
        this._studentParticipations.set(this.participationWebsocketService.getParticipationsForExercise(this.exerciseId));
        this._resultWithComplaint.set(
            getFirstResultWithComplaintFromResults(
                this.gradedStudentParticipation()?.submissions?.flatMap((submission) => (submission.results ?? []).filter((result): result is Result => result !== undefined)),
            ),
        );
        this.exerciseService.getExerciseDetails(this.exerciseId).subscribe((exerciseResponse: HttpResponse<ExerciseDetailsType>) => {
            this.handleNewExercise(exerciseResponse.body!);
            if (!this.gradedStudentParticipation() && this.practiceStudentParticipation()) {
                this.participationMode.set('practice');
            }
            this.loadComplaintAndLatestRatedResult();
        });
    }

    handleNewExercise(newExerciseDetails: ExerciseDetailsType) {
        this._exercise.set(newExerciseDetails.exercise);
        this.filterUnfinishedResults(this.exercise?.studentParticipations);
        this.mergeResultsAndSubmissionsForParticipations();
        this.liveQuizStatus.set(this.computeInitialLiveQuizStatus());
        this._isAfterAssessmentDueDate.set(!this.exercise?.assessmentDueDate || dayjs().isAfter(this.exercise.assessmentDueDate));
        this._allowComplaintsForAutomaticAssessments.set(false);
        this._plagiarismCaseInfo.set(newExerciseDetails.plagiarismCaseInfo);
        if (this.exercise?.type === ExerciseType.PROGRAMMING) {
            const programmingExercise = this.exercise as ProgrammingExercise;
            const isAfterDateForComplaint =
                !this.exercise.dueDate ||
                (hasExerciseDueDatePassed(this.exercise, this.gradedStudentParticipation()) &&
                    (!programmingExercise.buildAndTestStudentSubmissionsAfterDueDate || dayjs().isAfter(programmingExercise.buildAndTestStudentSubmissionsAfterDueDate)));

            this._allowComplaintsForAutomaticAssessments.set(!!programmingExercise.allowComplaintsForAutomaticAssessments && isAfterDateForComplaint);
            this._submissionPolicy.set(programmingExercise.submissionPolicy);
        }

        if ((this.exercise?.type === ExerciseType.PROGRAMMING || this.exercise?.type === ExerciseType.TEXT) && !this.exercise.exerciseGroup && this.courseId) {
            this._irisEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS));
            if (this.irisEnabled()) {
                this.irisSettingsService
                    .getCourseSettingsWithRateLimit(this.courseId)
                    .pipe(takeUntilDestroyed(this.destroyRef))
                    .subscribe((response) => {
                        this._irisChatEnabled.set(response?.settings?.enabled ?? false);
                    });
            }
        }

        this.showIfExampleSolutionPresent(newExerciseDetails.exercise);
        this.subscribeForNewResults();
        this.subscribeToTeamAssignmentUpdates();

        this._baseResource.set(`/course-management/${this.courseId}/${this.exercise?.type}-exercises/${this.exercise?.id}/`);
        if (this.exercise?.type) {
            this._exerciseIcon.set(getIcon(this.exercise.type));
        }
        this.createInstructorActions();
    }

    /**
     * Sets example solution and related fields if exampleSolution exists on newExercise,
     * otherwise clears the previously set example solution related fields.
     *
     * @param newExercise Exercise model that may have an exampleSolution.
     */
    showIfExampleSolutionPresent(newExercise: Exercise) {
        this._exampleSolutionInfo.set(ExerciseService.extractExampleSolutionInfo(newExercise, this.artemisMarkdown));
    }

    /**
     * Filters out any unfinished Results
     */
    private filterUnfinishedResults(participations?: StudentParticipation[]) {
        participations?.forEach((participation) => {
            const results = participation?.submissions?.flatMap((submission) => submission.results) ?? [];
            if (results) {
                this._resultsOfGradedStudentParticipation.set(results);
            }
        });
    }

    sortResults() {
        const participations = this._studentParticipations();
        if (participations?.length) {
            participations.forEach((participation) => participation.submissions?.flatMap((submission) => submission.results)?.sort(this.resultSortFunction));
            const sorted = participations
                .flatMap(
                    (participation) =>
                        participation.submissions?.flatMap((submission) => {
                            return (
                                submission.results?.map((result) => {
                                    result.submission = submission;
                                    result.submission.participation = participation;
                                    return result;
                                }) ?? []
                            );
                        }) ?? [],
                )
                .sort(this.resultSortFunction)
                .filter((result) => !(result.assessmentType === AssessmentType.AUTOMATIC_ATHENA && !result.successful));
            this._sortedHistoryResults.set(sorted);
        }
    }
    private resultSortFunction = (a: Result, b: Result) => {
        const aValue = dayjs(a.completionDate!).valueOf();
        const bValue = dayjs(b.completionDate!).valueOf();
        return aValue - bValue;
    };

    mergeResultsAndSubmissionsForParticipations() {
        // if there are new student participation(s) from the server, we need to update studentParticipations
        if (this.exercise?.studentParticipations?.length) {
            const merged = this.participationService.mergeStudentParticipations(this.exercise.studentParticipations);
            if (merged?.length) {
                this._studentParticipations.set(merged);
                this.sortResults();
                // Add exercise to studentParticipation, as the result component is dependent on its existence.
                merged.forEach((participation) => (participation.exercise = this.exercise));
            }
        }
    }

    subscribeForNewResults() {
        // Cancel previous subscription to avoid duplicates when exercise changes
        this.participationUpdateListener?.unsubscribe();

        const participations = this._studentParticipations();
        if (this.exercise && participations?.length) {
            participations.forEach((participation) => {
                this.participationWebsocketService.addParticipation(participation, this.exercise!);
            });
        }

        this.participationUpdateListener = this.participationWebsocketService
            .subscribeForParticipationChanges()
            // Skip the first event, as it is the initial state. All data should already be loaded.
            .pipe(skip(1), takeUntilDestroyed(this.destroyRef))
            .subscribe((changedParticipation: StudentParticipation) => {
                if (changedParticipation && this.exercise && changedParticipation.exercise?.id === this.exercise.id) {
                    const currentGraded = this.gradedStudentParticipation();
                    // Notify student about late submission result
                    if (
                        changedParticipation.exercise?.dueDate &&
                        hasExerciseDueDatePassed(changedParticipation.exercise, changedParticipation) &&
                        changedParticipation.id === currentGraded?.id &&
                        getAllResultsOfAllSubmissions(changedParticipation.submissions).length > getAllResultsOfAllSubmissions(currentGraded?.submissions).length
                    ) {
                        this.alertService.success('artemisApp.exercise.lateSubmissionResultReceived');
                    }
                    if (
                        (getAllResultsOfAllSubmissions(changedParticipation.submissions)?.length > getAllResultsOfAllSubmissions(currentGraded?.submissions).length ||
                            getAllResultsOfAllSubmissions(changedParticipation.submissions)?.last()?.completionDate === undefined) &&
                        getAllResultsOfAllSubmissions(changedParticipation.submissions).last()?.assessmentType === AssessmentType.AUTOMATIC_ATHENA &&
                        getAllResultsOfAllSubmissions(changedParticipation.submissions)?.last()?.successful !== undefined
                    ) {
                        if (getAllResultsOfAllSubmissions(changedParticipation.submissions)?.last()?.successful === true) {
                            this.alertService.success('artemisApp.exercise.athenaFeedbackSuccessful', { title: this.exercise?.title ?? '' });
                        } else {
                            this.alertService.error('artemisApp.exercise.athenaFeedbackFailed');
                        }
                    }
                    const currentParticipations = this._studentParticipations();
                    let updatedParticipations: StudentParticipation[];
                    if (currentParticipations?.some((participation) => participation.id === changedParticipation.id)) {
                        // Keep the existing participation's fields (the websocket payload may only carry a result delta)
                        // and merge in the changed submissions so prior attempts are not lost (see mergeSubmissions).
                        updatedParticipations = currentParticipations.map((participation) => {
                            if (participation.id !== changedParticipation.id) {
                                return participation;
                            }
                            const merged = deepClone(participation);
                            merged.submissions = this.mergeSubmissions(participation.submissions, changedParticipation.submissions);
                            return merged;
                        });
                    } else {
                        updatedParticipations = currentParticipations.concat(changedParticipation);
                    }
                    this._studentParticipations.set(updatedParticipations);
                    this.sortResults();
                    this.navigateToAthenaResult(changedParticipation);
                }
            });
    }

    /**
     * Receives team assignment changes and applies them if they belong to this exercise
     */
    async subscribeToTeamAssignmentUpdates() {
        // Cancel previous subscription to avoid duplicates when exercise changes
        this.teamAssignmentUpdateListener?.unsubscribe();

        this.teamAssignmentUpdateListener = (await this.teamService.teamAssignmentUpdates)
            .pipe(
                filter(({ exerciseId }: TeamAssignmentPayload) => exerciseId === this.exercise?.id),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((teamAssignment) => {
                if (this.exercise && teamAssignment.studentParticipations) {
                    const updatedExercise = deepClone(this.exercise!);
                    updatedExercise.studentAssignedTeamId = teamAssignment.teamId;
                    updatedExercise.studentParticipations = teamAssignment.studentParticipations;
                    this.exercise = updatedExercise;
                    this.mergeResultsAndSubmissionsForParticipations();
                }
            });
    }

    /**
     * Merges incoming submissions into the existing ones, deduplicating by id so the full attempt history is preserved.
     *
     * Practice quiz submits (and websocket result deltas) deliver a participation payload that only carries the latest
     * submission. Replacing the stored submissions with that payload would drop every prior attempt from the
     * result-history dropdown until a page refresh reloads the full participation. Existing submissions are therefore
     * kept in place (an incoming submission with the same id replaces its older version), and genuinely new
     * submissions are appended. Submissions without an id are never deduplicated away.
     */
    private mergeSubmissions(existingSubmissions: Submission[] = [], incomingSubmissions: Submission[] = []): Submission[] {
        const incomingById = new Map(incomingSubmissions.filter((submission) => submission.id !== undefined).map((submission) => [submission.id, submission]));
        const updatedExisting = existingSubmissions.map((submission) => (submission.id !== undefined ? (incomingById.get(submission.id) ?? submission) : submission));
        const existingIds = new Set(existingSubmissions.map((submission) => submission.id));
        const newSubmissions = incomingSubmissions.filter((submission) => submission.id === undefined || !existingIds.has(submission.id));
        return updatedExisting.concat(newSubmissions);
    }

    onNewParticipation(participation: StudentParticipation) {
        const current = this._studentParticipations();
        if (current.some((p) => p.id === participation.id)) {
            // Keep the incoming participation's fields (it is the freshly started/changed one) but preserve the full
            // attempt history by merging submissions rather than replacing them (see mergeSubmissions for the why).
            this._studentParticipations.set(
                current.map((p) => {
                    if (p.id !== participation.id) {
                        return p;
                    }
                    const merged = deepClone(participation);
                    merged.submissions = this.mergeSubmissions(p.submissions, participation.submissions);
                    return merged;
                }),
            );
        } else {
            this._studentParticipations.set(current.concat(participation));

            if (this.exercise) {
                this.exercise.studentParticipations = (this.exercise.studentParticipations ?? []).concat(participation);
            }
            if (participation.id && this.exercise) {
                this.participationWebsocketService.addParticipation(participation, this.exercise);
            }
        }
        this.propagateParticipationsToCachedCourse();
        this.sortResults();
        if (participation.testRun) {
            this.participationMode.set('practice');
        }
    }

    /**
     * Propagates the currently resolved student participations into the cached course (via {@link CourseStorageService})
     * so the course-overview sidebar re-maps and reflects the started exercise live — its card transitions from
     * "Not yet started" to the started/result state and shows the score without a page reload.
     *
     * This must run for both a participation that is new to this component instance and one that is already present:
     * starting a programming exercise immediately navigates to the code editor, which re-resolves this component with
     * the participation already loaded. In that case {@link onNewParticipation} takes the "already present" branch, so
     * scoping the propagation to only newly created participations left the sidebar card stuck at "Not yet started".
     */
    private propagateParticipationsToCachedCourse(): void {
        const exerciseId = this.exercise?.id;
        if (exerciseId === undefined) {
            return;
        }
        const course = this.courseStorageService.getCourse(this.courseId);
        const cachedExercise = course?.exercises?.find((exercise) => exercise.id === exerciseId);
        if (course && cachedExercise) {
            cachedExercise.studentParticipations = this._studentParticipations();
            this.courseStorageService.updateCourse(course);
        }
    }

    onLiveQuizStatusChange(status: LiveQuizParticipationStatus | undefined) {
        this.liveQuizStatus.set(status);
    }

    /**
     * Derives the initial live quiz badge status from the loaded exercise data. Returns undefined for non-quiz or
     * ended quizzes (where the result / data-driven status applies). A started batch means the student is in the
     * running quiz; otherwise they are still at the waiting/join phase.
     */
    private computeInitialLiveQuizStatus(): LiveQuizParticipationStatus | undefined {
        if (this.exercise?.type !== ExerciseType.QUIZ) {
            return undefined;
        }
        const quizExercise = this.exercise as QuizExercise;
        const submitted = this.gradedStudentParticipation()?.submissions?.some((submission) => submission.submitted) ?? false;
        if (quizExercise.quizEnded) {
            return submitted ? undefined : LiveQuizParticipationStatus.MISSED;
        }
        if (submitted) {
            return LiveQuizParticipationStatus.SUBMITTED;
        }
        return quizExercise.quizBatches?.some((batch) => batch.started) ? LiveQuizParticipationStatus.PARTICIPATING : LiveQuizParticipationStatus.NOT_STARTED;
    }

    /**
     * Reflects a live quiz submission in the graded participation immediately, so the participation status badge
     * switches from "Currently participating" to "Submitted, waiting for due date"
     */
    onQuizSubmitted(submission: QuizSubmission) {
        const graded = this.gradedStudentParticipation();
        if (!graded) {
            return;
        }
        const updatedParticipations = this._studentParticipations().map((participation) => {
            if (participation.id !== graded.id) {
                return participation;
            }
            const merged = deepClone(participation);
            merged.submissions = this.mergeSubmissions(participation.submissions, [submission]);
            return merged;
        });
        this._studentParticipations.set(updatedParticipations);
    }

    exerciseRatedBadge(result: Result): string {
        return result.rated ? 'bg-success' : 'bg-info';
    }

    /**
     * Loads and stores the complaint if any exists. Furthermore, loads the latest rated result and stores it.
     */
    loadComplaintAndLatestRatedResult(): void {
        const graded = this.gradedStudentParticipation();
        const sorted = this._sortedHistoryResults();
        if (!graded?.submissions?.[0] || !sorted?.length) {
            return;
        }
        this.complaintService.findBySubmissionId(graded.submissions[0].id!).subscribe({
            next: (res) => {
                if (!res.body) {
                    return;
                }
                this._complaint.set(this.complaintService.convertComplaintFromServer(res.body, this.resultWithComplaint()));
            },
            error: (err: HttpErrorResponse) => {
                this.onError(err.message);
            },
        });

        if (this.exercise!.type === ExerciseType.MODELING || this.exercise!.type === ExerciseType.TEXT || this.exercise!.type === ExerciseType.MATH) {
            return;
        }

        const ratedResults = getAllResultsOfAllSubmissions(graded?.submissions)
            ?.filter((result: Result) => result.rated)
            .sort(this.resultSortFunction);
        if (ratedResults) {
            const latestResult = ratedResults.last();
            this._latestRatedResult.set(latestResult);
        }
    }

    /**
     * Returns the status of the exercise if it is a quiz exercise or undefined otherwise.
     */
    get quizExerciseStatus(): QuizStatus | undefined {
        if (this.exercise?.type === ExerciseType.QUIZ) {
            return this.quizExerciseService.getStatus(this.exercise as QuizExercise);
        }
        return undefined;
    }

    private onError(error: string) {
        this.alertService.error(error);
    }

    toggleShowMoreResults() {
        this._showMoreResults.update((v) => !v);
    }

    // INSTRUCTOR ACTIONS
    createInstructorActions() {
        const items: InstructorActionItem[] = [];
        if (this.exercise?.isAtLeastTutor) {
            this.createTutorActions().forEach((item) => items.push(item));
        }
        if (this.exercise?.isAtLeastEditor) {
            this.createEditorActions().forEach((item) => items.push(item));
        }
        if (this.exercise?.isAtLeastInstructor && this.QUIZ_ENDED_STATUS.includes(this.quizExerciseStatus)) {
            items.push(this.getReEvaluateItem());
        }
        this._instructorActionItems.set(items);
    }

    createTutorActions(): InstructorActionItem[] {
        const tutorActionItems = this.getDefaultItems().slice();
        if (this.exercise?.type === ExerciseType.QUIZ) {
            this.getQuizItems().forEach((item) => tutorActionItems.push(item));
        } else {
            tutorActionItems.push(this.getParticipationItem());
        }
        return tutorActionItems;
    }

    getDefaultItems(): InstructorActionItem[] {
        return [
            {
                routerLink: `${this.baseResource()}`,
                icon: faEye,
                translation: 'entity.action.view',
            },
            {
                routerLink: `${this.baseResource()}scores`,
                icon: faTable,
                translation: 'entity.action.scores',
            },
        ];
    }

    getQuizItems(): InstructorActionItem[] {
        return [
            {
                routerLink: `${this.baseResource()}preview`,
                icon: faEye,
                translation: 'artemisApp.quizExercise.preview',
            },
            {
                routerLink: `${this.baseResource()}solution`,
                icon: faEye,
                translation: 'artemisApp.quizExercise.solution',
            },
        ];
    }

    getParticipationItem(): InstructorActionItem {
        return {
            routerLink: `${this.baseResource()}participations`,
            icon: faListAlt,
            translation: 'artemisApp.exercise.participations',
        };
    }

    createEditorActions(): InstructorActionItem[] {
        const editorItems: InstructorActionItem[] = [];
        if (this.exercise?.type === ExerciseType.QUIZ) {
            editorItems.push(this.getStatisticItem('quiz-point-statistic'));
            if (this.QUIZ_EDITABLE_STATUS.includes(this.quizExerciseStatus)) {
                editorItems.push(this.getQuizEditItem());
            }
        } else if (this.exercise?.type === ExerciseType.MODELING) {
            editorItems.push(this.getStatisticItem('exercise-statistics'));
        } else if (this.exercise?.type === ExerciseType.PROGRAMMING) {
            editorItems.push(this.getGradingItem());
        }
        return editorItems;
    }

    getStatisticItem(routerLink: string): InstructorActionItem {
        return {
            routerLink: `${this.baseResource()}${routerLink}`,
            icon: faSignal,
            translation: 'artemisApp.courseOverview.exerciseDetails.instructorActions.statistics',
        };
    }

    getGradingItem(): InstructorActionItem {
        return {
            routerLink: `${this.baseResource()}grading/test-cases`,
            icon: faFileSignature,
            translation: 'artemisApp.programmingExercise.configureGrading.shortTitle',
        };
    }

    getQuizEditItem(): InstructorActionItem {
        return {
            routerLink: `${this.baseResource()}edit`,
            icon: faWrench,
            translation: 'entity.action.edit',
        };
    }

    getReEvaluateItem(): InstructorActionItem {
        return {
            routerLink: `${this.baseResource()}re-evaluate`,
            icon: faWrench,
            translation: 'entity.action.re-evaluate',
        };
    }

    private navigateToAthenaResult(changedParticipation: StudentParticipation) {
        const athenaSubmission = changedParticipation.submissions?.find((s) => s.results?.some((r) => r.assessmentType === AssessmentType.AUTOMATIC_ATHENA && r.successful));

        const submissionId = athenaSubmission?.id;
        if (!submissionId || !this.exercise?.type || !changedParticipation.id) {
            return;
        }
        let exerciseTypePath: string;
        if (this.exercise.type === ExerciseType.TEXT) {
            exerciseTypePath = 'text-exercises';
        } else if (this.exercise.type === ExerciseType.MODELING) {
            exerciseTypePath = 'modeling-exercises';
        } else if (this.exercise.type === ExerciseType.MATH) {
            exerciseTypePath = 'math-exercises';
        } else {
            return;
        }
        this.router.navigate(['/courses', this.courseId, 'exercises', exerciseTypePath, this.exercise.id, 'participate', changedParticipation.id, 'submission', submissionId]);
    }

    ngOnDestroy() {
        const participations = this._studentParticipations();
        participations?.forEach((participation) => {
            if (participation.id && this.exercise) {
                this.participationWebsocketService.unsubscribeForLatestResultOfParticipation(participation.id, this.exercise);
            }
        });
    }

    protected readonly hasResults = hasResults;
}
