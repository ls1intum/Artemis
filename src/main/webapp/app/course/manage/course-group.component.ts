import { Component, OnDestroy, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { Subscription } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { User } from 'app/core/user/user.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course, CourseGroup, courseGroups } from 'app/entities/course.model';
import { capitalize } from 'lodash-es';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Observable, of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { UserService } from 'app/core/user/user.service';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { iconsAsHTML } from 'app/utils/icons.utils';
import { AccountService } from 'app/core/auth/account.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { ExportToCsv } from 'export-to-csv';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { faUserSlash } from '@fortawesome/free-solid-svg-icons';

const NAME_KEY = 'Name';
const USERNAME_KEY = 'Username';
const EMAIL_KEY = 'Email';
const REGISTRATION_NUMBER_KEY = 'Registration Number';

const cssClasses = {
    alreadyMember: 'already-member',
    newlyAddedMember: 'newly-added-member',
};

@Component({
    selector: 'jhi-course-group',
    templateUrl: './course-group.component.html',
    styleUrls: ['./course-group.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class CourseGroupComponent implements OnInit, OnDestroy {
    @ViewChild(DataTableComponent) dataTable: DataTableComponent;

    readonly ActionType = ActionType;
    readonly capitalize = capitalize;

    course: Course;
    courseGroup: CourseGroup;
    allCourseGroupUsers: User[] = [];
    filteredUsersSize = 0;
    paramSub: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    isAdmin = false;
    isLoading = false;
    isSearching = false;
    searchFailed = false;
    searchNoResults = false;
    isTransitioning = false;
    rowClass: string | undefined = undefined;

    // Icons
    faDownload = faDownload;
    faUserSlash = faUserSlash;

    constructor(
        private router: Router,
        private route: ActivatedRoute,
        private alertService: AlertService,
        private eventManager: EventManager,
        private courseService: CourseManagementService,
        private userService: UserService,
        private accountService: AccountService,
    ) {}

    /**
     * Init the course group component by loading all users of course group.
     */
    ngOnInit() {
        this.loadAll();
    }

    /**
     * Unsubscribe dialog error source on component destruction.
     */
    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Load all users of given course group.
     * Redirect to course-management when given course group is in predefined standard course groups.
     */
    loadAll() {
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
            switchMap(({ text: loginOrName }) => {
                this.searchFailed = false;
                this.searchNoResults = false;
                if (loginOrName.length < 3) {
                    return of([]);
                }
                this.isSearching = true;
                return this.userService
                    .search(loginOrName)
                    .pipe(map((usersResponse) => usersResponse.body!))
                    .pipe(
                        tap((users) => {
                            if (users.length === 0) {
                                this.searchNoResults = true;
                            }
                        }),
                        catchError(() => {
                            this.searchFailed = true;
                            return of([]);
                        }),
                    );
            }),
            tap(() => {
                this.isSearching = false;
            }),
            tap((users) => {
                setTimeout(() => {
                    for (let i = 0; i < this.dataTable.typeaheadButtons.length; i++) {
                        const isAlreadyInCourseGroup = this.allCourseGroupUsers.map((user) => user.id).includes(users[i].id);
                        this.dataTable.typeaheadButtons[i].insertAdjacentHTML('beforeend', iconsAsHTML[isAlreadyInCourseGroup ? 'users' : 'users-plus']);
                        if (isAlreadyInCourseGroup) {
                            this.dataTable.typeaheadButtons[i].classList.add(cssClasses.alreadyMember);
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
        // If the user is not part of this course group yet, perform the server call to add them
        if (!this.allCourseGroupUsers.map((u) => u.id).includes(user.id) && user.login) {
            this.isTransitioning = true;
            this.courseService.addUserToCourseGroup(this.course.id!, this.courseGroup, user.login).subscribe({
                next: () => {
                    this.isTransitioning = false;

                    // Add newly added user to the list of all users in the course group
                    this.allCourseGroupUsers.push(user);

                    // Hand back over to the data table for updating
                    callback(user);

                    // Flash green background color to signal to the user that this record was added
                    this.flashRowClass(cssClasses.newlyAddedMember);
                },
                error: () => {
                    this.isTransitioning = false;
                },
            });
        } else {
            // Hand back over to the data table
            callback(user);
        }
    };

    /**
     * Remove user from course group
     *
     * @param user User that should be removed from the currently viewed course group
     */
    removeFromGroup(user: User) {
        if (user.login) {
            this.courseService.removeUserFromCourseGroup(this.course.id!, this.courseGroup, user.login).subscribe({
                next: () => {
                    this.allCourseGroupUsers = this.allCourseGroupUsers.filter((u) => u.login !== user.login);
                    this.dialogErrorSource.next('');
                },
                error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
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
            case CourseGroup.EDITORS:
                return this.course.editorGroupName;
            case CourseGroup.INSTRUCTORS:
                return this.course.instructorGroupName;
        }
    }

    /**
     * Property that returns the course group entity name, e.g. "students" or "tutors".
     * If the count of users is exactly 1, singular is used instead of plural.
     */
    get courseGroupEntityName(): string {
        return this.allCourseGroupUsers.length === 1 ? this.courseGroup.slice(0, -1) : this.courseGroup;
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

    /**
     * Computes the row class that is being added to all rows of the datatable
     */
    dataTableRowClass = () => {
        return this.rowClass;
    };

    /**
     * Can be used to highlight rows temporarily by flashing a certain css class
     *
     * @param className Name of the class to be applied to all rows
     */
    flashRowClass = (className: string) => {
        this.rowClass = className;
        setTimeout(() => (this.rowClass = undefined));
    };

    /**
     * Method for exporting the csv with the needed data
     */
    exportUserInformation() {
        if (this.allCourseGroupUsers.length > 0) {
            const rows: any[] = this.allCourseGroupUsers.map((user: User) => {
                const data = {};
                data[NAME_KEY] = user.name?.trim() ?? '';
                data[USERNAME_KEY] = user.login?.trim() ?? '';
                data[EMAIL_KEY] = user.email?.trim() ?? '';
                data[REGISTRATION_NUMBER_KEY] = user.visibleRegistrationNumber?.trim() ?? '';
                return data;
            });
            const keys = [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY];
            this.exportAsCsv(rows, keys);
        }
    }

    /**
     * Method for generating the csv file containing the user information
     *
     * @param rows the data to export
     * @param keys the keys of the data
     */
    exportAsCsv(rows: any[], keys: string[]) {
        const options = {
            fieldSeparator: ';',
            quoteStrings: '"',
            showLabels: true,
            showTitle: false,
            filename: this.courseGroupEntityName.charAt(0).toUpperCase() + this.courseGroupEntityName.slice(1) + ' ' + this.course.title,
            useTextFile: false,
            useBom: true,
            headers: keys,
        };
        const csvExporter = new ExportToCsv(options);
        csvExporter.generateCsv(rows);
    }
}
