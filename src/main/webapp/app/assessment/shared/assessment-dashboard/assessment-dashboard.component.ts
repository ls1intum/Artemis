import { Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AlertService } from 'app/foundation/service/alert.service';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { HttpResponse } from '@angular/common/http';
import { Exercise, IncludedInOverallScore, getIcon, getIconTooltip } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StatsForDashboard } from 'app/assessment/shared/assessment-dashboard/stats-for-dashboard.model';
import { Course } from 'app/course/shared/entities/course.model';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { FilterProp as TeamFilterProp } from 'app/exercise/team/teams/teams.component';
import { SortService } from 'app/foundation/service/sort.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { AssessmentDashboardInformationComponent, AssessmentDashboardInformationEntry } from './assessment-dashboard-information.component';
import { TutorLeaderboardElement } from 'app/exercise/dashboards/tutor-leaderboard/tutor-leaderboard.model';
import { faClipboard, faHeartBroken, faShieldAlt, faSort, faTable } from '@fortawesome/free-solid-svg-icons';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TutorParticipationGraphComponent } from 'app/exercise/dashboards/tutor-participation-graph/tutor-participation-graph.component';
import { TutorLeaderboardComponent } from 'app/exercise/dashboards/tutor-leaderboard/tutor-leaderboard.component';
import { NotReleasedTagComponent } from 'app/shared-ui/components/not-released-tag/not-released-tag.component';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { ExamAssessmentButtonsComponent } from 'app/assessment/shared/assessment-dashboard/exam-assessment-buttons/exam-assessment-buttons.component';
import { TutorIssue, TutorIssueComplaintsChecker, TutorIssueRatingChecker, TutorIssueScoreChecker } from 'app/assessment/shared/assessment-dashboard/tutor-issue';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { SecondCorrectionEnableButtonComponent } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/second-correction-button/second-correction-enable-button.component';
import { MODULE_FEATURE_PLAGIARISM } from 'app/app.constants';
import { FeatureOverlayComponent } from 'app/shared-ui/components/feature-overlay/feature-overlay.component';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { CourseTitleBarTitleComponent } from 'app/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { DeimosDateRangeModalComponent, DeimosDateRangeSelection } from 'app/shared/deimos/deimos-date-range-modal.component';
import { DeimosService } from 'app/programming/shared/services/deimos.service';
import { FeatureToggleHideDirective } from 'app/foundation/feature-toggle/feature-toggle-hide.directive';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import dayjs from 'dayjs/esm';

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
        DeimosDateRangeModalComponent,
        FeatureToggleHideDirective,
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
    private deimosService = inject(DeimosService);

    readonly TeamFilterProp = TeamFilterProp;
    readonly documentationType: DocumentationType = 'Assessment';

    readonly plagiarismEnabled = signal<boolean>(false);

    // Signal-backed reactive state: these are populated from async subscriptions (and recomputed in place previously),
    // so under zoneless they are signals — each write schedules change detection without markForCheck.
    readonly course = signal<Course>(undefined!);
    readonly exam = signal<Exam>(undefined!);
    readonly courseId = signal<number>(undefined!);
    readonly examId = signal<number>(undefined!);
    exerciseGroupId: number;
    readonly allExercises = signal<Exercise[]>([]);
    readonly currentlyShownExercises = signal<Exercise[]>([]);
    readonly numberOfSubmissions = signal(new DueDateStat());
    readonly totalNumberOfAssessments = signal(0);
    readonly numberOfAssessmentsOfCorrectionRounds = signal<DueDateStat[]>([new DueDateStat()]);
    readonly numberOfCorrectionRounds = signal(1);
    readonly numberOfTutorAssessments = signal(0);

    readonly complaints = signal(new AssessmentDashboardInformationEntry(0, 0));
    readonly moreFeedbackRequests = signal(new AssessmentDashboardInformationEntry(0, 0));
    readonly assessmentLocks = signal(new AssessmentDashboardInformationEntry(0, 0));
    readonly ratings = signal(new AssessmentDashboardInformationEntry(0, 0));

    readonly totalAssessmentPercentage = signal(0);
    readonly hideFinishedExercises = signal<boolean>(true);
    readonly hideOptional = signal<boolean>(false);

    readonly stats = signal(new StatsForDashboard());

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    exercisesSortingPredicate = 'assessmentDueDate';
    exercisesReverseOrder = false;

    readonly tutor = signal<User>(undefined!);

    readonly isExamMode = signal<boolean>(false);
    readonly isTestRun = signal<boolean>(false);

    readonly tutorIssues = signal<TutorIssue[]>([]);

    isTogglingSecondCorrection: Map<number, boolean> = new Map<number, boolean>();

    readonly FeatureToggle = FeatureToggle;
    readonly deimosDateRangeModal = viewChild<DeimosDateRangeModalComponent>('deimosDateRangeModal');
    protected deimosSubmitting = signal(false);

    // Icons
    faSort = faSort;
    faTable = faTable;
    faClipboard = faClipboard;
    faHeartBroken = faHeartBroken;
    faShieldAlt = faShieldAlt;

    /**
     * On init set the courseID, load all exercises and statistics for tutors and set the identity for the AccountService.
     */
    ngOnInit(): void {
        this.courseId.set(Number(this.route.snapshot.paramMap.get('courseId')));
        this.examId.set(Number(this.route.snapshot.paramMap.get('examId')));
        this.isExamMode.set(!!this.examId());
        if (this.isExamMode()) {
            this.isTestRun.set(this.route.snapshot.url[1]?.toString() === 'test-runs');
            this.exerciseGroupId = Number(this.route.snapshot.paramMap.get('exerciseGroupId'));
        }
        this.loadAll();
        this.accountService.identity().then((user) => this.tutor.set(user!));
        this.plagiarismEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_PLAGIARISM));
    }

    /**
     * Load all exercises and statistics for tutors of this course.
     * Percentages are calculated and rounded towards zero.
     */
    loadAll() {
        if (this.isExamMode()) {
            this.hideFinishedExercises.set(false);
            this.examManagementService
                .getExamWithInterestingExercisesForAssessmentDashboard(this.courseId(), this.examId(), this.isTestRun())
                .subscribe((res: HttpResponse<Exam>) => {
                    const exam = res.body!;
                    this.exam.set(exam);
                    this.course.set(Course.from(exam.course!));
                    this.accountService.setAccessRightsForCourse(this.course());

                    // No exercises exist yet
                    if (exam.exerciseGroups) {
                        // get all exercises
                        const exercises: Exercise[] = [];
                        exam.exerciseGroups.forEach((exerciseGroup) => {
                            if (exerciseGroup.exercises) {
                                exercises.push(...exerciseGroup.exercises);

                                // Set the exercise group since it is undefined by default here
                                exerciseGroup.exercises.forEach((exercise: Exercise) => {
                                    exercise.exerciseGroup = exerciseGroup;
                                });
                            }
                        });

                        this.extractExercises(exercises);
                    }
                });
            this.examManagementService.getStatsForExamAssessmentDashboard(this.courseId(), this.examId()).subscribe({
                next: (res: HttpResponse<StatsForDashboard>) => {
                    const stats = StatsForDashboard.from(res.body!);
                    this.stats.set(stats);
                    this.numberOfSubmissions.set(stats.numberOfSubmissions);
                    this.numberOfAssessmentsOfCorrectionRounds.set(stats.numberOfAssessmentsOfCorrectionRounds);

                    let totalNumberOfAssessments = 0;
                    for (const dueDateStat of stats.numberOfAssessmentsOfCorrectionRounds) {
                        totalNumberOfAssessments += dueDateStat.inTime;
                    }
                    this.totalNumberOfAssessments.set(totalNumberOfAssessments);
                    this.numberOfCorrectionRounds.set(stats.numberOfAssessmentsOfCorrectionRounds.length);

                    const tutorLeaderboardEntry = stats.tutorLeaderboardEntries?.find((entry) => entry.userId === this.tutor()?.id);
                    this.sortService.sortByProperty(stats.tutorLeaderboardEntries, 'points', false);
                    if (tutorLeaderboardEntry) {
                        this.numberOfTutorAssessments.set(tutorLeaderboardEntry.numberOfAssessments);
                        this.complaints.set(
                            new AssessmentDashboardInformationEntry(
                                stats.numberOfComplaints,
                                tutorLeaderboardEntry.numberOfTutorComplaints,
                                stats.numberOfComplaints - stats.numberOfOpenComplaints,
                            ),
                        );
                    } else {
                        this.numberOfTutorAssessments.set(0);
                        this.complaints.set(new AssessmentDashboardInformationEntry(stats.numberOfComplaints, 0, stats.numberOfComplaints - stats.numberOfOpenComplaints));
                    }
                    this.assessmentLocks.set(new AssessmentDashboardInformationEntry(stats.totalNumberOfAssessmentLocks, stats.numberOfAssessmentLocks));

                    if (stats.numberOfSubmissions.total > 0) {
                        this.totalAssessmentPercentage.set(
                            Math.floor((totalNumberOfAssessments / (stats.numberOfSubmissions.total * stats.numberOfAssessmentsOfCorrectionRounds.length)) * 100),
                        );
                    }
                    this.computeIssuesWithTutorPerformance();
                },
                error: (response: string) => this.onError(response),
            });
        } else {
            this.courseService.getCourseWithInterestingExercisesForTutors(this.courseId()).subscribe({
                next: (res: HttpResponse<Course>) => {
                    const course = Course.from(res.body!);
                    this.course.set(course);
                    this.extractExercises(course.exercises);
                },
                error: (response: string) => this.onError(response),
            });

            this.courseService.getStatsForTutors(this.courseId()).subscribe({
                next: (res: HttpResponse<StatsForDashboard>) => {
                    const stats = StatsForDashboard.from(res.body!);
                    this.stats.set(stats);
                    this.numberOfSubmissions.set(stats.numberOfSubmissions);
                    this.totalNumberOfAssessments.set(stats.totalNumberOfAssessments);
                    this.numberOfAssessmentsOfCorrectionRounds.set(stats.numberOfAssessmentsOfCorrectionRounds);
                    const tutorLeaderboardEntry = stats.tutorLeaderboardEntries?.find((entry) => entry.userId === this.tutor()?.id);
                    this.sortService.sortByProperty(stats.tutorLeaderboardEntries, 'points', false);
                    if (tutorLeaderboardEntry) {
                        this.numberOfTutorAssessments.set(tutorLeaderboardEntry.numberOfAssessments);

                        this.complaints.set(
                            new AssessmentDashboardInformationEntry(
                                stats.numberOfComplaints,
                                tutorLeaderboardEntry.numberOfTutorComplaints,
                                stats.numberOfComplaints - stats.numberOfOpenComplaints,
                            ),
                        );
                        this.moreFeedbackRequests.set(
                            new AssessmentDashboardInformationEntry(
                                stats.numberOfMoreFeedbackRequests,
                                tutorLeaderboardEntry.numberOfTutorMoreFeedbackRequests,
                                stats.numberOfMoreFeedbackRequests - stats.numberOfOpenMoreFeedbackRequests,
                            ),
                        );
                        this.ratings.set(new AssessmentDashboardInformationEntry(stats.numberOfRatings, tutorLeaderboardEntry.numberOfTutorRatings));
                    } else {
                        this.numberOfTutorAssessments.set(0);
                        this.complaints.set(new AssessmentDashboardInformationEntry(stats.numberOfComplaints, 0, stats.numberOfComplaints - stats.numberOfOpenComplaints));
                        this.moreFeedbackRequests.set(
                            new AssessmentDashboardInformationEntry(
                                stats.numberOfMoreFeedbackRequests,
                                0,
                                stats.numberOfMoreFeedbackRequests - stats.numberOfOpenMoreFeedbackRequests,
                            ),
                        );
                        this.ratings.set(new AssessmentDashboardInformationEntry(stats.numberOfRatings, 0));
                    }
                    this.assessmentLocks.set(new AssessmentDashboardInformationEntry(stats.totalNumberOfAssessmentLocks, stats.numberOfAssessmentLocks));

                    if (stats.numberOfSubmissions.total > 0) {
                        this.totalAssessmentPercentage.set(Math.floor((stats.totalNumberOfAssessments / stats.numberOfSubmissions.total) * 100));
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
        // collect the tutor issues into a fresh array, then commit it to the signal
        const tutorIssues: TutorIssue[] = [];
        const tutorLeaderboardEntries = this.stats().tutorLeaderboardEntries;

        const complaintRatio = (entry: TutorLeaderboardElement) => {
            if (entry.numberOfAssessments === 0) {
                return 0;
            }
            return (100 * entry.numberOfTutorComplaints) / entry.numberOfAssessments;
        };

        const courseInformation = tutorLeaderboardEntries.reduce(
            (accumulator, entry) => {
                return {
                    summedAverageRatings: accumulator.summedAverageRatings + entry.averageRating,
                    summedAverageScore: accumulator.summedAverageScore + entry.averageScore,
                    summedComplaintRatio: accumulator.summedComplaintRatio + complaintRatio(entry),
                };
            },
            { summedAverageRatings: 0, summedAverageScore: 0, summedComplaintRatio: 0 },
        );

        const numberOfTutorsWithNonZeroRatings = tutorLeaderboardEntries.filter((entry) => entry.averageRating > 0).length;
        const numberOfTutorsWithNonZeroAssessments = tutorLeaderboardEntries.filter((entry) => entry.numberOfAssessments > 0).length;

        tutorLeaderboardEntries
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
                const tutorEntry = tutorLeaderboardEntries.find((entry) => entry.userId === issue.tutorId);
                tutorEntry!.hasIssuesWithPerformance = true;

                // add issue to the issues list
                tutorIssues.push(issue);
            });
        this.tutorIssues.set(tutorIssues);
    }

    /**
     * Divides exercises into finished and unfinished exercises.
     * @param exercises - the exercises that should get filtered
     */
    private extractExercises(exercises?: Exercise[]) {
        if (exercises && exercises.length > 0) {
            this.allExercises.set(exercises);
            this.initIsTogglingSecondCorrection();
            this.updateExercises();
        }
    }

    /**
     * Initiates the map that contains the current toggling state (false) for each exercise.
     */
    private initIsTogglingSecondCorrection() {
        this.allExercises().forEach((exercise) => {
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
            exercise.totalNumberOfAssessments !== exercise.numberOfSubmissions?.inTime
        );
    }

    /**
     * Toggle the option to show finished exercises.
     */
    triggerFinishedExercises() {
        this.hideFinishedExercises.update((value) => !value);
        this.updateExercises();
    }

    /**
     * Toggle the option to hide optional exercises.
     */
    triggerOptionalExercises() {
        this.hideOptional.update((value) => !value);
        this.updateExercises();
    }

    /**
     * update the exercise array based on the option show finished exercises
     */
    updateExercises() {
        let shown = this.hideFinishedExercises() ? this.getUnfinishedExercises(this.allExercises()) : this.allExercises();
        if (this.hideOptional()) {
            shown = shown.filter((exercise) => exercise.includedInOverallScore !== IncludedInOverallScore.NOT_INCLUDED);
        }
        this.currentlyShownExercises.set(shown);
    }

    /**
     * Pass on an error to the browser console and the alertService.
     * @param error
     */
    private onError(error: string) {
        this.alertService.error(error);
    }

    sortRows() {
        const sorted = [...this.currentlyShownExercises()];
        this.sortService.sortByProperty(sorted, this.exercisesSortingPredicate, this.exercisesReverseOrder);
        this.currentlyShownExercises.set(sorted);
    }

    toggleSecondCorrection(exerciseId: number) {
        const currentExercise = this.currentlyShownExercises().find((exercise) => exercise.id === exerciseId)!;
        this.isTogglingSecondCorrection.set(currentExercise.id!, true);
        this.exerciseService.toggleSecondCorrection(exerciseId).subscribe({
            next: (res: boolean) => {
                currentExercise.secondCorrectionEnabled = res;
                this.isTogglingSecondCorrection.set(currentExercise.id!, false);
                // Commit a new array reference so the signal notifies (the exercise object was mutated in place).
                this.currentlyShownExercises.set([...this.currentlyShownExercises()]);
            },
            error: (err: string) => {
                this.onError(err);
            },
        });
    }

    getAssessmentDashboardLinkForExercise(exercise: Exercise): string[] {
        if (this.isExamMode()) {
            return [
                '/course-management',
                this.courseId().toString(),
                'exams',
                this.examId().toString(),
                this.isTestRun() ? 'test-assessment-dashboard' : 'assessment-dashboard',
                exercise.id!.toString(),
            ];
        } else {
            return ['/course-management', this.courseId().toString(), 'assessment-dashboard', exercise.id!.toString()];
        }
    }

    openDeimosBatchDialog(): void {
        this.deimosDateRangeModal()?.open(dayjs().subtract(7, 'day'), dayjs());
    }

    triggerCourseDeimosBatch(selection: DeimosDateRangeSelection): void {
        if (!this.courseId()) {
            return;
        }

        this.deimosSubmitting.set(true);
        this.deimosService.triggerCourseBatch(this.courseId(), selection.from, selection.to).subscribe({
            next: () => {
                this.deimosSubmitting.set(false);
                this.alertService.success('artemisApp.deimos.trigger.success');
            },
            error: () => {
                this.deimosSubmitting.set(false);
                this.alertService.error('artemisApp.deimos.trigger.error');
            },
        });
    }

    asQuizExercise(exercise: Exercise): QuizExercise {
        return exercise as QuizExercise;
    }
}
