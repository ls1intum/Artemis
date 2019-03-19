import { Component, OnInit } from '@angular/core';
import { Course, CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { Exercise, ExerciseService } from 'app/entities/exercise';
import { AccountService } from 'app/core';
import { TUM_REGEX } from 'app/app.constants';

@Component({
    selector: 'jhi-overview',
    templateUrl: './overview.component.html',
    styles: []
})
export class OverviewComponent implements OnInit {
    public courses: Course[];
    public nextRelevantCourse: Course;
    public coursesToSelect: Course[];
    public courseToRegister: Course;
    public isTumStudent = false;
    showCourseSelection = false;
    addedSuccessful = false;
    loading = false;

    constructor(
        private courseService: CourseService,
        private exerciseService: ExerciseService,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private courseScoreCalculationService: CourseScoreCalculationService
    ) {
        this.loadAndFilterCourses();

    }

    ngOnInit(): void {
        this.accountService.identity().then(user => {
            this.isTumStudent = !!user.login.match(TUM_REGEX);
        });

    }

    loadAndFilterCourses() {
        this.courseService.findAll().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body;
                this.courseScoreCalculationService.setCourses(this.courses);
                this.courseService.findAllToRegister().subscribe(
                    (registerRes: HttpResponse<Course[]>) => {
                        this.coursesToSelect = registerRes.body.filter(course => {
                            return !this.courses.find(el => el.id === course.id);
                        });
                    },
                    (response: string) => this.onError(response)
                );
            },
            (response: string) => this.onError(response)
        );
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, null);
    }

    startRegistration() {
        this.showCourseSelection = true;
        if (this.coursesToSelect.length === 0) {
            setTimeout(() => {
                this.courseToRegister = null;
                this.showCourseSelection = false;

            }, 3000);
        }
    }

    cancelRegistration() {
        this.courseToRegister = null;
        this.showCourseSelection = false;
    }

    registerForCourse() {
        if (this.courseToRegister) {
            this.showCourseSelection = false;
            this.loading = true;
            this.courseService.registerForCourse(this.courseToRegister.id).subscribe(
                () => {
                    this.addedSuccessful = true;
                    this.loading = false;
                    setTimeout(() => {
                        this.courseToRegister = null;
                        this.addedSuccessful = false;

                    }, 3000);
                    this.loadAndFilterCourses();

                },
                error => {
                    console.log(error);
                    this.loading = false;
                    this.courseToRegister = null;
                }
            );
        }
    }

    trackCourseById(index: number, item: Course) {
        return item.id;
    }

    get nextRelevantExercise(): Exercise {
        let relevantExercise: Exercise = null;
        if (this.courses) {
            this.courses.forEach(course => {
                const relevantExerciseForCourse = this.exerciseService.getNextExerciseForHours(course.exercises);
                if (relevantExerciseForCourse) {
                    if (!relevantExercise) {
                        relevantExercise = relevantExerciseForCourse;
                        this.nextRelevantCourse = course;
                    } else if (relevantExerciseForCourse.dueDate.isBefore(relevantExercise.dueDate)) {
                        relevantExercise = relevantExerciseForCourse;
                        this.nextRelevantCourse = course;
                    }
                }
            });
        }
        return relevantExercise;
    }

}
