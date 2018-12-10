import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Course, CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { Subscription } from 'rxjs';
import { Principal } from '../core';
import { HttpResponse } from '@angular/common/http';
import { Exercise } from 'app/entities/exercise';

@Component({
    selector: 'jhi-courses',
    templateUrl: './tutor-course-dashboard.component.html',
    providers: [JhiAlertService, CourseService]
})
export class TutorCourseDashboardComponent implements OnInit {
    course: Course;
    courseId: number;
    exercises: Exercise[] = [];
    private subscription: Subscription;

    constructor(
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        private principal: Principal,
        private route: ActivatedRoute
    ) {}

    ngOnInit(): void {
        // (+) converts string 'id' to a number
        this.subscription = this.route.params.subscribe(params => {
            this.courseId = +params.courseId;
            this.loadAll();
        });

        this.route.queryParams.subscribe(queryParams => {
            if (queryParams['welcome'] === '') {
                this.showWelcomeAlert();
            }
        });
    }

    loadAll() {
        this.courseService.getForTutors(this.courseId).subscribe(
            (res: HttpResponse<Course>) => {
                this.course = res.body;

                if (this.course.exercises && this.course.exercises.length > 0) {
                    this.exercises = this.course.exercises;
                }
            },
            (response: string) => this.onError(response)
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
            this.jhiAlertService.info('arTeMiSApp.exercise.welcome');
        }, 500);
    }
}
