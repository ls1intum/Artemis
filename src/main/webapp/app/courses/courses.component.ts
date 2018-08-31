import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Course, CourseScoreCalculationService, CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { Subscription } from 'rxjs';

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
    private subscription: Subscription;

    constructor(
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        private courseScoreCalculationService: CourseScoreCalculationService,
        private route: ActivatedRoute) {}

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
            (res: Course[]) => {
                this.courses = res;
                this.courseScoreCalculationService.setCourses(this.courses);
                if (this.filterByCourseId) {
                    this.courses = this.courses.filter(course => course.id === this.filterByCourseId);
                }
            },
            (response: string) => this.onError(response)
        );
    }

    trackId(index: number, item: Course) {
        return item.id;
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

    displayTotalRelativeScoreForCourse(course: Course): number {
        if (course.exercises.length > 0) {
            return this.courseScoreCalculationService
                .calculateTotalScores(course.exercises)
                .get('relativeScore');
        } else {
            return 0;
        }
    }
}
