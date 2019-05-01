import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { TUM_REGEX } from 'app/app.constants';
import { AccountService } from 'app/core';
import { Course, CourseService } from 'app/entities/course';
import { HttpResponse } from '@angular/common/http';

const DEFAULT_COLORS = ['#6ae8ac', '#9dca53', '#94a11c', '#691b0b', '#ad5658', '#1b97ca', '#0d3cc2', '#0ab84f'];

@Component({
    selector: 'jhi-course-registration-selector',
    templateUrl: './course-registration-selector.component.html',
})
export class CourseRegistrationSelectorComponent implements OnInit {
    @Input() courses: Course[];
    @Output() courseRegistered = new EventEmitter();
    public coursesToSelect: Course[] = [];
    public courseToRegister: Course | null;
    public isTumStudent = false;
    showCourseSelection = false;
    addedSuccessful = false;
    loading = false;

    constructor(private accountService: AccountService, private courseService: CourseService, private jhiAlertService: JhiAlertService) {}

    ngOnInit(): void {
        this.accountService.identity().then(user => {
            this.isTumStudent = !!user.login.match(TUM_REGEX);
        });
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, undefined);
    }

    trackCourseById(index: number, item: Course) {
        return item.id;
    }

    loadAndFilterCourses() {
        return new Promise((resolve, reject) => {
            this.courseService.findAllToRegister().subscribe(
                registerRes => {
                    let coursesToRegister = registerRes.body as Course[];
                    this.coursesToSelect = coursesToRegister.filter(course => {
                        return !this.courses.find(el => el.id === course.id);
                    });
                    resolve();
                },
                (response: string) => reject(response),
            );
        });
    }

    startRegistration() {
        this.showCourseSelection = true;
        this.loading = true;
        this.loadAndFilterCourses()
            .then(() => {
                this.loading = false;
                if (this.coursesToSelect.length === 0) {
                    setTimeout(() => {
                        this.courseToRegister = null;
                        this.showCourseSelection = false;
                    }, 3000);
                }
            })
            .catch(() => {
                this.loading = false;
                this.courseToRegister = null;
                this.showCourseSelection = false;
            });
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
                        this.coursesToSelect = [];
                    }, 3000);
                    this.courseRegistered.emit();
                },
                error => {
                    console.log(error);
                    this.loading = false;
                    this.courseToRegister = null;
                },
            );
        }
    }
}
