import { Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { combineLatest } from 'rxjs';
import { filter, skip } from 'rxjs/operators';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import dayjs from 'dayjs/esm';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ExampleSolutionInfo, ExerciseDetailsType, ExerciseService } from 'app/exercise/services/exercise.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { hasExerciseDueDatePassed } from 'app/exercise/util/exercise.utils';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AlertService } from 'app/shared/service/alert.service';
import { TeamAssignmentPayload } from 'app/exercise/shared/entities/team/team.model';
import { TeamService } from 'app/exercise/team/team.service';
import { QuizExercise, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { getAllResultsOfAllSubmissions, getFirstResultWithComplaintFromResults } from 'app/exercise/shared/entities/submission/submission.model';
import { Complaint } from 'app/assessment/shared/entities/complaint.model';
import { SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { IconDefinition, faAngleDown, faAngleUp, faBook, faEye, faFileSignature, faListAlt, faSignal, faTable, faWrench } from '@fortawesome/free-solid-svg-icons';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { PlagiarismCaseInfo } from 'app/plagiarism/shared/entities/PlagiarismCaseInfo';
import { MAX_RESULT_HISTORY_LENGTH, ResultHistoryComponent } from 'app/exercise/result-history/result-history.component';
import { isCommunicationEnabled, isMessagingEnabled } from 'app/core/course/shared/entities/course.model';
import { ExerciseCacheService } from 'app/exercise/services/exercise-cache.service';
import { ScienceEventType } from 'app/shared/science/science.model';
import { MODULE_FEATURE_IRIS } from 'app/app.constants';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExerciseDetailsStudentActionsComponent } from './student-actions/exercise-details-student-actions.component';
import { ExerciseHeadersInformationComponent } from 'app/exercise/exercise-headers/exercise-headers-information/exercise-headers-information.component';
import { ResultComponent } from 'app/exercise/result/result.component';
import { ProblemStatementComponent } from './problem-statement/problem-statement.component';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { ProgrammingExerciseExampleSolutionRepoDownloadComponent } from 'app/programming/shared/actions/example-solution-repo-download/programming-exercise-example-solution-repo-download.component';
import { ExerciseInfoComponent } from 'app/exercise/exercise-info/exercise-info.component';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { IrisExerciseChatbotButtonComponent } from 'app/iris/overview/exercise-chatbot/exercise-chatbot-button.component';
import { DiscussionSectionComponent } from 'app/communication/shared/discussion-section/discussion-section.component';
import { LtiInitializerComponent } from './lti-initializer/lti-initializer.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ResetRepoButtonComponent } from 'app/core/course/overview/exercise-details/reset-repo-button/reset-repo-button.component';
import { ScienceService } from 'app/shared/science/science.service';
import { hasResults } from 'app/exercise/participation/participation.utils';
import { CompetencyContributionComponent } from 'app/atlas/shared/competency-contribution/competency-contribution.component';

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
    imports: [
        FaIconComponent,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgbDropdownItem,
        RouterLink,
        TranslateDirective,
        ExerciseDetailsStudentActionsComponent,
        ExerciseHeadersInformationComponent,
        ResultHistoryComponent,
        ResultComponent,
        ProblemStatementComponent,
        ResetRepoButtonComponent,
        ModelingEditorComponent,
        ProgrammingExerciseExampleSolutionRepoDownloadComponent,
        NgbTooltip,
        ExerciseInfoComponent,
        ComplaintsStudentViewComponent,
        RatingComponent,
        IrisExerciseChatbotButtonComponent,
        DiscussionSectionComponent,
        LtiInitializerComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        CompetencyContributionComponent,
    ],
})
export class CourseExerciseDetailsComponent implements OnInit, OnDestroy {
    private exerciseService = inject(ExerciseService);
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private participationService = inject(ParticipationService);
    private route = inject(ActivatedRoute);
    private profileService = inject(ProfileService);
    private alertService = inject(AlertService);
    private teamService = inject(TeamService);
    private quizExerciseService = inject(QuizExerciseService);
    private complaintService = inject(ComplaintService);
    private artemisMarkdown = inject(ArtemisMarkdownService);
    private readonly scienceService = inject(ScienceService);
    private irisSettingsService = inject(IrisSettingsService);
    private destroyRef = inject(DestroyRef);

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
    readonly dayjs = dayjs;
    readonly ChatServiceMode = ChatServiceMode;

    readonly isCommunicationEnabled = isCommunicationEnabled;
    readonly isMessagingEnabled = isMessagingEnabled;

    // Use signals for reactive state
    public learningPathMode = false;
    public exerciseId: number;
    public courseId: number;

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

    // Sorted results signal
    private readonly _sortedHistoryResults = signal<Result[]>([]);
    public get sortedHistoryResults(): Result[] {
        return this._sortedHistoryResults();
    }
    public set sortedHistoryResults(value: Result[]) {
        this._sortedHistoryResults.set(value);
    }

    // Other reactive state as signals
    private readonly _resultWithComplaint = signal<Result | undefined>(undefined);
    get resultWithComplaint(): Result | undefined {
        return this._resultWithComplaint();
    }
    set resultWithComplaint(value: Result | undefined) {
        this._resultWithComplaint.set(value);
    }

    private readonly _latestRatedResult = signal<Result | undefined>(undefined);
    get latestRatedResult(): Result | undefined {
        return this._latestRatedResult();
    }
    set latestRatedResult(value: Result | undefined) {
        this._latestRatedResult.set(value);
    }

    private readonly _complaint = signal<Complaint | undefined>(undefined);
    get complaint(): Complaint | undefined {
        return this._complaint();
    }
    set complaint(value: Complaint | undefined) {
        this._complaint.set(value);
    }

    private readonly _showMoreResults = signal(false);
    get showMoreResults(): boolean {
        return this._showMoreResults();
    }
    set showMoreResults(value: boolean) {
        this._showMoreResults.set(value);
    }

    private readonly _resultsOfGradedStudentParticipation = signal<(Result | undefined)[]>([]);
    get resultsOfGradedStudentParticipation(): (Result | undefined)[] {
        return this._resultsOfGradedStudentParticipation();
    }
    set resultsOfGradedStudentParticipation(value: (Result | undefined)[]) {
        this._resultsOfGradedStudentParticipation.set(value);
    }

    private readonly _isAfterAssessmentDueDate = signal(false);
    get isAfterAssessmentDueDate(): boolean {
        return this._isAfterAssessmentDueDate();
    }
    set isAfterAssessmentDueDate(value: boolean) {
        this._isAfterAssessmentDueDate.set(value);
    }

    private readonly _allowComplaintsForAutomaticAssessments = signal(false);
    get allowComplaintsForAutomaticAssessments(): boolean {
        return this._allowComplaintsForAutomaticAssessments();
    }
    set allowComplaintsForAutomaticAssessments(value: boolean) {
        this._allowComplaintsForAutomaticAssessments.set(value);
    }

    private readonly _baseResource = signal('');
    get baseResource(): string {
        return this._baseResource();
    }
    set baseResource(value: string) {
        this._baseResource.set(value);
    }

    private readonly _submissionPolicy = signal<SubmissionPolicy | undefined>(undefined);
    get submissionPolicy(): SubmissionPolicy | undefined {
        return this._submissionPolicy();
    }
    set submissionPolicy(value: SubmissionPolicy | undefined) {
        this._submissionPolicy.set(value);
    }

    private readonly _exampleSolutionCollapsed = signal<boolean | undefined>(undefined);
    get exampleSolutionCollapsed(): boolean | undefined {
        return this._exampleSolutionCollapsed();
    }
    set exampleSolutionCollapsed(value: boolean | undefined) {
        this._exampleSolutionCollapsed.set(value);
    }

    private readonly _plagiarismCaseInfo = signal<PlagiarismCaseInfo | undefined>(undefined);
    get plagiarismCaseInfo(): PlagiarismCaseInfo | undefined {
        return this._plagiarismCaseInfo();
    }
    set plagiarismCaseInfo(value: PlagiarismCaseInfo | undefined) {
        this._plagiarismCaseInfo.set(value);
    }

    private readonly _irisEnabled = signal(false);
    get irisEnabled(): boolean {
        return this._irisEnabled();
    }
    set irisEnabled(value: boolean) {
        this._irisEnabled.set(value);
    }

    private readonly _irisChatEnabled = signal(false);
    get irisChatEnabled(): boolean {
        return this._irisChatEnabled();
    }
    set irisChatEnabled(value: boolean) {
        this._irisChatEnabled.set(value);
    }

    private readonly _instructorActionItems = signal<InstructorActionItem[]>([]);
    get instructorActionItems(): InstructorActionItem[] {
        return this._instructorActionItems();
    }
    set instructorActionItems(value: InstructorActionItem[]) {
        this._instructorActionItems.set(value);
    }

    private readonly _exerciseIcon = signal<IconProp | undefined>(undefined);
    get exerciseIcon(): IconProp | undefined {
        return this._exerciseIcon();
    }
    set exerciseIcon(value: IconProp | undefined) {
        this._exerciseIcon.set(value);
    }

    private readonly _exampleSolutionInfo = signal<ExampleSolutionInfo | undefined>(undefined);
    get exampleSolutionInfo(): ExampleSolutionInfo | undefined {
        return this._exampleSolutionInfo();
    }
    set exampleSolutionInfo(value: ExampleSolutionInfo | undefined) {
        this._exampleSolutionInfo.set(value);
    }

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
        this._studentParticipations.set(this.participationWebsocketService.getParticipationsForExercise(this.exerciseId));
        this._resultWithComplaint.set(
            getFirstResultWithComplaintFromResults(
                this.gradedStudentParticipation()?.submissions?.flatMap((submission) => (submission.results ?? []).filter((result): result is Result => result !== undefined)),
            ),
        );
        this.exerciseService.getExerciseDetails(this.exerciseId).subscribe((exerciseResponse: HttpResponse<ExerciseDetailsType>) => {
            this.handleNewExercise(exerciseResponse.body!);
            this.loadComplaintAndLatestRatedResult();
        });
    }

    handleNewExercise(newExerciseDetails: ExerciseDetailsType) {
        this._exercise.set(newExerciseDetails.exercise);
        this.filterUnfinishedResults(this.exercise?.studentParticipations);
        this.mergeResultsAndSubmissionsForParticipations();
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

            this._irisEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS));
            if (this.irisEnabled && !this.exercise.exerciseGroup && this.courseId) {
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
        // For TAs the example solution is collapsed on default to avoid spoiling, as the example solution is always shown to TAs
        this._exampleSolutionCollapsed.set(!!newExercise?.isAtLeastTutor);
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
        const participations = this._studentParticipations();
        if (this.exercise && participations?.length) {
            participations.forEach((participation) => {
                this.participationWebsocketService.addParticipation(participation, this.exercise!);
            });
        }

        this.participationWebsocketService
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
                            this.alertService.success('artemisApp.exercise.athenaFeedbackSuccessful');
                        } else {
                            this.alertService.error('artemisApp.exercise.athenaFeedbackFailed');
                        }
                    }
                    const currentParticipations = this._studentParticipations();
                    let updatedParticipations: StudentParticipation[];
                    if (currentParticipations?.some((participation) => participation.id === changedParticipation.id)) {
                        updatedParticipations = currentParticipations.map((participation) => (participation.id === changedParticipation.id ? changedParticipation : participation));
                    } else {
                        updatedParticipations = [...currentParticipations, changedParticipation];
                    }
                    this._studentParticipations.set(updatedParticipations);
                    this.mergeResultsAndSubmissionsForParticipations();
                }
            });
    }

    /**
     * Receives team assignment changes and applies them if they belong to this exercise
     */
    async subscribeToTeamAssignmentUpdates() {
        (await this.teamService.teamAssignmentUpdates)
            .pipe(
                filter(({ exerciseId }: TeamAssignmentPayload) => exerciseId === this.exercise?.id),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((teamAssignment) => {
                if (this.exercise && teamAssignment.studentParticipations) {
                    this._studentParticipations.set(teamAssignment.studentParticipations);
                    this.mergeResultsAndSubmissionsForParticipations();
                }
            });
    }

    exerciseRatedBadge(result: Result): string {
        return result.rated ? 'bg-success' : 'bg-info';
    }

    get hasMoreResults(): boolean {
        const participations = this._studentParticipations();
        const sorted = this._sortedHistoryResults();
        if (!participations?.length || !sorted.length) {
            return false;
        }
        return sorted.length > MAX_RESULT_HISTORY_LENGTH;
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
                this._complaint.set(res.body);
            },
            error: (err: HttpErrorResponse) => {
                this.onError(err.message);
            },
        });

        if (this.exercise!.type === ExerciseType.MODELING || this.exercise!.type === ExerciseType.TEXT) {
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

    /**
     * Used to change the boolean value for the example solution dropdown menu
     */
    changeExampleSolution() {
        this._exampleSolutionCollapsed.update((v) => !v);
    }

    // INSTRUCTOR ACTIONS
    createInstructorActions() {
        const items: InstructorActionItem[] = [];
        if (this.exercise?.isAtLeastTutor) {
            items.push(...this.createTutorActions());
        }
        if (this.exercise?.isAtLeastEditor) {
            items.push(...this.createEditorActions());
        }
        if (this.exercise?.isAtLeastInstructor && this.QUIZ_ENDED_STATUS.includes(this.quizExerciseStatus)) {
            items.push(this.getReEvaluateItem());
        }
        this._instructorActionItems.set(items);
    }

    createTutorActions(): InstructorActionItem[] {
        const tutorActionItems = [...this.getDefaultItems()];
        if (this.exercise?.type === ExerciseType.QUIZ) {
            tutorActionItems.push(...this.getQuizItems());
        } else {
            tutorActionItems.push(this.getParticipationItem());
        }
        return tutorActionItems;
    }

    getDefaultItems(): InstructorActionItem[] {
        return [
            {
                routerLink: `${this.baseResource}`,
                icon: faEye,
                translation: 'entity.action.view',
            },
            {
                routerLink: `${this.baseResource}scores`,
                icon: faTable,
                translation: 'entity.action.scores',
            },
        ];
    }

    getQuizItems(): InstructorActionItem[] {
        return [
            {
                routerLink: `${this.baseResource}preview`,
                icon: faEye,
                translation: 'artemisApp.quizExercise.preview',
            },
            {
                routerLink: `${this.baseResource}solution`,
                icon: faEye,
                translation: 'artemisApp.quizExercise.solution',
            },
        ];
    }

    getParticipationItem(): InstructorActionItem {
        return {
            routerLink: `${this.baseResource}participations`,
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
            routerLink: `${this.baseResource}${routerLink}`,
            icon: faSignal,
            translation: 'artemisApp.courseOverview.exerciseDetails.instructorActions.statistics',
        };
    }

    getGradingItem(): InstructorActionItem {
        return {
            routerLink: `${this.baseResource}grading/test-cases`,
            icon: faFileSignature,
            translation: 'artemisApp.programmingExercise.configureGrading.shortTitle',
        };
    }

    getQuizEditItem(): InstructorActionItem {
        return {
            routerLink: `${this.baseResource}edit`,
            icon: faWrench,
            translation: 'entity.action.edit',
        };
    }

    getReEvaluateItem(): InstructorActionItem {
        return {
            routerLink: `${this.baseResource}re-evaluate`,
            icon: faWrench,
            translation: 'entity.action.re-evaluate',
        };
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
