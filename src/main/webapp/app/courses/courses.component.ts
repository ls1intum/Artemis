import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Course, CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    selector: 'jhi-courses',
    templateUrl: './courses.component.html',
    providers:  [
                    JhiAlertService,
                    CourseService
                ]
})
export class CoursesComponent implements OnInit {
    courses: Course[];
    filterByCourseId: number;
    filterByExerciseId: number;
    coursesTotalScore = new Map<string, string>();
    private sub: any;

    constructor(
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        private route: ActivatedRoute) {}

    ngOnInit(): void {
        // (+) converts string 'id' to a number
        this.sub = this.route.params.subscribe(params => {
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
            (res: Course[]) => {
                this.courses = res;
                if (this.filterByCourseId) {
                    this.courses = this.courses.filter(course => course.id === this.filterByCourseId);
                }
            },
            (res: Course[]) => this.onError(res)
        );

    }

    trackId(index: number, item: Course) {
        return item.id;
    }

    calculateCourseTotalScore(id: number) {
        this.courseService.getCourseTotalScore(id).subscribe(
            (res: any) => {
                this.coursesTotalScore.set(res.body.courseId, res.body.totalScore);
                console.log(res);
            },
            (res: any) => this.onError(res)
        );
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }

    showWelcomeAlert() {
        // show alert after timeout to fix translation not loaded
        setTimeout(() => {
            this.jhiAlertService.info('arTeMiSApp.exercise.welcome');
        }, 500);
    }

    // TODO migrate repository functionality from courses.controller
}
