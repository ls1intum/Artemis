import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { AlertService } from 'app/core/alert/alert.service';
import { User } from 'app/core/user/user.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course, CourseGroup, courseGroups } from 'app/entities/course.model';
import { capitalize } from 'lodash';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';

@Component({
    selector: 'jhi-course-group',
    templateUrl: './course-group.component.html',
})
export class CourseGroupComponent implements OnInit, OnDestroy {
    readonly ActionType = ActionType;

    course: Course;
    courseGroup: CourseGroup;
    users: User[] = [];
    filteredUsersSize = 0;
    paramSub: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    isLoading: boolean;

    constructor(
        private router: Router,
        private route: ActivatedRoute,
        private jhiAlertService: AlertService,
        private eventManager: JhiEventManager,
        private courseService: CourseManagementService,
    ) {
    }

    ngOnInit() {
        this.loadAll();
    }

    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }

    loadAll() {
        this.isLoading = true;
        this.route.data.subscribe(({ course }) => {
            this.course = course;
            this.paramSub = this.route.params.subscribe((params) => {
                this.courseGroup = params['courseGroup'];
                if (!courseGroups.includes(this.courseGroup)) {
                    return this.router.navigate(['/course-management']);
                }
                this.courseService
                    .getAllUsersInCourseGroup(this.course.id, this.courseGroup)
                    .subscribe((usersResponse) => {
                        this.users = usersResponse.body!;
                        this.isLoading = false;
                    });
            });
        });
    }

    /**
     * Remove user from course group
     *
     * @param user User that should be removed from the currently viewed course group
     */
    removeFromGroup(user: User) {
        if (user.login) {
            this.courseService.removeUserFromCourseGroup(this.course.id, this.courseGroup, user.login);
        }
    }

    /**
     * Property that returns the course group name, e.g. "artemis-test-students"
     */
    get courseGroupName() {
        switch (this.courseGroup) {
            case CourseGroup.STUDENTS:
                return this.course.studentGroupName;
            case CourseGroup.TUTORS:
                return this.course.teachingAssistantGroupName;
            case CourseGroup.INSTRUCTORS:
                return this.course.instructorGroupName;
        }
    }

    /**
     * Property that returns the capitalized course group, e.g. "Students" or "Tutors"
     */
    get courseGroupCapitalized() {
        return capitalize(this.courseGroup);
    }

    /**
     * Update the number of filtered users
     *
     * @param filteredUsersSize Total number of users after filters have been applied
     */
    handleUsersSizeChange = (filteredUsersSize: number) => {
        this.filteredUsersSize = filteredUsersSize;
    };

    /**
     * Formats the results in the autocomplete overlay.
     *
     * @param user
     */
    searchResultFormatter = (user: User) => {
        const { login, } = user;
        return `${name} (${login})`;
    };

    /**
     * Converts a user object to a string that can be searched for. This is
     * used by the autocomplete select inside the data table.
     *
     * @param user User
     */
    searchTextFromUser = (user: User): string => {
        return user.login || '';
    };
}
