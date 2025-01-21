import { Component, OnInit, inject } from '@angular/core';
import { Course, CourseGroup, courseGroups } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { UserService } from 'app/core/user/user.service';
import { Subscription } from 'rxjs';
import { capitalize } from 'lodash-es';
import { CourseGroupComponent } from 'app/shared/course-group/course-group.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { captureException } from '@sentry/angular';

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

    allCourseGroupUsers: User[] = [];
    course: Course;
    courseGroup: CourseGroup;
    isLoading = false;
    isAdmin = false;
    paramSub: Subscription;
    filteredUsersSize = 0;

    readonly capitalize = capitalize;

    ngOnInit(): void {
        this.loadAll();
    }

    userSearch = (loginOrName: string) => this.userService.search(loginOrName);

    addToGroup = (login: string) => this.courseService.addUserToCourseGroup(this.course.id!, this.courseGroup, login);

    removeFromGroup = (login: string) => this.courseService.removeUserFromCourseGroup(this.course.id!, this.courseGroup, login);

    /**
     * Update the number of filtered users
     *
     * @param filteredUsersSize Total number of users after filters have been applied
     */
    handleUsersSizeChange = (filteredUsersSize: number) => (this.filteredUsersSize = filteredUsersSize);

    /**
     * Load all users of given course group.
     * Redirect to course-management when given course group is in predefined standard course groups.
     */
    loadAll = () => {
        this.isLoading = true;
        this.isAdmin = this.accountService.isAdmin();
        this.route.parent!.data.subscribe(({ course }) => {
            this.course = course;
            this.paramSub = this.route.params.subscribe((params) => {
                this.courseGroup = params['courseGroup'];
                if (!courseGroups.includes(this.courseGroup)) {
                    return this.router.navigate(['/course-management']);
                }
                this.courseService.getAllUsersInCourseGroup(this.course.id!, this.courseGroup).subscribe((usersResponse) => {
                    this.allCourseGroupUsers = usersResponse.body!;
                    this.isLoading = false;
                });
            });
        });
    };

    /**
     * Property that returns the course group name, e.g. "artemis-test-students"
     */
    get courseGroupName() {
        switch (this.courseGroup) {
            case CourseGroup.STUDENTS:
                return this.course.studentGroupName;
            case CourseGroup.TUTORS:
                return this.course.teachingAssistantGroupName;
            case CourseGroup.EDITORS:
                return this.course.editorGroupName;
            case CourseGroup.INSTRUCTORS:
                return this.course.instructorGroupName;
            default:
                captureException('Unknown course group: ' + this.courseGroup);
                return undefined;
        }
    }

    /**
     * Property that returns the course group entity name, e.g. "students" or "tutors".
     * If the count of users is exactly 1, singular is used instead of plural.
     */
    get courseGroupEntityName(): string {
        return this.allCourseGroupUsers.length === 1 ? this.courseGroup.slice(0, -1) : this.courseGroup;
    }

    get exportFilename(): string {
        return this.courseGroupEntityName.charAt(0).toUpperCase() + this.courseGroupEntityName.slice(1) + ' ' + this.course.title;
    }
}
