import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-course-registration-detail-selector',
    templateUrl: './course-registration-detail.component.html',
})
export class CourseRegistrationDetailComponent implements OnInit, OnDestroy {
    loading = false;
    courseId: number;
    course: Course | null = null;
    private paramSubscription: any;

    constructor(private accountService: AccountService, private courseService: CourseManagementService, private route: ActivatedRoute, private router: Router) {}

    ngOnInit(): void {
        this.loading = true;
        this.paramSubscription = this.route.parent!.params.subscribe(async (params) => {
            this.courseId = parseInt(params['courseId']);
            this.courseService.findOneForRegistration(this.courseId).subscribe((res) => {
                this.course = res.body!;
                this.loading = false;
            });
            await this.redirectIfCourseIsFullyAccessible();
        });
    }

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
    }

    redirectToCoursePage(): void {
        this.router.navigate(['courses', this.courseId]);
    }

    /**
     * Determines whether the user is already registered for the course by trying to fetch the for-dashboard version
     */
    isCourseFullyAccessible(): Promise<boolean> {
        return new Promise((resolve, reject) => {
            this.courseService.findOneForDashboard(this.courseId).subscribe({
                next: () => resolve(true),
                error: (res: HttpErrorResponse) => {
                    if (res.status === 403) {
                        resolve(false);
                    } else {
                        reject(res);
                    }
                },
            });
        });
    }

    async redirectIfCourseIsFullyAccessible(): Promise<void> {
        const isFullyAccessible = await this.isCourseFullyAccessible();
        if (isFullyAccessible) {
            this.redirectToCoursePage();
        }
    }
}
