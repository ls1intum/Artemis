import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ABSOLUTE_SCORE, Course, CourseScoreCalculationService, CourseService, MAX_SCORE, RELATIVE_SCORE } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { Subscription } from 'rxjs';
import { AccountService } from '../core';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-courses',
    templateUrl: './course-list.component.html',
    providers: [JhiAlertService, CourseService],
})
export class CourseListComponent implements OnInit {
    courses: Course[];
    filterByCourseId: number;
    filterByExerciseId: number;
    private subscription: Subscription;

    constructor(
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private courseScoreCalculationService: CourseScoreCalculationService,
        private route: ActivatedRoute,
    ) {}

    ngOnInit(): void {
        // (+) converts string 'id' to a number
        this.subscription = this.route.params.subscribe(params => {
            this.filterByCourseId = +params['courseId'];
            this.filterByExerciseId = +params['exerciseId'];
            this.loadAll();
        });

        this.route.queryParams.subscribe(queryParams => {
            if (queryParams['welcome'] === '') {
                this.showWelcomeAlert();
            }
        });
    }

    loadAll() {
        this.courseService.findAll().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body;
                for (const course of this.courses) {
                    const scores = this.courseScoreCalculationService.calculateTotalScores(course.exercises);
                    course.absoluteScore = scores.get(ABSOLUTE_SCORE);
                    course.relativeScore = scores.get(RELATIVE_SCORE);
                    course.maxScore = scores.get(MAX_SCORE);
                }
                this.courseScoreCalculationService.setCourses(this.courses);
                if (this.filterByCourseId) {
                    this.courses = this.courses.filter(course => course.id === this.filterByCourseId);
                }
            },
            (response: string) => this.onError(response),
        );
    }

    trackId(index: number, item: Course) {
        return item.id;
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, null);
    }

    showWelcomeAlert() {
        // show alert after timeout to fix translation not loaded
        setTimeout(() => {
            this.jhiAlertService.info('artemisApp.exercise.welcome');
        }, 500);
    }
}
