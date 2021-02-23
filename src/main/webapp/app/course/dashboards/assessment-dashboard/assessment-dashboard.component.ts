import { AfterViewInit, Component, OnInit } from '@angular/core';
import { partition } from 'lodash';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from '../../manage/course-management.service';
import { JhiAlertService } from 'ng-jhipster';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { HttpResponse } from '@angular/common/http';
import { Exercise, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { StatsForDashboard } from 'app/course/dashboards/instructor-course-dashboard/stats-for-dashboard.model';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { tutorAssessmentTour } from 'app/guided-tour/tours/tutor-assessment-tour';
import { Course } from 'app/entities/course.model';
import { DueDateStat } from 'app/course/dashboards/instructor-course-dashboard/due-date-stat.model';
import { FilterProp as TeamFilterProp } from 'app/exercises/shared/team/teams.component';
import { SortService } from 'app/shared/service/sort.service';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

@Component({
    selector: 'jhi-courses',
    templateUrl: './assessment-dashboard.component.html',
    providers: [CourseManagementService],
})
export class AssessmentDashboardComponent implements OnInit, AfterViewInit {
    readonly TeamFilterProp = TeamFilterProp;

    course: Course;
    exam: Exam;
    courseId: number;
    examId: number;
    unfinishedExercises: Exercise[] = [];
    finishedExercises: Exercise[] = [];
    exercises: Exercise[] = [];
    numberOfSubmissions = new DueDateStat();
    totalNumberOfAssessments = new DueDateStat();
    numberOfAssessmentsOfCorrectionRounds = [new DueDateStat()];
    numberOfTutorAssessments = 0;
    numberOfComplaints = 0;
    numberOfOpenComplaints = 0;
    numberOfTutorComplaints = 0;
    numberOfMoreFeedbackRequests = 0;
    numberOfOpenMoreFeedbackRequests = 0;
    numberOfTutorMoreFeedbackRequests = 0;
    numberOfAssessmentLocks = 0;
    totalAssessmentPercentage = 0;
    showFinishedExercises = false;
    isAtLeastInstructor = false;

    stats = new StatsForDashboard();

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    exercisesSortingPredicate = 'assessmentDueDate';
    exercisesReverseOrder = false;

    tutor: User;
    exerciseForGuidedTour?: Exercise;

    isExamMode = false;
    isTestRun = false;
    toggelingSecondCorrectionButton = false;

    constructor(
        private courseService: CourseManagementService,
        private exerciseService: ExerciseService,
        private examManagementService: ExamManagementService,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private route: ActivatedRoute,
        private router: Router,
        private guidedTourService: GuidedTourService,
        private sortService: SortService,
    ) {}

    /**
     * On init set the courseID, load all exercises and statistics for tutors and set the identity for the AccountService.
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        this.isExamMode = !!this.examId;
        if (this.isExamMode) {
            this.isTestRun = this.route.snapshot.url[1]?.toString() === 'test-runs';
            this.showFinishedExercises = this.isTestRun;
        }
        this.loadAll();
        this.accountService.identity().then((user) => (this.tutor = user!));
    }

    /**
     * After the page has fully loaded, notify the GuidedTourService about it.
     */
    ngAfterViewInit(): void {
        this.guidedTourService.componentPageLoaded();
    }

    /**
     * Load all exercises and statistics for tutors of this course.
     * Percentages are calculated and rounded towards zero.
     */
    loadAll() {
        if (this.isExamMode) {
            this.showFinishedExercises = true;
            this.examManagementService.getExamWithInterestingExercisesForAssessmentDashboard(this.courseId, this.examId, this.isTestRun).subscribe((res: HttpResponse<Exam>) => {
                this.exam = res.body!;
                this.course = Course.from(this.exam.course!);
                this.courseService.checkAndSetCourseRights(this.course);
                this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.course);

                // get all exercises
                const exercises: Exercise[] = [];
                // eslint-disable-next-line chai-friendly/no-unused-expressions
                this.exam.exerciseGroups!.forEach((exerciseGroup) => {
                    if (exerciseGroup.exercises) {
                        exercises.push(...exerciseGroup.exercises);
                    }
                });

                this.extractExercises(exercises);

                // TODO: implement some tutor stats here similar to the ones below but based on the exam and not the course
            });
        } else {
            this.courseService.getCourseWithInterestingExercisesForTutors(this.courseId).subscribe(
                (res: HttpResponse<Course>) => {
                    this.course = Course.from(res.body!);
                    this.courseService.checkAndSetCourseRights(this.course);
                    this.extractExercises(this.course.exercises);
                    this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.course);
                },
                (response: string) => this.onError(response),
            );

            this.courseService.getStatsForTutors(this.courseId).subscribe(
                (res: HttpResponse<StatsForDashboard>) => {
                    this.stats = StatsForDashboard.from(res.body!);
                    this.numberOfSubmissions = this.stats.numberOfSubmissions;
                    this.totalNumberOfAssessments = this.stats.totalNumberOfAssessments;
                    this.numberOfAssessmentsOfCorrectionRounds = this.stats.numberOfAssessmentsOfCorrectionRounds;
                    this.numberOfComplaints = this.stats.numberOfComplaints;
                    this.numberOfOpenComplaints = this.stats.numberOfOpenComplaints;
                    this.numberOfMoreFeedbackRequests = this.stats.numberOfMoreFeedbackRequests;
                    this.numberOfOpenMoreFeedbackRequests = this.stats.numberOfOpenMoreFeedbackRequests;
                    this.numberOfAssessmentLocks = this.stats.numberOfAssessmentLocks;
                    const tutorLeaderboardEntry = this.stats.tutorLeaderboardEntries.find((entry) => entry.userId === this.tutor.id);
                    if (tutorLeaderboardEntry) {
                        this.numberOfTutorAssessments = tutorLeaderboardEntry.numberOfAssessments;
                        this.numberOfTutorComplaints = tutorLeaderboardEntry.numberOfTutorComplaints;
                        this.numberOfTutorMoreFeedbackRequests = tutorLeaderboardEntry.numberOfTutorMoreFeedbackRequests;
                    } else {
                        this.numberOfTutorAssessments = 0;
                        this.numberOfTutorComplaints = 0;
                        this.numberOfTutorMoreFeedbackRequests = 0;
                    }

                    if (this.numberOfSubmissions.total > 0) {
                        this.totalAssessmentPercentage = Math.floor((this.totalNumberOfAssessments.total / this.numberOfSubmissions.total) * 100);
                    }
                },
                (response: string) => this.onError(response),
            );
        }
    }

    /**
     * devides exercises into finished and unfinished exercises.
     *
     * @param exercises - the exercises that should get filtered
     * @private
     */
    private extractExercises(exercises?: Exercise[]) {
        if (exercises && exercises.length > 0) {
            const [finishedExercises, unfinishedExercises] = partition(
                exercises,
                (exercise) =>
                    // finds if all assessments for all correctionRounds are finished
                    exercise.numberOfAssessmentsOfCorrectionRounds
                        ?.map((round) => round.inTime === exercise.numberOfSubmissions?.inTime)
                        .reduce((acc, current) => acc && current) &&
                    exercise.totalNumberOfAssessments?.inTime === exercise.numberOfSubmissions?.inTime &&
                    exercise.numberOfOpenComplaints === 0 &&
                    exercise.numberOfOpenMoreFeedbackRequests === 0,
            );
            this.finishedExercises = finishedExercises;
            this.unfinishedExercises = unfinishedExercises;
            // sort exercises by type to get a better overview in the dashboard
            this.sortService.sortByProperty(this.unfinishedExercises, 'type', true);
            this.exerciseForGuidedTour = this.guidedTourService.enableTourForCourseExerciseComponent(this.course, tutorAssessmentTour, false);
            this.updateExercises();
        }
    }

    /**
     * Toggle the option to show finished exercises.
     */
    triggerFinishedExercises() {
        this.showFinishedExercises = !this.showFinishedExercises;
        this.updateExercises();
    }

    /**
     * update the exercise array based on the option show finished exercises
     */
    updateExercises() {
        if (this.showFinishedExercises) {
            this.exercises = this.unfinishedExercises.concat(this.finishedExercises);
        } else {
            this.exercises = this.unfinishedExercises;
        }
    }

    /**
     * Pass on an error to the browser console and the jhiAlertService.
     * @param error
     */
    private onError(error: string) {
        this.jhiAlertService.error(error);
    }

    sortRows() {
        this.sortService.sortByProperty(this.exercises, this.exercisesSortingPredicate, this.exercisesReverseOrder);
    }

    toggleSecondCorrection(exerciseId: number) {
        this.toggelingSecondCorrectionButton = true;
        const currentExercise = this.exercises.find((exercise) => exercise.id === exerciseId)!;
        const index = this.exercises.indexOf(currentExercise);
        this.exerciseService.toggleSecondCorrection(exerciseId).subscribe(
            (res: Boolean) => {
                this.exercises[index].secondCorrectionEnabled = !this.exercises[index].secondCorrectionEnabled;
                currentExercise!.secondCorrectionEnabled = res as boolean;
                this.toggelingSecondCorrectionButton = false;
            },
            (err: string) => {
                this.onError(err);
            },
        );
    }
}
