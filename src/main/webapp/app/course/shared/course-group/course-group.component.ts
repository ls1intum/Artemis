import { Component, computed, effect, inject, input, model, signal, untracked, viewChild } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subject, forkJoin, of } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { AlertService } from 'app/foundation/service/alert.service';
import { User } from 'app/account/user/user.model';
import { Course, CourseRoleSlug } from 'app/course/shared/entities/course.model';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { EMAIL_KEY, NAME_KEY, REGISTRATION_NUMBER_KEY, USERNAME_KEY } from 'app/shared-ui/export/export-constants';
import { RouterLink } from '@angular/router';
import { addPublicFilePrefix } from 'app/app.constants';
import { UsersImportDialogComponent } from 'app/shared-ui/user-import/dialog/users-import-dialog.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ExportUserInformationRow, exportUserInformationAsCsv } from 'app/shared-ui/user-import/util/write-users-to-csv';
import { CellTemplateRef, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared-ui/table-view/table-view';
import { TableLazyLoadEvent } from 'primeng/table';
import { buildDbQueryFromLazyEvent } from 'app/shared-ui/table-view/request-builder';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { UserRegistrationModalComponent } from 'app/shared-ui/user-registration-modal/user-registration-modal.component';
import { UserForRegistration, UserSearchResult } from 'app/shared-ui/user-registration-modal/user-for-registration.model';
import { StudentDTO } from 'app/core/shared/entities/student-dto.model';
import { Button, ButtonDirective } from 'primeng/button';
import { CourseRoleMember } from 'app/course/shared/course-group/course-role-member.model';
import { hydrate } from 'app/foundation/util/deep-clone.util';

@Component({
    selector: 'jhi-course-group',
    templateUrl: './course-group.component.html',
    styleUrls: ['./course-group.component.scss'],
    imports: [
        UsersImportDialogComponent,
        TranslateDirective,
        TableViewComponent,
        RouterLink,
        ProfilePictureComponent,
        DeleteButtonDirective,
        UserRegistrationModalComponent,
        Button,
        ButtonDirective,
    ],
})
export class CourseGroupComponent {
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly alertService = inject(AlertService);

    private readonly tableViewRef = viewChild(TableViewComponent);
    private lazyLoadGeneration = 0;
    readonly profilePictureTemplate = viewChild<CellTemplateRef<CourseRoleMember>>('profilePictureTemplate');
    readonly loginTemplate = viewChild<CellTemplateRef<CourseRoleMember>>('loginTemplate');
    private readonly addUsersModal = viewChild(UserRegistrationModalComponent);
    private readonly importDialog = viewChild(UsersImportDialogComponent);

    constructor() {
        // The parent route reuses this component when only the role slug (e.g. members/:courseRoleSlug) changes,
        // so the table's own initial lazy load never re-fires on its own. Reload it whenever the role changes,
        // skipping the very first run since the table already performs its initial load.
        let isFirstRun = true;
        effect(() => {
            this.courseRoleSlug();
            this.course();
            if (isFirstRun) {
                isFirstRun = false;
                return;
            }
            untracked(() => this.tableViewRef()?.reset());
        });
        effect(() => this.handleUsersSizeChange()(this.displayedRows().length));
    }

    readonly isAdmin = input(false);
    readonly course = input.required<Course>();
    readonly tutorialGroup = input<TutorialGroup | undefined>(undefined);
    readonly courseRoleSlug = input.required<CourseRoleSlug>();
    readonly allGroupUsers = model<User[]>([]);
    readonly paginated = input(true);
    readonly hiddenColumnFields = input<string[]>([]);
    readonly profilePictureHeaderKey = input('artemisApp.course.courseGroup.profilePicture');
    readonly allowAddingUsers = input(true);
    readonly allowRemovingUsers = input(true);
    readonly isDisabled = input(false);
    readonly exportFileName = input<string>();
    readonly userSearch = input<(loginOrName: string) => Observable<HttpResponse<User[]>>>();
    readonly addUserToGroup = input<(login: string) => Observable<HttpResponse<void>>>(() => of(new HttpResponse<void>()));
    readonly removeUserFromGroup = input<(login: string) => Observable<HttpResponse<void>>>(() => of(new HttpResponse<void>()));
    readonly handleUsersSizeChange = input<(usersSize: number) => void>(() => {});
    readonly removeUserQuestionKey = input('artemisApp.course.courseGroup.removeFromGroup.modalQuestion');

    protected readonly ActionType = ActionType;

    private readonly dialogErrorSource = new Subject<string>();
    readonly dialogError$ = this.dialogErrorSource.asObservable();

    readonly rows = signal<CourseRoleMember[]>([]);
    readonly totalRows = signal<number>(0);
    readonly isLoading = signal<boolean>(false);

    readonly displayedRows = computed<CourseRoleMember[]>(() => (this.paginated() ? this.rows() : this.allGroupUsers()));
    readonly displayedTotalRows = computed(() => (this.paginated() ? this.totalRows() : this.allGroupUsers().length));

    /** searchFn passed to the registration modal — searches all Artemis users, marks already-enrolled ones. */
    readonly searchUsersFn = computed(() => {
        const customSearch = this.userSearch();
        if (customSearch) {
            return (term: string, _page: number, _size: number): Observable<UserSearchResult> =>
                customSearch(term).pipe(
                    map((response) => {
                        const assignedIds = new Set(this.allGroupUsers().map((user) => user.id));
                        const content = (response.body ?? []).map((user) => this.toUserForRegistration(user, assignedIds.has(user.id)));
                        return { content, totalElements: content.length };
                    }),
                );
        }
        const courseId = this.course().id;
        const slug = this.courseRoleSlug();
        return (term: string, page: number, size: number) => this.courseManagementService.searchUsersForCourseRole(courseId!, slug, term, page, size);
    });

    /** registerFn passed to the registration modal — bulk-adds selected users via the existing import endpoint. */
    readonly registerUsersFn = computed(() => {
        if (!this.paginated()) {
            return (users: UserForRegistration[]): Observable<void> => {
                if (users.length === 0) return of(void 0);
                return forkJoin(users.map((user) => this.addUserToGroup()(user.login))).pipe(
                    tap(() => {
                        const existingIds = new Set(this.allGroupUsers().map((user) => user.id));
                        const addedUsers = users.filter((user) => !existingIds.has(user.id)).map((user) => this.fromUserForRegistration(user));
                        this.allGroupUsers.update((current) => [...current, ...addedUsers]);
                    }),
                    map(() => void 0),
                );
            };
        }
        const courseId = this.course().id;
        const slug = this.courseRoleSlug();
        return (users: UserForRegistration[]): Observable<void> => {
            if (!courseId) return of(void 0);
            // login is enough to resolve the user server-side; the rest are ignored.
            const dtos: StudentDTO[] = users.map((u) => ({ login: u.login, firstName: '', lastName: '', registrationNumber: '', email: '' }));
            return this.courseManagementService.addUsersToCourseRole(courseId, dtos, slug).pipe(
                tap((response) => {
                    // response.body lists the users that were NOT registered.
                    const notFound = response.body ?? [];
                    if (notFound.length > 0) {
                        const logins = notFound.map((u) => u.login).join(', ');
                        this.alertService.error('artemisApp.course.courseGroup.notFoundUsers', { logins });
                    }
                }),
                map(() => void 0),
            );
        };
    });

    readonly tableOptions = computed<TableViewOptions>(() => ({
        lazy: this.paginated(),
        paginated: this.paginated(),
        scrollable: true,
        scrollHeight: 'flex',
        searchPlaceholder: 'artemisApp.course.courseGroup.searchForUsers',
        initialSortField: 'name',
    }));

    readonly columns = computed<ColumnDef<CourseRoleMember>[]>(() => {
        const hiddenFields = new Set(this.hiddenColumnFields());
        return [
            {
                headerKey: this.profilePictureHeaderKey(),
                width: '5rem',
                templateRef: this.profilePictureTemplate(),
            },
            {
                field: 'login',
                headerKey: 'artemisApp.course.courseGroup.login',
                sort: true,
                width: '10rem',
                templateRef: this.loginTemplate(),
            },
            {
                field: 'visibleRegistrationNumber',
                headerKey: 'artemisApp.course.courseGroup.registrationNumber',
                sort: true,
                width: '10rem',
            },
            {
                field: 'name',
                headerKey: 'artemisApp.course.courseGroup.name',
                sort: true,
                width: '12rem',
            },
            {
                field: 'email',
                headerKey: 'artemisApp.course.courseGroup.email',
                sort: true,
            },
        ].filter((column) => {
            if (column.headerKey === this.profilePictureHeaderKey()) {
                return !hiddenFields.has('imageUrl');
            }
            return !column.field || !hiddenFields.has(column.field);
        });
    });

    onLazyLoad(event: TableLazyLoadEvent): void {
        if (!this.paginated()) {
            return;
        }
        const courseId = this.course().id;
        const slug = this.courseRoleSlug();
        if (!courseId || !slug) {
            return;
        }
        const search = buildDbQueryFromLazyEvent(event);
        const generation = ++this.lazyLoadGeneration;
        this.isLoading.set(true);
        this.courseManagementService.getPagedUsersInCourseRole(courseId, slug, search).subscribe({
            next: (result) => {
                if (generation !== this.lazyLoadGeneration) {
                    return;
                }
                this.rows.set(result.content);
                this.totalRows.set(result.totalElements);
                this.isLoading.set(false);
            },
            error: () => {
                if (generation === this.lazyLoadGeneration) {
                    // Clear stale rows instead of leaving the previous role's members on screen.
                    this.rows.set([]);
                    this.totalRows.set(0);
                    this.isLoading.set(false);
                }
            },
        });
    }

    /** Called after a new member was added, whether via the registration modal or CSV import. */
    onMembersAdded(): void {
        if (this.paginated()) {
            this.tableViewRef()?.reload();
        }
    }

    openAddUsersModal(): void {
        this.addUsersModal()?.open();
    }

    openImportDialog(event: MouseEvent): void {
        event.stopPropagation();
        this.importDialog()?.open();
    }

    /**
     * Remove user from course group.
     *
     * @param member Member that should be removed from the currently viewed course group
     */
    removeFromGroup(member: CourseRoleMember): void {
        if (member.login) {
            this.removeUserFromGroup()(member.login).subscribe({
                next: () => {
                    this.dialogErrorSource.next('');
                    if (this.paginated()) {
                        this.tableViewRef()?.reloadAfterRemoval();
                    } else {
                        this.allGroupUsers.update((users) => users.filter((user) => user.login !== member.login));
                    }
                },
                error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
            });
        }
    }

    /**
     * Export all group members as CSV. Fetches all users from the server to avoid exporting only the current page.
     */
    exportUserInformation(): void {
        const courseId = this.course().id;
        const slug = this.courseRoleSlug();
        if (!courseId || !slug) {
            return;
        }
        const fileName = this.exportFileName() ?? CourseGroupComponent.buildExportFileName(slug, this.course().title);
        this.courseManagementService.getAllUsersInCourseRole(courseId, slug).subscribe({
            next: (res) => {
                const users = res.body ?? [];
                if (users.length === 0) {
                    return;
                }
                const exportRows: ExportUserInformationRow[] = users.map((user: User): ExportUserInformationRow => ({
                    [NAME_KEY]: user.name?.trim() ?? '',
                    [USERNAME_KEY]: user.login?.trim() ?? '',
                    [EMAIL_KEY]: user.email?.trim() ?? '',
                    [REGISTRATION_NUMBER_KEY]: user.visibleRegistrationNumber?.trim() ?? '',
                }));
                exportUserInformationAsCsv(exportRows, [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY], fileName);
            },
        });
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;

    private toUserForRegistration(user: User, isRegistered: boolean): UserForRegistration {
        return {
            id: user.id!,
            login: user.login!,
            name: user.name ?? '',
            email: user.email,
            registrationNumber: user.visibleRegistrationNumber,
            profilePictureUrl: user.imageUrl,
            isRegistered,
        };
    }

    private fromUserForRegistration(user: UserForRegistration): User {
        return hydrate(new User(), {
            id: user.id,
            login: user.login,
            name: user.name,
            email: user.email,
            visibleRegistrationNumber: user.registrationNumber,
            imageUrl: user.profilePictureUrl,
        });
    }

    /** Derives the export filename from the role slug and course title, e.g. "Students My Course". */
    private static buildExportFileName(slug: CourseRoleSlug, courseTitle: string | undefined): string {
        if (!courseTitle) {
            return '';
        }
        return slug.charAt(0).toUpperCase() + slug.slice(1) + ' ' + courseTitle;
    }
}
