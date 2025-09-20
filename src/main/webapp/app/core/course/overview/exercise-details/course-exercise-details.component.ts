import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription, combineLatest } from 'rxjs';
import { filter, skip } from 'rxjs/operators';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import dayjs from 'dayjs/esm';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
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
import { IrisSettings } from 'app/iris/shared/entities/settings/iris-settings.model';
import { ScienceEventType } from 'app/shared/science/science.model';
import { PROFILE_IRIS } from 'app/app.constants';
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
    private readonly cdr = inject(ChangeDetectorRef);
    private readonly scienceService = inject(ScienceService);

    readonly AssessmentType = AssessmentType;
    readonly PlagiarismVerdict = PlagiarismVerdict;
    readonly QuizStatus = QuizStatus;
    readonly QUIZ_ENDED_STATUS: (QuizStatus | undefined)[] = [QuizStatus.CLOSED, QuizStatus.OPEN_FOR_PRACTICE];
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

    public learningPathMode = false;
    public exerciseId: number;
    public courseId: number;
    public exercise: Exercise;
    resultWithComplaint?: Result;
    latestRatedResult?: Result;
    complaint?: Complaint;
    showMoreResults = false;
    public sortedHistoryResults: Result[];
    private participationUpdateListener: Subscription;
    private teamAssignmentUpdateListener: Subscription;
    private submissionSubscription: Subscription;
    studentParticipations: StudentParticipation[] = [];
    resultsOfGradedStudentParticipation: (Result | undefined)[] = [];
    gradedStudentParticipation?: StudentParticipation;
    practiceStudentParticipation?: StudentParticipation;
    isAfterAssessmentDueDate: boolean;
    allowComplaintsForAutomaticAssessments: boolean;
    baseResource: string;
    submissionPolicy?: SubmissionPolicy;
    exampleSolutionCollapsed: boolean;
    plagiarismCaseInfo?: PlagiarismCaseInfo;
    irisSettings?: IrisSettings;
    paramsSubscription: Subscription;
    instructorActionItems: InstructorActionItem[] = [];
    exerciseIcon: IconProp;
    numberOfPracticeResults: number;

    exampleSolutionInfo?: ExampleSolutionInfo;

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
            this.paramsSubscription = combineLatest([courseIdParams$, exerciseIdParams$]).subscribe(([courseIdParams, exerciseIdParams]) => {
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
        this.irisSettings = undefined;
        this.studentParticipations = this.participationWebsocketService.getParticipationsForExercise(this.exerciseId);
        this.updateStudentParticipations();
        this.resultWithComplaint = getFirstResultWithComplaintFromResults(
            this.gradedStudentParticipation?.submissions?.flatMap((submission) => (submission.results ?? []).filter((result): result is Result => result !== undefined)),
        );
        this.exerciseService.getExerciseDetails(this.exerciseId).subscribe((exerciseResponse: HttpResponse<ExerciseDetailsType>) => {
            this.handleNewExercise(exerciseResponse.body!);
            this.loadComplaintAndLatestRatedResult();
        });
    }

    handleNewExercise(newExerciseDetails: ExerciseDetailsType) {
        this.exercise = newExerciseDetails.exercise;
        this.filterUnfinishedResults(this.exercise.studentParticipations);
        this.mergeResultsAndSubmissionsForParticipations();
        this.isAfterAssessmentDueDate = !this.exercise.assessmentDueDate || dayjs().isAfter(this.exercise.assessmentDueDate);
        this.allowComplaintsForAutomaticAssessments = false;
        this.plagiarismCaseInfo = newExerciseDetails.plagiarismCaseInfo;
        if (this.exercise.type === ExerciseType.PROGRAMMING) {
            const programmingExercise = this.exercise as ProgrammingExercise;
            const isAfterDateForComplaint =
                !this.exercise.dueDate ||
                (hasExerciseDueDatePassed(this.exercise, this.gradedStudentParticipation) &&
                    (!programmingExercise.buildAndTestStudentSubmissionsAfterDueDate || dayjs().isAfter(programmingExercise.buildAndTestStudentSubmissionsAfterDueDate)));

            this.allowComplaintsForAutomaticAssessments = !!programmingExercise.allowComplaintsForAutomaticAssessments && isAfterDateForComplaint;
            this.submissionPolicy = programmingExercise.submissionPolicy;

            if (this.profileService.isProfileActive(PROFILE_IRIS)) {
                this.irisSettings = newExerciseDetails.irisSettings;
            }
        }

        this.showIfExampleSolutionPresent(newExerciseDetails.exercise);
        this.subscribeForNewResults();
        this.subscribeToTeamAssignmentUpdates();

        this.baseResource = `/course-management/${this.courseId}/${this.exercise.type}-exercises/${this.exercise.id}/`;
        if (this.exercise?.type) {
            this.exerciseIcon = getIcon(this.exercise?.type);
        }
        this.createInstructorActions();

        this.cdr.detectChanges(); // IMPORTANT: necessary to update the view after the exercise has been loaded in learning path view
    }

    /**
     * Sets example solution and related fields if exampleSolution exists on newExercise,
     * otherwise clears the previously set example solution related fields.
     *
     * @param newExercise Exercise model that may have an exampleSolution.
     */
    showIfExampleSolutionPresent(newExercise: Exercise) {
        this.exampleSolutionInfo = ExerciseService.extractExampleSolutionInfo(newExercise, this.artemisMarkdown);
        // For TAs the example solution is collapsed on default to avoid spoiling, as the example solution is always shown to TAs
        this.exampleSolutionCollapsed = !!newExercise?.isAtLeastTutor;
    }

    /**
     * Filters out any unfinished Results
     */
    private filterUnfinishedResults(participations?: StudentParticipation[]) {
        participations?.forEach((participation: Participation) => {
            const results = participation?.submissions?.flatMap((submission) => submission.results) ?? [];
            if (results) {
                this.resultsOfGradedStudentParticipation = results;
            }
        });
    }

    sortResults() {
        if (this.studentParticipations?.length) {
            this.studentParticipations.forEach((participation) => participation.submissions?.flatMap((submission) => submission.results)?.sort(this.resultSortFunction));
            this.sortedHistoryResults = this.studentParticipations
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
        }
    }
    private resultSortFunction = (a: Result, b: Result) => {
        const aValue = dayjs(a.completionDate!).valueOf();
        const bValue = dayjs(b.completionDate!).valueOf();
        return aValue - bValue;
    };

    mergeResultsAndSubmissionsForParticipations() {
        // if there are new student participation(s) from the server, we need to update this.studentParticipation
        if (this.exercise?.studentParticipations?.length) {
            this.studentParticipations = this.participationService.mergeStudentParticipations(this.exercise.studentParticipations);
            this.exercise.studentParticipations = this.studentParticipations;
            this.updateStudentParticipations();
            this.sortResults();
            // Add exercise to studentParticipation, as the result component is dependent on its existence.
            this.studentParticipations.forEach((participation) => (participation.exercise = this.exercise));
        } else if (this.studentParticipations?.length && this.exercise) {
            // otherwise we make sure that the student participation in exercise is correct
            this.exercise.studentParticipations = this.studentParticipations;
        }
    }

    subscribeForNewResults() {
        if (this.exercise && this.studentParticipations?.length) {
            this.studentParticipations.forEach((participation) => {
                this.participationWebsocketService.addParticipation(participation, this.exercise);
            });
        }

        this.participationUpdateListener?.unsubscribe();
        this.participationUpdateListener = this.participationWebsocketService
            .subscribeForParticipationChanges()
            // Skip the first event, as it is the initial state. All data should already be loaded.
            .pipe(skip(1))
            .subscribe((changedParticipation: StudentParticipation) => {
                if (changedParticipation && this.exercise && changedParticipation.exercise?.id === this.exercise.id) {
                    // Notify student about late submission result
                    if (
                        changedParticipation.exercise?.dueDate &&
                        hasExerciseDueDatePassed(changedParticipation.exercise, changedParticipation) &&
                        changedParticipation.id === this.gradedStudentParticipation?.id &&
                        getAllResultsOfAllSubmissions(changedParticipation.submissions).length > getAllResultsOfAllSubmissions(this.gradedStudentParticipation?.submissions).length
                    ) {
                        this.alertService.success('artemisApp.exercise.lateSubmissionResultReceived');
                    }
                    if (
                        (getAllResultsOfAllSubmissions(changedParticipation.submissions)?.length >
                            getAllResultsOfAllSubmissions(this.gradedStudentParticipation?.submissions).length ||
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
                    if (this.studentParticipations?.some((participation) => participation.id === changedParticipation.id)) {
                        this.exercise.studentParticipations = this.studentParticipations.map((participation) =>
                            participation.id === changedParticipation.id ? changedParticipation : participation,
                        );
                    } else {
                        this.exercise.studentParticipations = [...this.studentParticipations, changedParticipation];
                    }
                    this.updateStudentParticipations();
                    this.mergeResultsAndSubmissionsForParticipations();
                }
            });
    }

    private updateStudentParticipations() {
        this.gradedStudentParticipation = this.participationService.getSpecificStudentParticipation(this.studentParticipations, false);
        this.practiceStudentParticipation = this.participationService.getSpecificStudentParticipation(this.studentParticipations, true);
        this.numberOfPracticeResults = this.practiceStudentParticipation?.submissions?.flatMap((submission) => submission.results)?.length ?? 0;
    }

    /**
     * Receives team assignment changes and applies them if they belong to this exercise
     */
    async subscribeToTeamAssignmentUpdates() {
        this.teamAssignmentUpdateListener = (await this.teamService.teamAssignmentUpdates)
            .pipe(filter(({ exerciseId }: TeamAssignmentPayload) => exerciseId === this.exercise?.id))
            .subscribe((teamAssignment) => {
                this.exercise!.studentAssignedTeamId = teamAssignment.teamId;
                this.exercise!.studentParticipations = teamAssignment.studentParticipations;
            });
    }

    exerciseRatedBadge(result: Result): string {
        return result.rated ? 'bg-success' : 'bg-info';
    }

    get hasMoreResults(): boolean {
        if (!this.studentParticipations?.length || !this.sortedHistoryResults.length) {
            return false;
        }
        return this.sortedHistoryResults.length > MAX_RESULT_HISTORY_LENGTH;
    }

    /**
     * Loads and stores the complaint if any exists. Furthermore, loads the latest rated result and stores it.
     */
    loadComplaintAndLatestRatedResult(): void {
        if (!this.gradedStudentParticipation?.submissions?.[0] || !this.sortedHistoryResults?.length) {
            return;
        }
        this.complaintService.findBySubmissionId(this.gradedStudentParticipation!.submissions![0].id!).subscribe({
            next: (res) => {
                if (!res.body) {
                    return;
                }
                this.complaint = res.body;
            },
            error: (err: HttpErrorResponse) => {
                this.onError(err.message);
            },
        });

        if (this.exercise!.type === ExerciseType.MODELING || this.exercise!.type === ExerciseType.TEXT) {
            return;
        }

        const ratedResults = getAllResultsOfAllSubmissions(this.gradedStudentParticipation?.submissions)
            ?.filter((result: Result) => result.rated)
            .sort(this.resultSortFunction);
        if (ratedResults) {
            const latestResult = ratedResults.last();
            this.latestRatedResult = latestResult;
        }
    }

    /**
     * Returns the status of the exercise if it is a quiz exercise or undefined otherwise.
     */
    get quizExerciseStatus(): QuizStatus | undefined {
        if (this.exercise!.type === ExerciseType.QUIZ) {
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
        this.exampleSolutionCollapsed = !this.exampleSolutionCollapsed;
    }

    // INSTRUCTOR ACTIONS
    createInstructorActions() {
        if (this.exercise?.isAtLeastTutor) {
            this.instructorActionItems = this.createTutorActions();
        }
        if (this.exercise?.isAtLeastEditor) {
            this.instructorActionItems.push(...this.createEditorActions());
        }
        if (this.exercise?.isAtLeastInstructor && this.QUIZ_ENDED_STATUS.includes(this.quizExerciseStatus)) {
            this.instructorActionItems.push(this.getReEvaluateItem());
        }
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
        this.participationUpdateListener?.unsubscribe();
        this.studentParticipations?.forEach((participation) => {
            if (participation.id && this.exercise) {
                this.participationWebsocketService.unsubscribeForLatestResultOfParticipation(participation.id, this.exercise);
            }
        });

        this.teamAssignmentUpdateListener?.unsubscribe();
        this.submissionSubscription?.unsubscribe();
        this.paramsSubscription?.unsubscribe();
    }

    protected readonly hasResults = hasResults;
}
