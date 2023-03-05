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

    /**
     * Check if we have full access to the course already
     */
    async courseIsFullyAccessible(): Promise<boolean> {
        // try to fetch full course from server to check access
        const resp = await this.courseService.find(this.courseId).toPromise();
        return resp?.status === 200;
    }

    ngOnInit(): void {
        this.loading = true;
        this.paramSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId']);
            this.courseService.findOneToRegister(this.courseId).subscribe((courseResponse) => {
                this.course = courseResponse.body!;
                this.loading = false;
            });
            this.courseIsFullyAccessible().then((isFullyAccessible) => {
                if (isFullyAccessible) {
                    this.redirectToCoursePage();
                }
            });
        });
    }

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
    }

    redirectToCoursePage() {
        this.router.navigate(['courses', this.courseId]);
    }
}
