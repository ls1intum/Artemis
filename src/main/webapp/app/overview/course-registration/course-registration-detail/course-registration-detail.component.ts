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
        this.paramSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId']);
            this.courseService.findOneToRegister(this.courseId).subscribe((res) => {
                console.log({ res });
                const courseIsFullyAccessible = res.url?.endsWith('/for-dashboard');
                if (courseIsFullyAccessible) {
                    // server returned a course for the dashboard, which means we have full access to the course already
                    this.redirectToCoursePage();
                    return;
                }
                this.course = res.body!;
                this.loading = false;
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
