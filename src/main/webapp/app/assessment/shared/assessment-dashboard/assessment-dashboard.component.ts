import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { HttpResponse } from '@angular/common/http';
import { Exercise, IncludedInOverallScore, getIcon, getIconTooltip } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StatsForDashboard } from 'app/assessment/shared/assessment-dashboard/stats-for-dashboard.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { FilterProp as TeamFilterProp } from 'app/exercise/team/teams/teams.component';
import { SortService } from 'app/shared/service/sort.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { AssessmentDashboardInformationComponent, AssessmentDashboardInformationEntry } from './assessment-dashboard-information.component';
import { TutorLeaderboardElement } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.model';
import { faClipboard, faHeartBroken, faSort, faTable } from '@fortawesome/free-solid-svg-icons';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TutorParticipationGraphComponent } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.component';
import { TutorLeaderboardComponent } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.component';
import { NotReleasedTagComponent } from 'app/shared/components/not-released-tag/not-released-tag.component';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { ExamAssessmentButtonsComponent } from 'app/assessment/shared/assessment-dashboard/exam-assessment-buttons/exam-assessment-buttons.component';
import { TutorIssue, TutorIssueComplaintsChecker, TutorIssueRatingChecker, TutorIssueScoreChecker } from 'app/assessment/shared/assessment-dashboard/tutor-issue';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { SecondCorrectionEnableButtonComponent } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/second-correction-button/second-correction-enable-button.component';
import { MODULE_FEATURE_PLAGIARISM } from 'app/app.constants';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';
import { CourseTitleBarActionsDirective } from 'app/core/course/shared/directives/course-title-bar-actions.directive';
import { CourseTitleBarTitleComponent } from 'app/core/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseTitleBarTitleDirective } from 'app/core/course/shared/directives/course-title-bar-title.directive';

@Component({
    selector: 'jhi-assessment-dashboard',
    templateUrl: './assessment-dashboard.component.html',
    styleUrls: ['./exam-assessment-buttons/exam-assessment-buttons.component.scss'],
    providers: [CourseManagementService],
    imports: [
        RouterLink,
        FaIconComponent,
        TranslateDirective,
        ExamAssessmentButtonsComponent,
        AssessmentDashboardInformationComponent,
        FormsModule,
        NgbTooltip,
        ArtemisTranslatePipe,
        SecondCorrectionEnableButtonComponent,
        TutorParticipationGraphComponent,
        TutorLeaderboardComponent,
        NotReleasedTagComponent,
        DocumentationButtonComponent,
        ArtemisTimeAgoPipe,
        ArtemisDatePipe,
        SortDirective,
        SortByDirective,
        FeatureOverlayComponent,
        CourseTitleBarActionsDirective,
        CourseTitleBarTitleComponent,
        CourseTitleBarTitleDirective,
    ],
})
export class AssessmentDashboardComponent implements OnInit {
    private courseService = inject(CourseManagementService);
    private exerciseService = inject(ExerciseService);
    private examManagementService = inject(ExamManagementService);
    private alertService = inject(AlertService);
    private accountService = inject(AccountService);
    private route = inject(ActivatedRoute);
    private sortService = inject(SortService);
    private profileService = inject(ProfileService);

    readonly TeamFilterProp = TeamFilterProp;
    readonly documentationType: DocumentationType = 'Assessment';

    plagiarismEnabled = false;

    course: Course;
    exam: Exam;
    courseId: number;
    examId: number;
    exerciseGroupId: number;
    allExercises: Exercise[] = [];
    currentlyShownExercises: Exercise[] = [];
    numberOfSubmissions = new DueDateStat();
    totalNumberOfAssessments = new DueDateStat();
    numberOfAssessmentsOfCorrectionRounds = [new DueDateStat()];
    numberOfCorrectionRounds = 1;
    numberOfTutorAssessments = 0;

    complaints = new AssessmentDashboardInformationEntry(0, 0);
    moreFeedbackRequests = new AssessmentDashboardInformationEntry(0, 0);
    assessmentLocks = new AssessmentDashboardInformationEntry(0, 0);
    ratings = new AssessmentDashboardInformationEntry(0, 0);

    totalAssessmentPercentage = 0;
    hideFinishedExercises = true;
    hideOptional = false;

    stats = new StatsForDashboard();

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    exercisesSortingPredicate = 'assessmentDueDate';
    exercisesReverseOrder = false;

    tutor: User;

    isExamMode = false;
    isTestRun = false;

    tutorIssues: TutorIssue[] = [];

    isTogglingSecondCorrection: Map<number, boolean> = new Map<number, boolean>();

    // Icons
    faSort = faSort;
    faTable = faTable;
    faClipboard = faClipboard;
    faHeartBroken = faHeartBroken;

    /**
     * On init set the courseID, load all exercises and statistics for tutors and set the identity for the AccountService.
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        this.isExamMode = !!this.examId;
        if (this.isExamMode) {
            this.isTestRun = this.route.snapshot.url[1]?.toString() === 'test-runs';
            this.exerciseGroupId = Number(this.route.snapshot.paramMap.get('exerciseGroupId'));
        }
        this.loadAll();
        this.accountService.identity().then((user) => (this.tutor = user!));
        this.plagiarismEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_PLAGIARISM);
    }

    /**
     * Load all exercises and statistics for tutors of this course.
     * Percentages are calculated and rounded towards zero.
     */
    loadAll() {
        if (this.isExamMode) {
            this.hideFinishedExercises = false;
            this.examManagementService.getExamWithInterestingExercisesForAssessmentDashboard(this.courseId, this.examId, this.isTestRun).subscribe((res: HttpResponse<Exam>) => {
                this.exam = res.body!;
                this.course = Course.from(this.exam.course!);
                this.accountService.setAccessRightsForCourse(this.course);

                // No exercises exist yet
                if (!this.exam.exerciseGroups) {
                    return;
                }

                // get all exercises
                const exercises: Exercise[] = [];
                this.exam.exerciseGroups!.forEach((exerciseGroup) => {
                    if (exerciseGroup.exercises) {
                        exercises.push(...exerciseGroup.exercises);

                        // Set the exercise group since it is undefined by default here
                        exerciseGroup.exercises.forEach((exercise: Exercise) => {
                            exercise.exerciseGroup = exerciseGroup;
                        });
                    }
                });

                this.extractExercises(exercises);
            });
            this.examManagementService.getStatsForExamAssessmentDashboard(this.courseId, this.examId).subscribe({
                next: (res: HttpResponse<StatsForDashboard>) => {
                    this.stats = StatsForDashboard.from(res.body!);
                    this.numberOfSubmissions = this.stats.numberOfSubmissions;
                    this.numberOfAssessmentsOfCorrectionRounds = this.stats.numberOfAssessmentsOfCorrectionRounds;

                    this.totalNumberOfAssessments = new DueDateStat();
                    for (const dueDateStat of this.numberOfAssessmentsOfCorrectionRounds) {
                        this.totalNumberOfAssessments.inTime += dueDateStat.inTime;
                    }
                    this.numberOfCorrectionRounds = this.numberOfAssessmentsOfCorrectionRounds.length;

                    const tutorLeaderboardEntry = this.stats.tutorLeaderboardEntries?.find((entry) => entry.userId === this.tutor.id);
                    this.sortService.sortByProperty(this.stats.tutorLeaderboardEntries, 'points', false);
                    if (tutorLeaderboardEntry) {
                        this.numberOfTutorAssessments = tutorLeaderboardEntry.numberOfAssessments;
                        this.complaints = new AssessmentDashboardInformationEntry(
                            this.stats.numberOfComplaints,
                            tutorLeaderboardEntry.numberOfTutorComplaints,
                            this.stats.numberOfComplaints - this.stats.numberOfOpenComplaints,
                        );
                    } else {
                        this.numberOfTutorAssessments = 0;
                        this.complaints = new AssessmentDashboardInformationEntry(
                            this.stats.numberOfComplaints,
                            0,
                            this.stats.numberOfComplaints - this.stats.numberOfOpenComplaints,
                        );
                    }
                    this.assessmentLocks = new AssessmentDashboardInformationEntry(this.stats.totalNumberOfAssessmentLocks, this.stats.numberOfAssessmentLocks);

                    if (this.numberOfSubmissions.total > 0) {
                        this.totalAssessmentPercentage = Math.floor((this.totalNumberOfAssessments.total / (this.numberOfSubmissions.total * this.numberOfCorrectionRounds)) * 100);
                    }
                    this.computeIssuesWithTutorPerformance();
                },
                error: (response: string) => this.onError(response),
            });
        } else {
            this.courseService.getCourseWithInterestingExercisesForTutors(this.courseId).subscribe({
                next: (res: HttpResponse<Course>) => {
                    this.course = Course.from(res.body!);
                    this.extractExercises(this.course.exercises);
                },
                error: (response: string) => this.onError(response),
            });

            this.courseService.getStatsForTutors(this.courseId).subscribe({
                next: (res: HttpResponse<StatsForDashboard>) => {
                    this.stats = StatsForDashboard.from(res.body!);
                    this.numberOfSubmissions = this.stats.numberOfSubmissions;
                    this.totalNumberOfAssessments = this.stats.totalNumberOfAssessments;
                    this.numberOfAssessmentsOfCorrectionRounds = this.stats.numberOfAssessmentsOfCorrectionRounds;
                    const tutorLeaderboardEntry = this.stats.tutorLeaderboardEntries?.find((entry) => entry.userId === this.tutor.id);
                    this.sortService.sortByProperty(this.stats.tutorLeaderboardEntries, 'points', false);
                    if (tutorLeaderboardEntry) {
                        this.numberOfTutorAssessments = tutorLeaderboardEntry.numberOfAssessments;

                        this.complaints = new AssessmentDashboardInformationEntry(
                            this.stats.numberOfComplaints,
                            tutorLeaderboardEntry.numberOfTutorComplaints,
                            this.stats.numberOfComplaints - this.stats.numberOfOpenComplaints,
                        );
                        this.moreFeedbackRequests = new AssessmentDashboardInformationEntry(
                            this.stats.numberOfMoreFeedbackRequests,
                            tutorLeaderboardEntry.numberOfTutorMoreFeedbackRequests,
                            this.stats.numberOfMoreFeedbackRequests - this.stats.numberOfOpenMoreFeedbackRequests,
                        );
                        this.ratings = new AssessmentDashboardInformationEntry(this.stats.numberOfRatings, tutorLeaderboardEntry.numberOfTutorRatings);
                    } else {
                        this.numberOfTutorAssessments = 0;
                        this.complaints = new AssessmentDashboardInformationEntry(
                            this.stats.numberOfComplaints,
                            0,
                            this.stats.numberOfComplaints - this.stats.numberOfOpenComplaints,
                        );
                        this.moreFeedbackRequests = new AssessmentDashboardInformationEntry(
                            this.stats.numberOfMoreFeedbackRequests,
                            0,
                            this.stats.numberOfMoreFeedbackRequests - this.stats.numberOfOpenMoreFeedbackRequests,
                        );
                        this.ratings = new AssessmentDashboardInformationEntry(this.stats.numberOfRatings, 0);
                    }
                    this.assessmentLocks = new AssessmentDashboardInformationEntry(this.stats.totalNumberOfAssessmentLocks, this.stats.numberOfAssessmentLocks);

                    if (this.numberOfSubmissions.total > 0) {
                        this.totalAssessmentPercentage = Math.floor((this.totalNumberOfAssessments.total / this.numberOfSubmissions.total) * 100);
                    }

                    this.computeIssuesWithTutorPerformance();
                },
                error: (response: string) => this.onError(response),
            });
        }
    }

    /**
     * Computes performance issues for every tutor based on its rating, score, and number of complaints when compared to the average tutor stats
     */
    computeIssuesWithTutorPerformance(): void {
        // clear the tutor issues array
        this.tutorIssues = [];

        const complaintRatio = (entry: TutorLeaderboardElement) => {
            if (entry.numberOfAssessments === 0) {
                return 0;
            }
            return (100 * entry.numberOfTutorComplaints) / entry.numberOfAssessments;
        };

        const courseInformation = this.stats.tutorLeaderboardEntries.reduce(
            (accumulator, entry) => {
                return {
                    summedAverageRatings: accumulator.summedAverageRatings + entry.averageRating,
                    summedAverageScore: accumulator.summedAverageScore + entry.averageScore,
                    summedComplaintRatio: accumulator.summedComplaintRatio + complaintRatio(entry),
                };
            },
            { summedAverageRatings: 0, summedAverageScore: 0, summedComplaintRatio: 0 },
        );

        const numberOfTutorsWithNonZeroRatings = this.stats.tutorLeaderboardEntries.filter((entry) => entry.averageRating > 0).length;
        const numberOfTutorsWithNonZeroAssessments = this.stats.tutorLeaderboardEntries.filter((entry) => entry.numberOfAssessments > 0).length;

        this.stats.tutorLeaderboardEntries
            // create the tutor issue checkers for rating, score and complaints
            .flatMap((entry) => [
                new TutorIssueRatingChecker(
                    entry.numberOfTutorRatings,
                    entry.averageRating,
                    courseInformation.summedAverageRatings / numberOfTutorsWithNonZeroRatings,
                    entry.name,
                    entry.userId,
                ),
                new TutorIssueScoreChecker(
                    entry.numberOfAssessments,
                    entry.averageScore,
                    courseInformation.summedAverageScore / numberOfTutorsWithNonZeroAssessments,
                    entry.name,
                    entry.userId,
                ),
                new TutorIssueComplaintsChecker(
                    entry.numberOfTutorComplaints,
                    complaintRatio(entry),
                    courseInformation.summedComplaintRatio / numberOfTutorsWithNonZeroAssessments,
                    entry.name,
                    entry.userId,
                ),
            ])
            // run every checker to see if the tutor value is within the allowed threshold
            .filter((checker) => checker.isPerformanceIssue)
            // create tutor issue
            .map((checker) => checker.toIssue())
            .forEach((issue) => {
                // mark tutor with performance issues
                const tutorEntry = this.stats.tutorLeaderboardEntries.find((entry) => entry.userId === issue.tutorId);
                tutorEntry!.hasIssuesWithPerformance = true;

                // add issue to the issues list
                this.tutorIssues.push(issue);
            });
    }

    /**
     * Divides exercises into finished and unfinished exercises.
     * @param exercises - the exercises that should get filtered
     */
    private extractExercises(exercises?: Exercise[]) {
        if (exercises && exercises.length > 0) {
            this.allExercises = exercises;
            this.currentlyShownExercises = this.getUnfinishedExercises(exercises);
            // sort exercises by type to get a better overview in the dashboard
            this.sortService.sortByProperty(this.currentlyShownExercises, 'type', true);
            this.initIsTogglingSecondCorrection();
            this.updateExercises();
        }
    }

    /**
     * Initiates the map that contains the current toggling state (false) for each exercise.
     */
    private initIsTogglingSecondCorrection() {
        this.allExercises.forEach((exercise) => {
            this.isTogglingSecondCorrection.set(exercise.id!, false);
        });
    }

    private getUnfinishedExercises(exercises?: Exercise[]) {
        const filteredExercises = exercises?.filter(
            (exercise) =>
                (!exercise.allowComplaintsForAutomaticAssessments && this.hasUnfinishedAssessments(exercise)) ||
                exercise.numberOfOpenComplaints !== 0 ||
                exercise.numberOfOpenMoreFeedbackRequests !== 0,
        );

        return filteredExercises ? filteredExercises : [];
    }

    private hasUnfinishedAssessments(exercise: Exercise): boolean {
        return (
            exercise.numberOfAssessmentsOfCorrectionRounds?.map((round) => round.inTime !== exercise.numberOfSubmissions?.inTime).reduce((acc, cur) => acc || cur) ||
            exercise.totalNumberOfAssessments?.inTime !== exercise.numberOfSubmissions?.inTime
        );
    }

    /**
     * Toggle the option to show finished exercises.
     */
    triggerFinishedExercises() {
        this.hideFinishedExercises = !this.hideFinishedExercises;
        this.updateExercises();
    }

    /**
     * Toggle the option to hide optional exercises.
     */
    triggerOptionalExercises() {
        this.hideOptional = !this.hideOptional;
        this.updateExercises();
    }

    /**
     * update the exercise array based on the option show finished exercises
     */
    updateExercises() {
        this.currentlyShownExercises = this.hideFinishedExercises ? this.getUnfinishedExercises(this.allExercises) : this.allExercises;
        if (this.hideOptional) {
            this.currentlyShownExercises = this.currentlyShownExercises.filter((exercise) => exercise.includedInOverallScore !== IncludedInOverallScore.NOT_INCLUDED);
        }
    }

    /**
     * Pass on an error to the browser console and the alertService.
     * @param error
     */
    private onError(error: string) {
        this.alertService.error(error);
    }

    sortRows() {
        this.sortService.sortByProperty(this.currentlyShownExercises, this.exercisesSortingPredicate, this.exercisesReverseOrder);
    }

    toggleSecondCorrection(exerciseId: number) {
        const currentExercise = this.currentlyShownExercises.find((exercise) => exercise.id === exerciseId)!;
        this.isTogglingSecondCorrection.set(currentExercise.id!, true);
        const index = this.currentlyShownExercises.indexOf(currentExercise);
        this.exerciseService.toggleSecondCorrection(exerciseId).subscribe({
            next: (res: boolean) => {
                this.currentlyShownExercises[index].secondCorrectionEnabled = !this.currentlyShownExercises[index].secondCorrectionEnabled;
                currentExercise!.secondCorrectionEnabled = res as boolean;
                this.isTogglingSecondCorrection.set(currentExercise.id!, false);
            },
            error: (err: string) => {
                this.onError(err);
            },
        });
    }

    getAssessmentDashboardLinkForExercise(exercise: Exercise): string[] {
        if (this.isExamMode) {
            return [
                '/course-management',
                this.courseId.toString(),
                'exams',
                this.examId.toString(),
                this.isTestRun ? 'test-assessment-dashboard' : 'assessment-dashboard',
                exercise.id!.toString(),
            ];
        } else {
            return ['/course-management', this.courseId.toString(), 'assessment-dashboard', exercise.id!.toString()];
        }
    }

    asQuizExercise(exercise: Exercise): QuizExercise {
        return exercise as QuizExercise;
    }
}
