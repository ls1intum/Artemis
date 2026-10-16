import { Component, OnInit, inject, signal } from '@angular/core';
import { Course, CourseRoleSlug, courseRoleSegments } from 'app/course/shared/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { Observable, Subscription, of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseGroupComponent } from 'app/course/shared/course-group/course-group.component';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';

@Component({
    selector: 'jhi-course-group-membership',
    templateUrl: './course-group-membership.component.html',
    imports: [CourseGroupComponent, ArtemisTranslatePipe, CourseTitleBarTitleDirective],
})
export class CourseGroupMembershipComponent implements OnInit {
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private courseService = inject(CourseManagementService);
    private accountService = inject(AccountService);

    course = signal<Course | undefined>(undefined);
    courseRoleSlug = signal<CourseRoleSlug | undefined>(undefined);
    isAdmin = signal(false);
    paramSub?: Subscription;

    ngOnInit(): void {
        this.loadAll();
    }

    removeFromRole = (login: string): Observable<HttpResponse<void>> => {
        const courseId = this.course()?.id;
        const courseRoleSlug = this.courseRoleSlug();
        if (courseId === undefined || !courseRoleSlug) {
            return of(new HttpResponse<void>());
        }
        return this.courseService.removeUserFromCourseRole(courseId, courseRoleSlug, login);
    };

    /**
     * Load course from route; user loading is handled server-side by CourseGroupComponent.
     */
    loadAll = () => {
        this.isAdmin.set(this.accountService.isAdmin());
        this.route.parent!.data.subscribe(({ course }: { course?: Course }) => {
            this.course.set(course);
            this.paramSub = this.route.params.subscribe((params) => {
                const slug: CourseRoleSlug = params['courseRoleSlug'];
                if (!courseRoleSegments.includes(slug)) {
                    void this.router.navigate(['/courses']);
                    return;
                }
                this.courseRoleSlug.set(slug);
            });
        });
    };
}
