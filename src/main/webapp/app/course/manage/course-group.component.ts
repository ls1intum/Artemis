import { Component, OnDestroy, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
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
import { Observable, of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { UserService } from 'app/core/user/user.service';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { iconsAsHTML } from 'app/utils/icons.utils';

@Component({
    selector: 'jhi-course-group',
    templateUrl: './course-group.component.html',
    styles: [
        'ngb-typeahead-window .dropdown-item.active { background-color: #28a745; }',
        'ngb-typeahead-window .dropdown-item.already-member { color: #212529; background-color: #E9F6EC; }',
        'ngb-typeahead-window .dropdown-item.already-member:hover { background-color: #D4EDD9; }',
        'ngb-typeahead-window .dropdown-item { display: flex; justify-content: space-between }',
    ],
    encapsulation: ViewEncapsulation.None,
})
export class CourseGroupComponent implements OnInit, OnDestroy {
    @ViewChild(DataTableComponent) dataTable: DataTableComponent;

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
        private userService: UserService,
    ) {}

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
                this.courseService.getAllUsersInCourseGroup(this.course.id, this.courseGroup).subscribe((usersResponse) => {
                    this.users = usersResponse.body!;
                    this.isLoading = false;
                });
            });
        });
    }

    /**
     * Receives the search text and filter results from DataTableComponent, modifies them and returns the result which will be used by ngbTypeahead.
     *
     * 1. Perform server-side search using the search text
     * 2. Return results from server query that contain all users (instead of only the client-side users who are group members already)
     *
     * @param stream$ stream of searches of the format {text, entities} where entities are the results
     * @return stream of users for the autocomplete
     */
    searchAllUsers = (stream$: Observable<{ text: string; entities: User[] }>): Observable<User[]> => {
        return stream$.pipe(
            switchMap(({ text: loginOrName, entities: users }) => {
                if (loginOrName.length < 3) {
                    return of([]);
                }
                return this.userService
                    .search(loginOrName)
                    .pipe(map((usersResponse) => usersResponse.body!))
                    .pipe(
                        catchError(() => {
                            return of([]);
                        }),
                    );
            }),
            tap((users) => {
                setTimeout(() => {
                    for (let i = 0; i < this.dataTable.typeaheadButtons.length; i++) {
                        const isAlreadyInCourseGroup = this.users.map((user) => user.id).includes(users[i].id);
                        this.dataTable.typeaheadButtons[i].insertAdjacentHTML('beforeend', iconsAsHTML[isAlreadyInCourseGroup ? 'users' : 'users-plus']);
                        if (isAlreadyInCourseGroup) {
                            this.dataTable.typeaheadButtons[i].classList.add('already-member');
                        }
                    }
                });
            }),
        );
    };

    /**
     * Receives the user that was selected in the autocomplete and the callback from DataTableComponent.
     * The callback inserts the search term of the selected entity into the search field and updates the displayed users.
     *
     * @param user The selected user from the autocomplete suggestions
     * @param callback Function that can be called with the selected user to trigger the DataTableComponent default behavior
     */
    onAutocompleteSelect = (user: User, callback: (user: User) => void): void => {
        callback(user);
    };

    /**
     * Remove user from course group
     *
     * @param user User that should be removed from the currently viewed course group
     */
    removeFromGroup(user: User) {
        if (user.login) {
            this.courseService.removeUserFromCourseGroup(this.course.id, this.courseGroup, user.login).subscribe(() => {
                this.users = this.users.filter((u) => u.login !== user.login);
            });
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
        const { name, login } = user;
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
