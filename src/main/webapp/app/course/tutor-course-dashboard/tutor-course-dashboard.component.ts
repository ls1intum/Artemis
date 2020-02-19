import { Component, OnInit } from '@angular/core';
import { partition } from 'lodash';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseService } from 'app/course/manage/course.service';
import { AlertService } from 'app/core/alert/alert.service';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { HttpResponse } from '@angular/common/http';
import { Exercise, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { StatsForDashboard } from 'app/course/instructor-course-dashboard/stats-for-dashboard.model';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { tutorAssessmentTour } from 'app/guided-tour/tours/tutor-assessment-tour';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-courses',
    templateUrl: './tutor-course-dashboard.component.html',
    providers: [CourseService],
})
export class TutorCourseDashboardComponent implements OnInit {
    course: Course;
    courseId: number;
    unfinishedExercises: Exercise[] = [];
    finishedExercises: Exercise[] = [];
    exercises: Exercise[] = [];
    numberOfSubmissions = 0;
    numberOfAssessments = 0;
    numberOfTutorAssessments = 0;
    numberOfComplaints = 0;
    numberOfOpenComplaints = 0;
    numberOfTutorComplaints = 0;
    numberOfMoreFeedbackRequests = 0;
    numberOfOpenMoreFeedbackRequests = 0;
    numberOfTutorMoreFeedbackRequests = 0;
    totalAssessmentPercentage = 0;
    showFinishedExercises = false;

    stats = new StatsForDashboard();

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    exercisesSortingPredicate = 'assessmentDueDate';
    exercisesReverseOrder = false;

    tutor: User;

    exerciseForGuidedTour: Exercise | null;

    constructor(
        private courseService: CourseService,
        private jhiAlertService: AlertService,
        private accountService: AccountService,
        private route: ActivatedRoute,
        private router: Router,
        private guidedTourService: GuidedTourService,
    ) {}

    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.loadAll();
        this.accountService.identity().then(user => (this.tutor = user!));
    }

    loadAll() {
        this.courseService.getForTutors(this.courseId).subscribe(
            (res: HttpResponse<Course>) => {
                this.course = res.body!;
                this.course.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(this.course);
                this.course.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.course);

                if (this.course.exercises && this.course.exercises.length > 0) {
                    const [finishedExercises, unfinishedExercises] = partition(
                        this.course.exercises,
                        exercise =>
                            exercise.numberOfAssessments === exercise.numberOfParticipations &&
                            exercise.numberOfOpenComplaints === 0 &&
                            exercise.numberOfOpenMoreFeedbackRequests === 0,
                    );
                    this.finishedExercises = finishedExercises;
                    this.unfinishedExercises = unfinishedExercises;
                    // sort exercises by type to get a better overview in the dashboard
                    this.exercises = this.unfinishedExercises.sort((a, b) => (a.type > b.type ? 1 : b.type > a.type ? -1 : 0));
                    this.exerciseForGuidedTour = this.guidedTourService.enableTourForCourseExerciseComponent(this.course, tutorAssessmentTour, false);
                }
            },
            (response: string) => this.onError(response),
        );

        this.courseService.getStatsForTutors(this.courseId).subscribe(
            (res: HttpResponse<StatsForDashboard>) => {
                this.stats = res.body!;
                this.numberOfSubmissions = this.stats.numberOfSubmissions;
                this.numberOfAssessments = this.stats.numberOfAssessments;
                this.numberOfComplaints = this.stats.numberOfComplaints;
                this.numberOfOpenComplaints = this.stats.numberOfOpenComplaints;
                this.numberOfMoreFeedbackRequests = this.stats.numberOfMoreFeedbackRequests;
                this.numberOfOpenMoreFeedbackRequests = this.stats.numberOfOpenMoreFeedbackRequests;
                const tutorLeaderboardEntry = this.stats.tutorLeaderboardEntries.find(entry => entry.userId === this.tutor.id);
                if (tutorLeaderboardEntry) {
                    this.numberOfTutorAssessments = tutorLeaderboardEntry.numberOfAssessments;
                    this.numberOfTutorComplaints = tutorLeaderboardEntry.numberOfAcceptedComplaints;
                    this.numberOfTutorMoreFeedbackRequests = tutorLeaderboardEntry.numberOfNotAnsweredMoreFeedbackRequests;
                } else {
                    this.numberOfTutorAssessments = 0;
                    this.numberOfTutorComplaints = 0;
                    this.numberOfTutorMoreFeedbackRequests = 0;
                }

                if (this.numberOfSubmissions > 0) {
                    this.totalAssessmentPercentage = Math.round((this.numberOfAssessments / this.numberOfSubmissions) * 100);
                }
            },
            (response: string) => this.onError(response),
        );
    }

    triggerFinishedExercises() {
        this.showFinishedExercises = !this.showFinishedExercises;

        if (this.showFinishedExercises) {
            this.exercises = this.unfinishedExercises.concat(this.finishedExercises);
        } else {
            this.exercises = this.unfinishedExercises;
        }
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error(error, null, undefined);
    }

    back() {
        this.router.navigate(['course']);
    }

    callback() {}
}
