import { Component, OnDestroy, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { filter } from 'rxjs/operators';
import { Result } from 'app/entities/result.model';
import * as moment from 'moment';
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
import { Exercise, ExerciseCategory, ExerciseType, ParticipationStatus } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { participationStatus } from 'app/exercises/shared/exercise/exercise-utils';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { CourseExerciseSubmissionResultSimulationService } from 'app/course/manage/course-exercise-submission-result-simulation.service';
import { ProgrammingExerciseSimulationUtils } from 'app/exercises/programming/shared/utils/programming-exercise-simulation-utils';
import { JhiAlertService } from 'ng-jhipster';
import { ProgrammingExerciseSimulationService } from 'app/exercises/programming/manage/services/programming-exercise-simulation.service';
import { TeamAssignmentPayload } from 'app/entities/team.model';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { QuizStatus, QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { StudentQuestionsComponent } from 'app/overview/student-questions/student-questions.component';
const MAX_RESULT_HISTORY_LENGTH = 5;

@Component({
    selector: 'jhi-course-exercise-details',
    templateUrl: './course-exercise-details.component.html',
    styleUrls: ['../course-overview.scss'],
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
    public showMoreResults = false;
    public sortedHistoryResult: Result[]; // might be a subset of the actual results in combinedParticipation.results
    public exerciseCategories: ExerciseCategory[];
    private participationUpdateListener: Subscription;
    private teamAssignmentUpdateListener: Subscription;
    studentParticipation?: StudentParticipation;
    isAfterAssessmentDueDate: boolean;
    public gradingCriteria: GradingCriterion[];
    showWelcomeAlert = false;
    private studentQuestions?: StudentQuestionsComponent;

    /**
     * variables are only for testing purposes(noVersionControlAndContinuousIntegrationAvailable)
     */
    public inProductionEnvironment: boolean;
    public noVersionControlAndContinuousIntegrationServerAvailable = false;
    public wasSubmissionSimulated = false;

    constructor(
        private $location: Location,
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
        private courseExerciseSubmissionResultSimulationService: CourseExerciseSubmissionResultSimulationService,
        private programmingExerciseSimulationUtils: ProgrammingExerciseSimulationUtils,
        private jhiAlertService: JhiAlertService,
        private programmingExerciseSimulationService: ProgrammingExerciseSimulationService,
        private teamService: TeamService,
        private quizExerciseService: QuizExerciseService,
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

        this.route.queryParams.subscribe((queryParams) => {
            if (queryParams['welcome'] === '') {
                setTimeout(() => {
                    this.showWelcomeAlert = true;
                }, 500);
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
    }

    loadExercise() {
        this.exercise = undefined;
        this.studentParticipation = this.participationWebsocketService.getParticipationForExercise(this.exerciseId);
        this.exerciseService.getExerciseDetails(this.exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
            this.handleNewExercise(exerciseResponse.body!);
        });
    }

    handleNewExercise(newExercise: Exercise) {
        this.exercise = newExercise;
        this.exercise.studentParticipations = this.filterParticipations(newExercise.studentParticipations);
        this.mergeResultsAndSubmissionsForParticipations();
        this.exercise.participationStatus = participationStatus(this.exercise);
        this.isAfterAssessmentDueDate = !this.exercise.assessmentDueDate || moment().isAfter(this.exercise.assessmentDueDate);
        this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.exercise);

        // This is only needed in the local environment
        if (!this.inProductionEnvironment && this.exercise.type === ExerciseType.PROGRAMMING && (<ProgrammingExercise>this.exercise).isLocalSimulation) {
            this.noVersionControlAndContinuousIntegrationServerAvailable = true;
        }

        this.subscribeForNewResults();
        this.subscribeToTeamAssignmentUpdates();

        if (this.studentQuestions && this.exercise) {
            // We need to manually update the exercise property of the student questions component
            this.studentQuestions.exercise = this.exercise;
            this.studentQuestions.loadQuestions(); // reload the student questions
        }
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
        const aValue = moment(a.completionDate!).valueOf();
        const bValue = moment(b.completionDate!).valueOf();
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
                this.exercise.studentParticipations =
                    this.exercise.studentParticipations && this.exercise.studentParticipations.length > 0
                        ? this.exercise.studentParticipations.map((el) => {
                              return el.id === changedParticipation.id ? changedParticipation : el;
                          })
                        : [changedParticipation];
                this.mergeResultsAndSubmissionsForParticipations();
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

    backToCourse() {
        this.$location.back();
    }

    exerciseRatedBadge(result: Result): string {
        return result.rated ? 'badge-success' : 'badge-info';
    }

    get exerciseIsOver(): boolean {
        return this.exercise ? moment(this.exercise!.dueDate!).isBefore(moment()) : true;
    }

    get hasMoreResults(): boolean {
        if (!this.studentParticipation || !this.studentParticipation.results) {
            return false;
        }
        return this.studentParticipation.results.length > MAX_RESULT_HISTORY_LENGTH;
    }

    get exerciseRouterLink(): string | null {
        if (this.exercise && [ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD].includes(this.exercise.type!)) {
            return `/course-management/${this.courseId}/${this.exercise.type}-exercises/${this.exercise!.id}/assessment`;
        }

        return null;
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
    get latestRatedResult() {
        if (!this.studentParticipation || !this.hasResults) {
            return undefined;
        }

        if (this.exercise!.type === ExerciseType.MODELING || this.exercise!.type === ExerciseType.TEXT) {
            return this.studentParticipation?.results?.find((result: Result) => !!result.completionDate) || undefined;
        }

        const ratedResults = this.studentParticipation?.results?.filter((result: Result) => result.rated).sort(this.resultSortFunction);
        if (ratedResults) {
            const latestResult = ratedResults.length ? ratedResults[ratedResults.length - 1] : undefined;
            if (latestResult) {
                latestResult.participation = this.studentParticipation;
            }
            return latestResult;
        }
    }

    publishBuildPlanUrl() {
        return (this.exercise as ProgrammingExercise).publishBuildPlanUrl;
    }

    buildPlanActive() {
        return (
            !!this.exercise &&
            this.exercise.studentParticipations &&
            this.exercise.studentParticipations.length > 0 &&
            this.exercise.studentParticipations[0].initializationState !== InitializationState.INACTIVE
        );
    }

    projectKey(): string {
        return (this.exercise as ProgrammingExercise).projectKey!;
    }

    buildPlanId(participation: Participation) {
        return (participation! as ProgrammingExerciseStudentParticipation).buildPlanId;
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
     * used only for the StudentQuestionsComponent
     * @param instance The component instance
     */
    onChildActivate(instance: StudentQuestionsComponent) {
        this.studentQuestions = instance; // save the reference to the component instance
        if (this.exercise) {
            instance.exercise = this.exercise;
            instance.loadQuestions(); // reload the student questions
        }
    }

    // ################## ONLY FOR LOCAL TESTING PURPOSE -- START ##################

    /**
     * Triggers the simulation of a participation and submission for the currently logged in user
     * This method will fail if used in production
     * This functionality is only for testing purposes(noVersionControlAndContinuousIntegrationAvailable)
     */
    simulateSubmission() {
        this.programmingExerciseSimulationService.failsIfInProduction(this.inProductionEnvironment);
        this.courseExerciseSubmissionResultSimulationService.simulateSubmission(this.exerciseId).subscribe(
            () => {
                this.wasSubmissionSimulated = true;
                this.jhiAlertService.success('artemisApp.exercise.submissionSuccessful');
            },
            () => {
                this.jhiAlertService.error('artemisApp.exercise.submissionUnsuccessful');
            },
        );
    }

    /**
     * Triggers the simulation of a result for the currently logged in user
     * This method will fail if used in production
     * This functionality is only for testing purposes(noVersionControlAndContinuousIntegrationAvailable)
     */
    simulateResult() {
        this.programmingExerciseSimulationService.failsIfInProduction(this.inProductionEnvironment);
        this.courseExerciseSubmissionResultSimulationService.simulateResult(this.exerciseId).subscribe(
            (result) => {
                // set the value to false in order to deactivate the result button
                this.wasSubmissionSimulated = false;

                // set these values in order to visualize the simulated result on the exercise details page
                this.exercise!.participationStatus = ParticipationStatus.EXERCISE_SUBMITTED;
                this.studentParticipation = <StudentParticipation>result.body!.participation;
                this.studentParticipation.results = [];
                this.studentParticipation.results[0] = result.body!;

                this.jhiAlertService.success('artemisApp.exercise.resultCreationSuccessful');
            },
            () => {
                this.jhiAlertService.error('artemisApp.exercise.resultCreationUnsuccessful');
            },
        );
    }

    // ################## ONLY FOR LOCAL TESTING PURPOSE -- END ##################
}
