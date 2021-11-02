import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { matchesRegexFully } from 'app/utils/regex.util';

@Component({
    selector: 'jhi-course-registration-selector',
    templateUrl: './course-registration-selector.component.html',
})
export class CourseRegistrationSelectorComponent implements OnInit {
    @Input() courses: Course[];
    @Output() courseRegistered = new EventEmitter();
    public coursesToSelect: Course[] = [];
    public courseToRegister: Course | undefined;
    public userIsAllowedToRegister = false;
    showCourseSelection = false;
    addedSuccessful = false;
    loading = false;

    constructor(
        private accountService: AccountService,
        private courseService: CourseManagementService,
        private alertService: AlertService,
        private profileService: ProfileService,
    ) {}

    ngOnInit(): void {
        this.accountService.identity().then((user) => {
            this.profileService.getProfileInfo().subscribe((profileInfo) => {
                if (profileInfo) {
                    this.userIsAllowedToRegister = matchesRegexFully(user!.login, profileInfo.allowedCourseRegistrationUsernamePattern);
                }
            });
        });
    }

    trackCourseById(index: number, item: Course) {
        return item.id;
    }

    loadAndFilterCourses() {
        return new Promise<void>((resolve, reject) => {
            this.courseService.findAllToRegister().subscribe(
                (registerRes) => {
                    this.coursesToSelect = registerRes
                        .body!.filter((course) => {
                            return !this.courses.find((el) => el.id === course.id);
                        })
                        .sort((a, b) => a.title!.localeCompare(b.title!));
                    resolve();
                },
                (response: string) => reject(response),
            );
        });
    }

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

    cancelRegistration() {
        this.courseToRegister = undefined;
        this.showCourseSelection = false;
    }

    registerForCourse() {
        if (this.courseToRegister) {
            this.showCourseSelection = false;
            this.loading = true;
            this.courseService.registerForCourse(this.courseToRegister.id!).subscribe(
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
                () => {
                    this.loading = false;
                    this.courseToRegister = undefined;
                },
            );
        }
    }
}
