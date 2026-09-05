import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Course, CourseRoleSlug, courseRoleSegments } from 'app/course/shared/entities/course.model';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { UserService } from 'app/account/user/shared/user.service';
import { Observable, Subscription, of } from 'rxjs';
import { capitalize } from 'lodash-es';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseGroupComponent } from 'app/course/shared/course-group/course-group.component';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-course-group-membership',
    templateUrl: './course-group-membership.component.html',
    imports: [CourseGroupComponent, TranslateDirective],
})
export class CourseGroupMembershipComponent implements OnInit {
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private courseService = inject(CourseManagementService);
    private userService = inject(UserService);
    private accountService = inject(AccountService);

    allCourseGroupUsers = signal<User[]>([]);
    course = signal<Course | undefined>(undefined);
    courseRoleSlug = signal<CourseRoleSlug | undefined>(undefined);
    isLoading = signal(false);
    isAdmin = signal(false);
    paramSub?: Subscription;
    filteredUsersSize = signal(0);

    readonly capitalize = capitalize;

    /**
     * Property that returns the course role entity name, e.g. "students" or "tutors".
     * If the count of users is exactly 1, singular is used instead of plural.
     */
    courseGroupEntityName = computed(() => {
        const courseRoleSlug = this.courseRoleSlug();
        if (!courseRoleSlug) {
            return '';
        }
        return this.allCourseGroupUsers().length === 1 ? courseRoleSlug.slice(0, -1) : courseRoleSlug;
    });

    exportFilename = computed(() => {
        const entityName = this.courseGroupEntityName();
        const course = this.course();
        if (!entityName || !course) {
            return '';
        }
        return entityName.charAt(0).toUpperCase() + entityName.slice(1) + ' ' + course.title;
    });

    ngOnInit(): void {
        this.loadAll();
    }

    userSearch = (loginOrName: string) => this.userService.search(loginOrName);

    addToRole = (login: string): Observable<HttpResponse<void>> => {
        const courseId = this.course()?.id;
        const courseRoleSlug = this.courseRoleSlug();
        if (courseId === undefined || !courseRoleSlug) {
            return of(new HttpResponse<void>());
        }
        return this.courseService.addUserToCourseRole(courseId, courseRoleSlug, login);
    };

    removeFromRole = (login: string): Observable<HttpResponse<void>> => {
        const courseId = this.course()?.id;
        const courseRoleSlug = this.courseRoleSlug();
        if (courseId === undefined || !courseRoleSlug) {
            return of(new HttpResponse<void>());
        }
        return this.courseService.removeUserFromCourseRole(courseId, courseRoleSlug, login);
    };

    /**
     * Update the number of filtered users
     *
     * @param filteredUsersSize Total number of users after filters have been applied
     */
    handleUsersSizeChange = (filteredUsersSize: number) => this.filteredUsersSize.set(filteredUsersSize);

    /**
     * Load all users of given course group.
     * Redirect to the course overview when the course group is invalid.
     */
    loadAll = () => {
        this.isLoading.set(true);
        this.isAdmin.set(this.accountService.isAdmin());
        this.route.parent!.data.subscribe(({ course }: { course?: Course }) => {
            this.course.set(course);
            const courseId = course?.id;
            this.paramSub = this.route.params.subscribe((params) => {
                const slug: CourseRoleSlug = params['courseRoleSlug'];
                if (!courseRoleSegments.includes(slug)) {
                    void this.router.navigate(['/courses']);
                    return;
                }
                this.courseRoleSlug.set(slug);
                if (courseId === undefined) {
                    return;
                }
                this.courseService.getAllUsersInCourseRole(courseId, slug).subscribe((usersResponse) => {
                    this.allCourseGroupUsers.set(usersResponse.body!);
                    this.isLoading.set(false);
                });
            });
        });
    };
}
