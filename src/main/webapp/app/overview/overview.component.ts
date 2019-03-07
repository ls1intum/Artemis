import { Component } from '@angular/core';
import { Course, CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    selector: 'jhi-overview',
    templateUrl: './overview.component.html',
    styles: []
})
export class OverviewComponent {
    public courses: Course[];
    public coursesToSelect: Course[];
    public courseToRegister: Course;
    showCourseSelection = false;
    addedSuccessful = false;
    loading = false;

    constructor(private courseService: CourseService, private jhiAlertService: JhiAlertService, private courseScoreCalculationService: CourseScoreCalculationService) {
        this.loadAndFilterCourses();

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
}
