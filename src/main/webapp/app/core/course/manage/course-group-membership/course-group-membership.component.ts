import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Course, CourseGroup, courseGroups } from 'app/core/course/shared/entities/course.model';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { UserService } from 'app/core/user/shared/user.service';
import { Subscription } from 'rxjs';
import { capitalize } from 'lodash-es';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { captureException } from '@sentry/angular';
import { CourseGroupComponent } from 'app/core/course/shared/course-group/course-group.component';

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
    courseGroup = signal<CourseGroup | undefined>(undefined);
    isLoading = signal(false);
    isAdmin = signal(false);
    paramSub: Subscription;
    filteredUsersSize = signal(0);

    readonly capitalize = capitalize;

    /**
     * Property that returns the course group name, e.g. "artemis-test-students"
     */
    courseGroupName = computed(() => {
        const course = this.course();
        const courseGroup = this.courseGroup();
        if (!course || !courseGroup) {
            return undefined;
        }
        switch (courseGroup) {
            case CourseGroup.STUDENTS:
                return course.studentGroupName;
            case CourseGroup.TUTORS:
                return course.teachingAssistantGroupName;
            case CourseGroup.EDITORS:
                return course.editorGroupName;
            case CourseGroup.INSTRUCTORS:
                return course.instructorGroupName;
            default:
                captureException('Unknown course group: ' + courseGroup);
                return undefined;
        }
    });

    /**
     * Property that returns the course group entity name, e.g. "students" or "tutors".
     * If the count of users is exactly 1, singular is used instead of plural.
     */
    courseGroupEntityName = computed(() => {
        const courseGroup = this.courseGroup();
        if (!courseGroup) {
            return '';
        }
        return this.allCourseGroupUsers().length === 1 ? courseGroup.slice(0, -1) : courseGroup;
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

    addToGroup = (login: string) => this.courseService.addUserToCourseGroup(this.course()!.id!, this.courseGroup()!, login);

    removeFromGroup = (login: string) => this.courseService.removeUserFromCourseGroup(this.course()!.id!, this.courseGroup()!, login);

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
                this.courseGroup.set(params['courseGroup']);
                if (!courseGroups.includes(this.courseGroup()!)) {
                    return this.router.navigate(['/course-management']);
                }
                this.courseService.getAllUsersInCourseGroup(this.course()!.id!, this.courseGroup()!).subscribe((usersResponse) => {
                    this.allCourseGroupUsers.set(usersResponse.body!);
                    this.isLoading.set(false);
                });
            });
        });
    };
}
