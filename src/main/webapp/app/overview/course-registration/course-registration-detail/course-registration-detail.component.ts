import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { Observable, catchError, map, of, throwError } from 'rxjs';

@Component({
    selector: 'jhi-course-registration-detail-selector',
    templateUrl: './course-registration-detail.component.html',
})
export class CourseRegistrationDetailComponent implements OnInit, OnDestroy {
    private accountService = inject(AccountService);
    private courseService = inject(CourseManagementService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);

    loading = false;
    courseId: number;
    course: Course | null = null;
    private paramSubscription: any;

    ngOnInit(): void {
        this.loading = true;
        this.paramSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId']);
            this.courseService.findOneForRegistration(this.courseId).subscribe((res) => {
                this.course = res.body!;
                this.loading = false;
            });
            this.redirectIfCourseIsFullyAccessible();
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
    isCourseFullyAccessible(): Observable<boolean> {
        return this.courseService.findOneForDashboard(this.courseId).pipe(
            map(() => true),
            catchError((res: HttpErrorResponse) => {
                if (res.status === 403) {
                    return of(false);
                } else {
                    return throwError(res);
                }
            }),
        );
    }

    redirectIfCourseIsFullyAccessible() {
        this.isCourseFullyAccessible().subscribe((isFullyAccessible) => {
            if (isFullyAccessible) {
                this.redirectToCoursePage();
            }
        });
    }
}
