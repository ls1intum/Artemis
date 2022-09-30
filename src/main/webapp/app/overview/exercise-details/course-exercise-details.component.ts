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
import { Participation } from 'app/entities/participation/participation.model';
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
import { faAngleDown, faAngleUp, faBook, faEye, faFileSignature, faListAlt, faSignal, faTable, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TextExercise } from 'app/entities/text-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/shared/exercise-hint.service';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';
import { PlagiarismCaseInfo } from 'app/exercises/shared/plagiarism/types/PlagiarismCaseInfo';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { MAX_RESULT_HISTORY_LENGTH } from 'app/overview/result-history/result-history.component';

@Component({
    selector: 'jhi-course-exercise-details',
    templateUrl: './course-exercise-details.component.html',
    styleUrls: ['../course-overview.scss', '../tab-bar/tab-bar.scss'],
})
export class CourseExerciseDetailsComponent implements OnInit, OnDestroy {
    readonly AssessmentType = AssessmentType;
    readonly PlagiarismVerdict = PlagiarismVerdict;
    readonly QuizStatus = QuizStatus;
    readonly QUIZ_ENDED_STATUS: (QuizStatus | undefined)[] = [QuizStatus.CLOSED, QuizStatus.OPEN_FOR_PRACTICE];
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;
    readonly TEXT = ExerciseType.TEXT;
    readonly FILE_UPLOAD = ExerciseType.FILE_UPLOAD;
    readonly evaluateBadge = ResultService.evaluateBadge;

    private currentUser: User;
    private exerciseId: number;
    public courseId: number;
    public exercise?: Exercise;
    public resultWithComplaint?: Result;
    public latestRatedResult?: Result;
    public complaint?: Complaint;
    public showMoreResults = false;
    public sortedHistoryResults: Result[];
    public exerciseCategories: ExerciseCategory[];
    private participationUpdateListener: Subscription;
    private teamAssignmentUpdateListener: Subscription;
    private submissionSubscription: Subscription;
    studentParticipations: StudentParticipation[] = [];
    gradedStudentParticipation?: StudentParticipation;
    practiceStudentParticipation?: StudentParticipation;
    isAfterAssessmentDueDate: boolean;
    allowComplaintsForAutomaticAssessments: boolean;
    public gradingCriteria: GradingCriterion[];
    private discussionComponent?: DiscussionSectionComponent;
    baseResource: string;
    isExamExercise: boolean;
    hasSubmissionPolicy: boolean;
    submissionPolicy: SubmissionPolicy;
    exampleSolutionCollapsed: boolean;
    plagiarismCaseInfo?: PlagiarismCaseInfo;
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

    // Icons
    faBook = faBook;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faListAlt = faListAlt;
    faSignal = faSignal;
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
        this.route.params.subscribe((params) => {
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
            if (this.studentParticipations) {
                this.studentParticipations.forEach((participation) => {
                    this.participationWebsocketService.unsubscribeForLatestResultOfParticipation(participation.id!, this.exercise!);
                });
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
        this.studentParticipations = this.participationWebsocketService.getParticipationsForExercise(this.exerciseId);
        this.gradedStudentParticipation = this.participationService.getSpecificStudentParticipation(this.studentParticipations, false);
        this.practiceStudentParticipation = this.participationService.getSpecificStudentParticipation(this.studentParticipations, true);
        this.resultWithComplaint = getFirstResultWithComplaintFromResults(this.gradedStudentParticipation?.results);
        this.exerciseService.getExerciseDetails(this.exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
            this.handleNewExercise(exerciseResponse.body!);
            this.getLatestRatedResult();
        });
        this.plagiarismCaseService.getPlagiarismCaseInfoForStudent(this.courseId, this.exerciseId).subscribe((res: HttpResponse<PlagiarismCaseInfo>) => {
            this.plagiarismCaseInfo = res.body ?? undefined;
        });
    }

    handleNewExercise(newExercise: Exercise) {
        this.exercise = newExercise;
        this.filterUnfinishedResults(this.exercise.studentParticipations);
        this.mergeResultsAndSubmissionsForParticipations();
        this.exercise.participationStatus = participationStatus(this.exercise);
        const now = dayjs();
        this.isAfterAssessmentDueDate = !this.exercise.assessmentDueDate || now.isAfter(this.exercise.assessmentDueDate);
        this.exerciseCategories = this.exercise.categories || [];
        this.allowComplaintsForAutomaticAssessments = false;

        if (this.exercise.type === ExerciseType.PROGRAMMING) {
            const programmingExercise = this.exercise as ProgrammingExercise;
            const isAfterDateForComplaint =
                !this.exercise.dueDate ||
                (hasExerciseDueDatePassed(this.exercise, this.gradedStudentParticipation) &&
                    (!programmingExercise.buildAndTestStudentSubmissionsAfterDueDate || now.isAfter(programmingExercise.buildAndTestStudentSubmissionsAfterDueDate)));

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
        if (this.exercise.type === ExerciseType.PROGRAMMING && hasExerciseDueDatePassed(this.exercise, this.gradedStudentParticipation)) {
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
     * Filters out any unfinished Results
     */
    private filterUnfinishedResults(participations?: StudentParticipation[]) {
        participations?.forEach((participation: Participation) => {
            if (participation.results) {
                participation.results = participation.results.filter((result: Result) => result.completionDate);
            }
        });
    }

    sortResults() {
        if (this.studentParticipations?.length) {
            this.studentParticipations.forEach((participation) => participation.results?.sort(this.resultSortFunction));
            this.sortedHistoryResults = this.studentParticipations.flatMap((participation) => participation.results ?? []).sort(this.resultSortFunction);
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
            this.gradedStudentParticipation = this.participationService.getSpecificStudentParticipation(this.studentParticipations, false);
            this.practiceStudentParticipation = this.participationService.getSpecificStudentParticipation(this.studentParticipations, true);
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
                    hasExerciseDueDatePassed(changedParticipation.exercise, changedParticipation) &&
                    changedParticipation.id === this.gradedStudentParticipation?.id &&
                    changedParticipation.results?.length! > this.gradedStudentParticipation?.results?.length!
                ) {
                    this.alertService.success('artemisApp.exercise.lateSubmissionResultReceived');
                }
                if (this.studentParticipations?.some((participation) => participation.id === changedParticipation.id)) {
                    this.exercise.studentParticipations = this.studentParticipations.map((participation) =>
                        participation.id === changedParticipation.id ? changedParticipation : participation,
                    );
                } else {
                    this.exercise.studentParticipations = [...this.studentParticipations, changedParticipation];
                }
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
        this.studentParticipations.forEach((participation) => {
            this.submissionSubscription = this.submissionService
                .getLatestPendingSubmissionByParticipationId(participation!.id!, this.exercise?.id!, true)
                .subscribe(({ submission }) => {
                    // Notify about received late submission
                    if (submission && !participation.testRun && getExerciseDueDate(this.exercise!, participation)?.isBefore(submission.submissionDate)) {
                        this.alertService.success('artemisApp.exercise.lateSubmissionReceived');
                    }
                });
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

    get showResults(): boolean {
        if (!this.sortedHistoryResults?.length) {
            return false;
        }

        if (this.exercise!.type === ExerciseType.MODELING || this.exercise!.type === ExerciseType.TEXT) {
            return this.isAfterAssessmentDueDate;
        } else {
            return true;
        }
    }

    /**
     * Returns the latest finished result for modeling and text exercises. It does not have to be rated.
     * For other exercise types it returns a rated result.
     */
    getLatestRatedResult() {
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
            return this.gradedStudentParticipation?.results?.find((result: Result) => !!result.completionDate) || undefined;
        }

        const ratedResults = this.gradedStudentParticipation?.results?.filter((result: Result) => result.rated).sort(this.resultSortFunction);
        if (ratedResults) {
            const latestResult = ratedResults.last();
            if (latestResult) {
                latestResult.participation = this.gradedStudentParticipation;
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
