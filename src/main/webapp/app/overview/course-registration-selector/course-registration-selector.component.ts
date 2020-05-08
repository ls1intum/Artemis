import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { AlertService } from 'app/core/alert/alert.service';
import { TUM_USERNAME_REGEX } from 'app/app.constants';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../../course/manage/course-management.service';

@Component({
    selector: 'jhi-course-registration-selector',
    templateUrl: './course-registration-selector.component.html',
})
export class CourseRegistrationSelectorComponent implements OnInit {
    @Input() courses: Course[];
    @Output() courseRegistered = new EventEmitter();
    public coursesToSelect: Course[] = [];
    public courseToRegister: Course | undefined;
    public isTumStudent = false;
    showCourseSelection = false;
    addedSuccessful = false;
    loading = false;

    constructor(private accountService: AccountService, private courseService: CourseManagementService, private jhiAlertService: AlertService) {}

    /**
     * On init, check the identity of the user whether he/she is TUM student or not
     */
    ngOnInit(): void {
        this.accountService.identity().then((user) => {
            this.isTumStudent = !!user!.login!.match(TUM_USERNAME_REGEX);
        });
    }

    /**
     * Sends an error message
     * @param error - error message to be shown
     */
    private onError(error: string) {
        this.jhiAlertService.error(error, null, undefined);
    }

    /**
     * Returns tracked course id
     * @param index - number
     * @param item - Course to return its id
     */
    trackCourseById(index: number, item: Course) {
        return item.id;
    }

    /**
     * Find all courses to be registered to and filter them
     */
    loadAndFilterCourses() {
        return new Promise((resolve, reject) => {
            this.courseService.findAllToRegister().subscribe(
                (registerRes) => {
                    this.coursesToSelect = registerRes.body!.filter((course) => {
                        return !this.courses.find((el) => el.id === course.id);
                    });
                    resolve();
                },
                (response: string) => reject(response),
            );
        });
    }

    /**
     * Loads courses that can be registered to and starts the registration
     */
    startRegistration() {
        this.loading = true;
        this.loadAndFilterCourses()
            .then(() => {
                this.loading = false;
                this.showCourseSelection = true;
                if (this.coursesToSelect.length === 0) {
                    setTimeout(() => {
                        this.courseToRegister = undefined;
                        this.showCourseSelection = false;
                    }, 3000);
                }
            })
            .catch(() => {
                this.loading = false;
                this.courseToRegister = undefined;
                this.showCourseSelection = false;
            });
    }

    /**
     * Cancels registration
     */
    cancelRegistration() {
        this.courseToRegister = undefined;
        this.showCourseSelection = false;
    }

    /**
     * Registers for the selected course
     */
    registerForCourse() {
        if (this.courseToRegister) {
            this.showCourseSelection = false;
            this.loading = true;
            this.courseService.registerForCourse(this.courseToRegister.id).subscribe(
                () => {
                    this.addedSuccessful = true;
                    this.loading = false;
                    setTimeout(() => {
                        this.courseToRegister = undefined;
                        this.addedSuccessful = false;
                        this.coursesToSelect = [];
                    }, 3000);
                    this.courseRegistered.emit();
                },
                (error) => {
                    console.log(error);
                    this.loading = false;
                    this.courseToRegister = undefined;
                },
            );
        }
    }
}
