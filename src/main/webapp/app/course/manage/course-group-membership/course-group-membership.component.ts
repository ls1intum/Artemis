import { Component, OnInit, inject, signal } from '@angular/core';
import { Course, CourseRoleSlug, courseRoleSegments } from 'app/course/shared/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Subscription } from 'rxjs';
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
    paramSub: Subscription;

    ngOnInit(): void {
        this.loadAll();
    }

    removeFromRole = (login: string) => this.courseService.removeUserFromCourseRole(this.course()!.id!, this.courseRoleSlug()!, login);

    /**
     * Load course from route; user loading is handled server-side by CourseGroupComponent.
     */
    loadAll = () => {
        this.isAdmin.set(this.accountService.isAdmin());
        this.route.parent!.data.subscribe(({ course }) => {
            this.course.set(course);
            this.paramSub = this.route.params.subscribe((params) => {
                this.courseRoleSlug.set(params['courseRoleSlug']);
                if (!courseRoleSegments.includes(this.courseRoleSlug()!)) {
                    return this.router.navigate(['/course-management']);
                }
            });
        });
    };
}
