import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';
import { Course, CourseService, StatsForTutorDashboard } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { AccountService, User } from '../core';
import { HttpResponse } from '@angular/common/http';
import { Exercise, getIcon, getIconTooltip } from 'app/entities/exercise';
import { TutorParticipationStatus } from 'app/entities/tutor-participation';
import * as moment from 'moment';

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
    totalAssessmentPercentage = 0;
    showFinishedExercises = false;

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    private tutor: User;

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
        this.accountService.identity().then(user => (this.tutor = user));
    }

    loadAll() {
        this.courseService.getForTutors(this.courseId).subscribe(
            (res: HttpResponse<Course>) => {
                this.course = res.body!;
                this.course.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(this.course);
                this.course.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.course);

                if (this.course.exercises && this.course.exercises.length > 0) {
                    this.unfinishedExercises = this.course.exercises
                        .filter(exercise => exercise.tutorParticipations[0].status !== TutorParticipationStatus.COMPLETED)
                        .sort(this.sortByAssessmentDueDate);
                    this.finishedExercises = this.course.exercises
                        .filter(exercise => exercise.tutorParticipations[0].status === TutorParticipationStatus.COMPLETED)
                        .sort(this.sortByAssessmentDueDate);
                    // sort exercises by type to get a better overview in the dashboard
                    this.exercises = this.unfinishedExercises.sort((a, b) => (a.type > b.type ? 1 : b.type > a.type ? -1 : 0));
                }
            },
            (response: string) => this.onError(response),
        );

        this.courseService.getStatsForTutors(this.courseId).subscribe(
            (res: HttpResponse<StatsForTutorDashboard>) => {
                this.numberOfSubmissions = res.body.numberOfSubmissions;
                this.numberOfAssessments = res.body.numberOfAssessments;
                this.numberOfTutorAssessments = res.body.numberOfTutorAssessments;
                this.numberOfComplaints = res.body.numberOfComplaints;

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

    private sortByAssessmentDueDate(firstEl: Exercise, secondEl: Exercise): number {
        if (!firstEl.assessmentDueDate && !secondEl.assessmentDueDate) {
            return firstEl.id - secondEl.id;
        }

        // Priority to the assessment with an assessment date
        if (!firstEl.assessmentDueDate) {
            return 1;
        }

        if (!secondEl.assessmentDueDate) {
            return -1;
        }

        return moment(firstEl.assessmentDueDate).diff(secondEl.assessmentDueDate);
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error(error, null, undefined);
    }

    back() {
        this.location.back();
    }
}
