import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';
import { Course, CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { AccountService, User } from '../core';
import { HttpResponse } from '@angular/common/http';
import { Exercise, getIcon, getIconTooltip } from 'app/entities/exercise';
import { StatsForDashboard } from 'app/instructor-course-dashboard/stats-for-dashboard.model';

@Component({
    selector: 'jhi-courses',
    templateUrl: './tutor-course-dashboard.component.html',
    providers: [JhiAlertService, CourseService],
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
    numberOfTutorComplaints = 0;
    numberOfMoreFeedbackRequests = 0;
    numberOfTutorMoreFeedbackRequests = 0;
    totalAssessmentPercentage = 0;
    showFinishedExercises = false;

    stats = new StatsForDashboard();

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    exercisesSortingPredicate = 'assessmentDueDate';
    exercisesReverseOrder = false;

    tutor: User;

    constructor(
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private route: ActivatedRoute,
        private location: Location,
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
                    this.unfinishedExercises = this.course.exercises.filter(exercise => (exercise.numberOfAssessments || 0) < (exercise.numberOfParticipations || 0));
                    // TODO: I think we should use a different criterion how to filter unfinished exercises
                    this.finishedExercises = this.course.exercises.filter(exercise => exercise.numberOfAssessments === exercise.numberOfParticipations); // TODO: I think we should use a different criterion how to filter finished exercises
                    // sort exercises by type to get a better overview in the dashboard
                    this.exercises = this.unfinishedExercises.sort((a, b) => (a.type > b.type ? 1 : b.type > a.type ? -1 : 0));
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
                this.numberOfMoreFeedbackRequests = this.stats.numberOfMoreFeedbackRequests;
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
        this.location.back();
    }

    callback() {}
}
