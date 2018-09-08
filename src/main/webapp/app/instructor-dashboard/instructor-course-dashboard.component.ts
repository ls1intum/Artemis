import { JhiAlertService } from 'ng-jhipster';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { Course, CourseService } from '../entities/course';
import { Result } from '../entities/result';
import { Participation } from '../entities/participation';

@Component({
    selector: 'jhi-instructor-course-dashboard',
    templateUrl: './instructor-course-dashboard.component.html',
    providers:  [
        JhiAlertService
    ]
})

export class InstructorCourseDashboardComponent implements OnInit, OnDestroy {

    course: Course;
    paramSub: Subscription;
    predicate: string;
    reverse: boolean;
    numberOfExercises = 0;
    rows: any;
    results = new Array<Result>();
    participations = new Array<Participation>();
    courseScores = new Array<any>();

    constructor(private route: ActivatedRoute,
                private courseService: CourseService) {
        this.reverse = false;
        this.predicate = 'id';
    }

    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            this.courseService.find(params['courseId']).subscribe(res => {
                this.course = res.body;
                this.getResults(this.course.id);
            });
        });
    }

    getResults(courseId: number) {
        this.courseService.findAllResults(courseId).subscribe(res => {
            this.results = res;
            this.groupResults();
        });

        this.courseService.findAllParticipations(courseId).subscribe(res => {
            this.participations = res;
            this.groupResults();
        });

        this.courseService.getAllCourseScoresOfCourseUsers(courseId).subscribe(res => {
            this.courseScores = res;
            this.groupResults();
        });
    }

    groupResults() {
        if (!this.results || !this.participations || !this.courseScores || this.participations.length === 0 || this.results.length === 0 || this.courseScores.length === 0) {
            return;
        }

        const rows = {};
        const exercisesSeen = {};
        for (const p of this.participations) {
            if (!rows[p.student.id]) {
                rows[p.student.id] = {
                    'firstName': p.student.firstName,
                    'lastName': p.student.lastName,
                    'login': p.student.login,
                    'participated': 0,
                    'participationInPercent': 0,
                    'successful': 0,
                    'successfullyCompletedInPercent': 0,
                    'overallScore': 0,
                };
            }

            rows[p.student.id].participated++;

            if (!exercisesSeen[p.exercise.id]) {
                exercisesSeen[p.exercise.id] = true;
                this.numberOfExercises++;
            }
        }

        // Successful Participations as the total amount and a relative value to all Exercises
        for (const r of this.results) {
            rows[r.participation.student.id].successful++;
            rows[r.participation.student.id].successfullyCompletedInPercent = (rows[r.participation.student.id].successful / this.numberOfExercises) * 100;
        }

        // Relative amount of participation in all exercises
        // Since 1 user can have multiple participations studentSeen makes sure each student is only calculated once
        const studentSeen = {};
        for (const p of this.participations) {
            if (!studentSeen[p.student.id]) {
                studentSeen[p.student.id] = true;
                rows[p.student.id].participationInPercent = (rows[p.student.id].participated / this.numberOfExercises) * 100;
            }
        }

        // Set the total score of all Exercises (as mentioned on the RESTapi division by amount of exercises)
        for (const s of this.courseScores) {
            if (s.participation.student) {
                rows[s.participation.student.id].overallScore = s.score;
            }
        }

        this.rows = Object.keys(rows).map(key => rows[key]);
    }

    ngOnDestroy() {
        this.paramSub.unsubscribe();
    }

    callback() { }
}
