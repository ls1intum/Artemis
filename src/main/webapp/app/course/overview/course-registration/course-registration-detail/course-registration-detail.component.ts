import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Observable, Subscription } from 'rxjs';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CoursePrerequisitesButtonComponent } from '../course-prerequisites-button/course-prerequisites-button.component';
import { CourseRegistrationButtonComponent } from '../course-registration-button/course-registration-button.component';

@Component({
    selector: 'jhi-course-registration-detail-selector',
    templateUrl: './course-registration-detail.component.html',
    imports: [TranslateDirective, CoursePrerequisitesButtonComponent, CourseRegistrationButtonComponent],
})
export class CourseRegistrationDetailComponent implements OnInit, OnDestroy {
    private courseService = inject(CourseManagementService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);

    readonly loading = signal(false);
    courseId!: number; // set in ngOnInit() from route params
    readonly course = signal<Course | undefined>(undefined);
    private paramSubscription?: Subscription;

    ngOnInit(): void {
        this.loading.set(true);
        this.paramSubscription = this.route.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId']);
            this.courseService.findOneForRegistration(this.courseId).subscribe((res) => {
                this.course.set(res.body!);
                this.loading.set(false);
            });
            this.redirectIfCourseIsFullyAccessible();
        });
    }

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
    }

    redirectToCoursePage(): void {
        void this.router.navigate(['courses', this.courseId]);
    }

    /**
     * Whether the user already has access to the course, and should therefore be sent into it rather than shown the
     * enrollment form.
     *
     * This used to request the whole course dashboard and read its status code, which loaded every exercise,
     * participation and result of the course only to throw the response away. The dedicated endpoint answers with a
     * single boolean and, because not having access is the expected answer here rather than an error, without a 403
     * that the global error handler would surface as an alert.
     */
    isCourseFullyAccessible(): Observable<boolean> {
        return this.courseService.hasAccessToCourse(this.courseId);
    }

    redirectIfCourseIsFullyAccessible() {
        this.isCourseFullyAccessible().subscribe((isFullyAccessible) => {
            if (isFullyAccessible) {
                this.redirectToCoursePage();
            }
        });
    }
}
