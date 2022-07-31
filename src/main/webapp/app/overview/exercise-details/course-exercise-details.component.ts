import { Component, ContentChild, OnDestroy, OnInit, TemplateRef } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { Result } from 'app/entities/result.model';
import dayjs from 'dayjs/esm';
import { User } from 'app/core/user/user.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { programmingExerciseFail, programmingExerciseSuccess } from 'app/guided-tour/tours/course-exercise-detail-tour';
import { SourceTreeService } from 'app/exercises/programming/shared/service/sourceTree.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { InitializationState, Participation } from 'app/entities/participation/participation.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { getExerciseDueDate, hasExerciseDueDatePassed, participationStatus } from 'app/exercises/shared/exercise/exercise.utils';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { AlertService } from 'app/core/util/alert.service';
import { TeamAssignmentPayload } from 'app/entities/team.model';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { QuizExercise, QuizStatus } from 'app/entities/quiz/quiz-exercise.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { DiscussionSectionComponent } from 'app/overview/discussion-section/discussion-section.component';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { getFirstResultWithComplaintFromResults } from 'app/entities/submission.model';
import { ComplaintService } from 'app/complaints/complaint.service';
import { Complaint } from 'app/entities/complaint.model';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { setBuildPlanUrlForProgrammingParticipations } from 'app/exercises/shared/participation/participation.utils';
import { SubmissionPolicyService } from 'app/exercises/programming/manage/services/submission-policy.service';
import { SubmissionPolicy } from 'app/entities/submission-policy.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { UMLModel } from '@ls1intum/apollon';
import { SafeHtml } from '@angular/platform-browser';
import { faBook, faExternalLinkAlt, faEye, faFileSignature, faListAlt, faSignal, faTable, faWrench, faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';
import { TextExercise } from 'app/entities/text-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/shared/exercise-hint.service';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';

const MAX_RESULT_HISTORY_LENGTH = 5;

@Component({
    selector: 'jhi-course-exercise-details',
    templateUrl: './course-exercise-details.component.html',
    styleUrls: ['../course-overview.scss', '../tab-bar/tab-bar.scss'],
})
export class CourseExerciseDetailsComponent implements OnInit, OnDestroy {
    readonly AssessmentType = AssessmentType;
    readonly QuizStatus = QuizStatus;
    readonly QUIZ_ENDED_STATUS: (QuizStatus | undefined)[] = [QuizStatus.CLOSED, QuizStatus.OPEN_FOR_PRACTICE];
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;
    readonly TEXT = ExerciseType.TEXT;
    readonly FILE_UPLOAD = ExerciseType.FILE_UPLOAD;

    private currentUser: User;
    private exerciseId: number;
    public courseId: number;
    private subscription: Subscription;
    public exercise?: Exercise;
    public resultWithComplaint?: Result;
    public latestRatedResult?: Result;
    public complaint?: Complaint;
    public showMoreResults = false;
    public sortedHistoryResult: Result[]; // might be a subset of the actual results in combinedParticipation.results
    public exerciseCategories: ExerciseCategory[];
    private participationUpdateListener: Subscription;
    private teamAssignmentUpdateListener: Subscription;
    private submissionSubscription: Subscription;
    studentParticipation?: StudentParticipation;
    isAfterAssessmentDueDate: boolean;
    allowComplaintsForAutomaticAssessments: boolean;
    public gradingCriteria: GradingCriterion[];
    private discussionComponent?: DiscussionSectionComponent;
    baseResource: string;
    isExamExercise: boolean;
    hasSubmissionPolicy: boolean;
    submissionPolicy: SubmissionPolicy;
    exampleSolutionCollapsed: boolean;
    plagiarismCaseId?: number;
    availableExerciseHints: ExerciseHint[];
    activatedExerciseHints: ExerciseHint[];

    public modelingExercise?: ModelingExercise;
    public exampleSolution?: SafeHtml;
    public exampleSolutionUML?: UMLModel;
    public isProgrammingExerciseExampleSolutionPublished = false;

    // extension points, see shared/extension-point
    @ContentChild('overrideStudentActions') overrideStudentActions: TemplateRef<any>;

    /**
     * variables are only for testing purposes(noVersionControlAndContinuousIntegrationAvailable)
     */
    public inProductionEnvironment: boolean;
    public wasSubmissionSimulated = false;

    // Icons
    faBook = faBook;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faListAlt = faListAlt;
    faSignal = faSignal;
    faExternalLinkAlt = faExternalLinkAlt;
    faFileSignature = faFileSignature;
    faAngleDown = faAngleDown;
    faAngleUp = faAngleUp;

    constructor(
        private exerciseService: ExerciseService,
        private courseService: CourseManagementService,
        private jhiWebsocketService: JhiWebsocketService,
        private accountService: AccountService,
        private courseCalculationService: CourseScoreCalculationService,
        private participationWebsocketService: ParticipationWebsocketService,
        private participationService: ParticipationService,
        private sourceTreeService: SourceTreeService,
        private courseServer: CourseManagementService,
        private route: ActivatedRoute,
        private profileService: ProfileService,
        private guidedTourService: GuidedTourService,
        private alertService: AlertService,
        private programmingExerciseSubmissionPolicyService: SubmissionPolicyService,
        private teamService: TeamService,
        private quizExerciseService: QuizExerciseService,
        private submissionService: ProgrammingSubmissionService,
        private complaintService: ComplaintService,
        private navigationUtilService: ArtemisNavigationUtilService,
        private artemisMarkdown: ArtemisMarkdownService,
        private plagiarismCaseService: PlagiarismCasesService,
        private exerciseHintService: ExerciseHintService,
    ) {}

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            const didExerciseChange = this.exerciseId !== parseInt(params['exerciseId'], 10);
            const didCourseChange = this.courseId !== parseInt(params['courseId'], 10);
            this.exerciseId = parseInt(params['exerciseId'], 10);
            this.courseId = parseInt(params['courseId'], 10);
            this.accountService.identity().then((user: User) => {
                this.currentUser = user;
            });
            if (didExerciseChange || didCourseChange) {
                this.loadExercise();
            }
        });

        // Checks if the current environment is production
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.inProductionEnvironment = profileInfo.inProduction;
            }
        });
    }

    ngOnDestroy() {
        if (this.participationUpdateListener) {
            this.participationUpdateListener.unsubscribe();
            if (this.studentParticipation) {
                this.participationWebsocketService.unsubscribeForLatestResultOfParticipation(this.studentParticipation!.id!, this.exercise!);
            }
        }
        if (this.teamAssignmentUpdateListener) {
            this.teamAssignmentUpdateListener.unsubscribe();
        }
        if (this.submissionSubscription) {
            this.submissionSubscription.unsubscribe();
        }
    }

    loadExercise() {
        this.exercise = undefined;
        this.studentParticipation = this.participationWebsocketService.getParticipationForExercise(this.exerciseId);
        this.resultWithComplaint = getFirstResultWithComplaintFromResults(this.studentParticipation?.results);
        this.exerciseService.getExerciseDetails(this.exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
            this.handleNewExercise(exerciseResponse.body!);
            this.getLatestRatedResult();
        });
        this.plagiarismCaseService.getPlagiarismCaseIdForStudent(this.courseId, this.exerciseId).subscribe((res: HttpResponse<number>) => {
            this.plagiarismCaseId = res.body ?? undefined;
        });
    }

    handleNewExercise(newExercise: Exercise) {
        this.exercise = newExercise;
        this.exercise.studentParticipations = this.filterParticipations(newExercise.studentParticipations);
        this.mergeResultsAndSubmissionsForParticipations();
        this.exercise.participationStatus = participationStatus(this.exercise);
        const now = dayjs();
        this.isAfterAssessmentDueDate = !this.exercise.assessmentDueDate || now.isAfter(this.exercise.assessmentDueDate);
        this.exerciseCategories = this.exercise.categories || [];
        this.allowComplaintsForAutomaticAssessments = false;

        if (this.exercise.type === ExerciseType.PROGRAMMING) {
            const programmingExercise = this.exercise as ProgrammingExercise;
            const isAfterDateForComplaint =
                (!this.exercise.dueDate || hasExerciseDueDatePassed(this.exercise, this.studentParticipation)) &&
                (!programmingExercise.buildAndTestStudentSubmissionsAfterDueDate || now.isAfter(programmingExercise.buildAndTestStudentSubmissionsAfterDueDate));

            this.allowComplaintsForAutomaticAssessments = !!programmingExercise.allowComplaintsForAutomaticAssessments && isAfterDateForComplaint;
            if (this.exercise?.studentParticipations && programmingExercise.projectKey) {
                this.profileService.getProfileInfo().subscribe((profileInfo) => {
                    setBuildPlanUrlForProgrammingParticipations(profileInfo, this.exercise?.studentParticipations!, (this.exercise as ProgrammingExercise).projectKey);
                });
            }
            this.hasSubmissionPolicy = false;
            this.programmingExerciseSubmissionPolicyService.getSubmissionPolicyOfProgrammingExercise(this.exerciseId).subscribe((submissionPolicy) => {
                this.submissionPolicy = submissionPolicy;
                this.hasSubmissionPolicy = true;
            });
        }

        this.showIfExampleSolutionPresent(newExercise);
        this.subscribeForNewResults();
        this.subscribeToTeamAssignmentUpdates();

        // Subscribe for late programming submissions to show the student a success message
        if (this.exercise.type === ExerciseType.PROGRAMMING && hasExerciseDueDatePassed(this.exercise, this.studentParticipation!)) {
            this.subscribeForNewSubmissions();
        }

        if (this.discussionComponent && this.exercise) {
            // We need to manually update the exercise property of the posts component
            this.discussionComponent.exercise = this.exercise;
        }
        this.baseResource = `/course-management/${this.courseId}/${this.exercise.type}-exercises/${this.exercise.id}/`;
    }

    /**
     * Sets example solution and related fields if exampleSolution exists on newExercise,
     * otherwise clears the previously set example solution related fields.
     *
     * @param newExercise Exercise model that may have an exampleSolution.
     */
    showIfExampleSolutionPresent(newExercise: Exercise) {
        // Clear fields below to avoid displaying old data if this method is called more than once.
        this.modelingExercise = undefined;
        this.exampleSolution = undefined;
        this.exampleSolutionUML = undefined;
        this.isProgrammingExerciseExampleSolutionPublished = false;

        if (newExercise.type === ExerciseType.MODELING) {
            this.modelingExercise = newExercise as ModelingExercise;
            if (this.modelingExercise.exampleSolutionModel) {
                this.exampleSolutionUML = JSON.parse(this.modelingExercise.exampleSolutionModel);
            }
        } else if (newExercise.type === ExerciseType.TEXT || newExercise.type === ExerciseType.FILE_UPLOAD) {
            const exercise = newExercise as TextExercise & FileUploadExercise;
            if (exercise.exampleSolution) {
                this.exampleSolution = this.artemisMarkdown.safeHtmlForMarkdown(exercise.exampleSolution);
            }
        } else if (newExercise.type === ExerciseType.PROGRAMMING) {
            const exercise = newExercise as ProgrammingExercise;
            this.isProgrammingExerciseExampleSolutionPublished = exercise.exampleSolutionPublished || false;
        }
        // For TAs the example solution is collapsed on default to avoid spoiling, as the example solution is always shown to TAs
        this.exampleSolutionCollapsed = !!this.exercise?.isAtLeastTutor;
    }

    /**
     * Filter for participations that belong to the current user only. Additionally, we make sure that all results that are not finished (i.e. completionDate is not set) are
     * removed from the participations. We also sort the participations so that FINISHED participations come first.
     */
    private filterParticipations(participations?: StudentParticipation[]): StudentParticipation[] {
        if (!participations) {
            return [];
        }
        const filteredParticipations = participations.filter((participation: StudentParticipation) => {
            const personal = participation.student?.id === this.currentUser.id;
            const team = participation.team?.students?.map((s) => s.id).includes(this.currentUser.id);
            return personal || team;
        });
        filteredParticipations.forEach((participation: Participation) => {
            if (participation.results) {
                participation.results = participation.results.filter((result: Result) => result.completionDate);
            }
        });
        this.sortParticipationsFinishedFirst(filteredParticipations);
        return filteredParticipations;
    }

    /**
     * Sort the given participations so that FINISHED participations come first.
     *
     * Note, that this function directly operates on the array passed as argument and does not return anything.
     */
    private sortParticipationsFinishedFirst(participations: StudentParticipation[]) {
        if (participations && participations.length > 1) {
            participations.sort((a, b) => (b.initializationState === InitializationState.FINISHED ? 1 : -1));
        }
    }

    sortResults() {
        if (this.studentParticipation && this.studentParticipation.results) {
            this.studentParticipation.results = this.studentParticipation.results.sort(this.resultSortFunction);
            const sortedResultLength = this.studentParticipation.results.length;
            const startingElement = Math.max(sortedResultLength - MAX_RESULT_HISTORY_LENGTH, 0);
            this.sortedHistoryResult = this.studentParticipation.results.slice(startingElement, sortedResultLength);
        }
    }

    private resultSortFunction = (a: Result, b: Result) => {
        const aValue = dayjs(a.completionDate!).valueOf();
        const bValue = dayjs(b.completionDate!).valueOf();
        return aValue - bValue;
    };

    mergeResultsAndSubmissionsForParticipations() {
        // if there are new student participation(s) from the server, we need to update this.studentParticipation
        if (this.exercise) {
            if (this.exercise.studentParticipations && this.exercise.studentParticipations.length > 0) {
                this.studentParticipation = this.participationService.mergeStudentParticipations(this.exercise.studentParticipations);
                this.sortResults();
                // Add exercise to studentParticipation, as the result component is dependent on its existence.
                if (this.studentParticipation && this.studentParticipation.exercise == undefined) {
                    this.studentParticipation.exercise = this.exercise;
                }
            } else if (this.studentParticipation) {
                // otherwise we make sure that the student participation in exercise is correct
                this.exercise.studentParticipations = [this.studentParticipation];
            }
        }
    }

    subscribeForNewResults() {
        if (this.exercise && this.exercise.studentParticipations && this.exercise.studentParticipations.length > 0) {
            this.exercise.studentParticipations.forEach((participation) => {
                this.participationWebsocketService.addParticipation(participation, this.exercise!);
            });
            if (this.latestRatedResult) {
                if (this.latestRatedResult.successful) {
                    this.guidedTourService.enableTourForExercise(this.exercise, programmingExerciseSuccess, true);
                } else if (this.latestRatedResult.hasFeedback && !this.latestRatedResult.successful) {
                    this.guidedTourService.enableTourForExercise(this.exercise, programmingExerciseFail, true);
                }
            }
        }
        this.participationUpdateListener = this.participationWebsocketService.subscribeForParticipationChanges().subscribe((changedParticipation: StudentParticipation) => {
            if (changedParticipation && this.exercise && changedParticipation.exercise?.id === this.exercise.id) {
                // Notify student about late submission result
                if (
                    changedParticipation.exercise?.dueDate &&
                    hasExerciseDueDatePassed(changedParticipation.exercise!, changedParticipation) &&
                    changedParticipation.results?.length! > this.studentParticipation?.results?.length!
                ) {
                    this.alertService.success('artemisApp.exercise.lateSubmissionResultReceived');
                }
                this.exercise.studentParticipations =
                    this.exercise.studentParticipations && this.exercise.studentParticipations.length > 0
                        ? this.exercise.studentParticipations.map((el) => (el.id === changedParticipation.id ? changedParticipation : el))
                        : [changedParticipation];
                this.mergeResultsAndSubmissionsForParticipations();

                if (ExerciseType.PROGRAMMING === this.exercise?.type) {
                    this.exerciseHintService.getActivatedExerciseHints(this.exerciseId).subscribe((activatedRes?: HttpResponse<ExerciseHint[]>) => {
                        this.activatedExerciseHints = activatedRes!.body!;

                        this.exerciseHintService.getAvailableExerciseHints(this.exerciseId).subscribe((availableRes?: HttpResponse<ExerciseHint[]>) => {
                            // filter out the activated hints from the available hints
                            this.availableExerciseHints = availableRes!.body!.filter(
                                (availableHint) => !this.activatedExerciseHints.some((activatedHint) => availableHint.id === activatedHint.id),
                            );
                            const filteredAvailableExerciseHints = this.availableExerciseHints.filter((hint) => hint.displayThreshold !== 0);
                            if (filteredAvailableExerciseHints.length) {
                                this.alertService.info('artemisApp.exerciseHint.availableHintsAlertMessage', {
                                    taskName: filteredAvailableExerciseHints.first()?.programmingExerciseTask?.taskName,
                                });
                            }
                        });
                    });
                }
            }
        });
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
                this.exercise!.participationStatus = participationStatus(this.exercise!);
            });
    }

    /**
     * Subscribe for incoming (late) submissions to show a message if the student submitted after the due date.
     */
    subscribeForNewSubmissions() {
        if (this.studentParticipation) {
            this.submissionSubscription = this.submissionService
                .getLatestPendingSubmissionByParticipationId(this.studentParticipation!.id!, this.exercise?.id!, true)
                .subscribe(({ submission }) => {
                    // Notify about received late submission
                    if (submission && getExerciseDueDate(this.exercise!, this.studentParticipation)?.isBefore(submission.submissionDate)) {
                        this.alertService.success('artemisApp.exercise.lateSubmissionReceived');
                    }
                });
        }
    }

    /**
     * Navigates to the previous page or, if no previous navigation happened, to the courses exercise overview
     */
    backToCourse() {
        this.navigationUtilService.navigateBack(['courses', this.courseId.toString(), 'exercises']);
    }

    exerciseRatedBadge(result: Result): string {
        return result.rated ? 'bg-success' : 'bg-info';
    }

    get hasMoreResults(): boolean {
        if (!this.studentParticipation || !this.studentParticipation.results) {
            return false;
        }
        return this.studentParticipation.results.length > MAX_RESULT_HISTORY_LENGTH;
    }

    get showResults(): boolean {
        if (this.exercise!.type === ExerciseType.MODELING || this.exercise!.type === ExerciseType.TEXT) {
            return this.hasResults && this.isAfterAssessmentDueDate;
        }
        return this.hasResults;
    }

    get hasResults(): boolean {
        if (!this.studentParticipation || !this.studentParticipation.results) {
            return false;
        }
        return this.studentParticipation.results.length > 0;
    }

    /**
     * Returns the latest finished result for modeling and text exercises. It does not have to be rated.
     * For other exercise types it returns a rated result.
     */
    getLatestRatedResult() {
        if (!this.studentParticipation?.submissions || !this.studentParticipation!.submissions![0] || !this.hasResults) {
            return;
        }
        this.complaintService.findBySubmissionId(this.studentParticipation!.submissions![0].id!).subscribe({
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
            return this.studentParticipation?.results?.find((result: Result) => !!result.completionDate) || undefined;
        }

        const ratedResults = this.studentParticipation?.results?.filter((result: Result) => result.rated).sort(this.resultSortFunction);
        if (ratedResults) {
            const latestResult = ratedResults.last();
            if (latestResult) {
                latestResult.participation = this.studentParticipation;
            }
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

    /**
     * This function gets called if the router outlet gets activated. This is
     * used only for the DiscussionComponent
     * @param instance The component instance
     */
    onChildActivate(instance: DiscussionSectionComponent) {
        this.discussionComponent = instance; // save the reference to the component instance
        if (this.exercise) {
            instance.exercise = this.exercise;
        }
    }

    private onError(error: string) {
        this.alertService.error(error);
    }

    onHintActivated(exerciseHint: ExerciseHint) {
        this.availableExerciseHints = this.availableExerciseHints.filter((hint) => hint.id !== exerciseHint.id);
        this.activatedExerciseHints.push(exerciseHint);
    }

    /**
     * Used to change the boolean value for the example solution dropdown menu
     */
    changeExampleSolution() {
        this.exampleSolutionCollapsed = !this.exampleSolutionCollapsed;
    }
}
