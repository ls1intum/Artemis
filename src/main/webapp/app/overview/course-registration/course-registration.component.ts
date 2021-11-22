import { Component, OnInit } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { matchesRegexFully } from 'app/utils/regex.util';
import { HttpResponse } from '@angular/common/http';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-course-registration-selector',
    templateUrl: './course-registration.component.html',
})
export class CourseRegistrationComponent implements OnInit {
    courses: Course[];
    public coursesToSelect: Course[] = [];
    public userIsAllowedToRegister = false;
    addedSuccessful = false;
    loading = false;

    constructor(
        private accountService: AccountService,
        private courseService: CourseManagementService,
        private alertService: AlertService,
        private profileService: ProfileService,
        private router: Router,
    ) {}

    ngOnInit(): void {
        this.loading = true;
        this.courseService.findAllForDashboard().subscribe((res: HttpResponse<Course[]>) => {
            this.courses = res.body!;
            this.loadAndFilterCourses();
        });
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
        this.courseService.findAllToRegister().subscribe((registerRes) => {
            this.coursesToSelect = registerRes
                .body!.filter((course) => {
                    return !this.courses.find((el) => el.id === course.id);
                })
                .sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''));
        });
    }

    registerForCourse(courseId: number) {
        this.courseService.registerForCourse(courseId).subscribe(
            () => {
                this.addedSuccessful = true;
                this.loading = false;
                setTimeout(() => {
                    this.addedSuccessful = false;
                    this.coursesToSelect = this.coursesToSelect.filter((course) => course.id! !== courseId);
                }, 3000);
                this.router.navigate(['courses']);
            },
            () => {
                this.loading = false;
            },
        );
    }
}
