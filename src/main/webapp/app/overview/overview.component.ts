import { Component, OnInit } from '@angular/core';
import { Course, CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { LocalStorageService } from 'ngx-webstorage';
import { Exercise, ExerciseService } from 'app/entities/exercise';
import { AccountService, UserService } from 'app/core';
import { CUSTOM_USER_KEY } from 'app/app.constants';

@Component({
    selector: 'jhi-overview',
    templateUrl: './overview.component.html',
    styles: [],
})
export class OverviewComponent {
    public courses: Course[];
    public nextRelevantCourse: Course;
    public startLoginProcess = false;
    public selectedUserLogin: string | null;
    public userNotFound: boolean;

    constructor(
        private courseService: CourseService,
        private userService: UserService,
        private exerciseService: ExerciseService,
        private jhiAlertService: JhiAlertService,
        private localStorageService: LocalStorageService,
        private accountService: AccountService,
        private courseScoreCalculationService: CourseScoreCalculationService,
    ) {
        if (this.localStorageService.retrieve(CUSTOM_USER_KEY)) {
            this.selectedUserLogin = this.localStorageService.retrieve(CUSTOM_USER_KEY);
        }
        this.loadAndFilterCourses();
    }

    loadAndFilterCourses() {
        const options = {};
        if (this.selectedUserLogin) {
            options['userId'] = this.selectedUserLogin;
        }
        this.courseService.findAll(options).subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
                this.courseScoreCalculationService.setCourses(this.courses);
            },
            (response: string) => this.onError(response),
        );
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, undefined);
    }

    get nextRelevantExercise(): Exercise | null {
        let relevantExercise: Exercise | null = null;
        if (this.courses) {
            this.courses.forEach(course => {
                const relevantExerciseForCourse = this.exerciseService.getNextExerciseForHours(course.exercises);
                if (relevantExerciseForCourse) {
                    if (!relevantExercise) {
                        relevantExercise = relevantExerciseForCourse;
                        this.nextRelevantCourse = course;
                    } else if (relevantExerciseForCourse.dueDate!.isBefore(relevantExercise.dueDate!)) {
                        relevantExercise = relevantExerciseForCourse;
                        this.nextRelevantCourse = course;
                    }
                }
            });
        }
        return relevantExercise;
    }

    startUsingLogin(): void {
        if (!this.selectedUserLogin) {
            this.startLoginProcess = false;
            this.localStorageService.clear(CUSTOM_USER_KEY);
            return;
        }
        this.userService.find(this.selectedUserLogin).subscribe(
            res => {
                this.startLoginProcess = false;
                if (res.body) {
                    this.localStorageService.store(CUSTOM_USER_KEY, this.selectedUserLogin);
                    this.loadAndFilterCourses();
                } else {
                }
            },
            error => {
                this.userNotFound = true;
                this.startLoginProcess = false;
                this.selectedUserLogin = null;
                this.localStorageService.clear(CUSTOM_USER_KEY);
                setTimeout(() => {
                    this.userNotFound = false;
                }, 1500);
            },
        );
    }

    removeUserLogin(): void {
        this.selectedUserLogin = null;
        this.startLoginProcess = false;
        this.localStorageService.clear(CUSTOM_USER_KEY);
        this.loadAndFilterCourses();
    }
}
