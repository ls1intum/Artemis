import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Course, CourseRoleSlug, courseRoleSegments } from 'app/course/shared/entities/course.model';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { UserService } from 'app/account/user/shared/user.service';
import { Subscription } from 'rxjs';
import { capitalize } from 'lodash-es';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseGroupComponent } from 'app/course/shared/course-group/course-group.component';

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
    paramSub: Subscription;
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

    addToRole = (login: string) => this.courseService.addUserToCourseRole(this.course()!.id!, this.courseRoleSlug()!, login);

    removeFromRole = (login: string) => this.courseService.removeUserFromCourseRole(this.course()!.id!, this.courseRoleSlug()!, login);

    /**
     * Update the number of filtered users
     *
     * @param filteredUsersSize Total number of users after filters have been applied
     */
    handleUsersSizeChange = (filteredUsersSize: number) => this.filteredUsersSize.set(filteredUsersSize);

    /**
     * Load all users of given course group.
     * Redirect to course-management when given course group is in predefined standard course groups.
     */
    loadAll = () => {
        this.isLoading.set(true);
        this.isAdmin.set(this.accountService.isAdmin());
        this.route.parent!.data.subscribe(({ course }) => {
            this.course.set(course);
            this.paramSub = this.route.params.subscribe((params) => {
                this.courseRoleSlug.set(params['courseRoleSlug']);
                if (!courseRoleSegments.includes(this.courseRoleSlug()!)) {
                    return this.router.navigate(['/course-management']);
                }
                this.courseService.getAllUsersInCourseRole(this.course()!.id!, this.courseRoleSlug()!).subscribe((usersResponse) => {
                    this.allCourseGroupUsers.set(usersResponse.body!);
                    this.isLoading.set(false);
                });
            });
        });
    };
}
