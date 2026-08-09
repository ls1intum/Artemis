import { ChangeDetectionStrategy, Component, OnInit, TrackByFunction, computed, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject, map, of } from 'rxjs';
import { Organization } from 'app/admin/organization-management/organization.model';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { User } from 'app/account/user/user.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { faUserPlus, faUserSlash } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { CellTemplateRef, ColumnDef, TumUiButtonDirective, TumUiTableComponent, TumUiTableQueryEvent } from '@tumaet/ui-angular';
import { buildDbQueryFromTableEvent } from 'app/shared-ui/tum-ui-integration/tum-ui-table-request-builder';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { UserRegistrationModalComponent } from 'app/shared-ui/user-registration-modal/user-registration-modal.component';
import { UserForRegistration } from 'app/shared-ui/user-registration-modal/user-for-registration.model';
/**
 * Admin component for viewing and managing organization details.
 * Allows adding and removing users from organizations and browsing their courses.
 */
@Component({
    selector: 'jhi-organization-management-detail',
    templateUrl: './organization-management-detail.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        TranslateDirective,
        RouterLink,
        FaIconComponent,
        DeleteButtonDirective,
        AdminTitleBarTitleDirective,
        TumUiTableComponent,
        UserRegistrationModalComponent,
        TumUiButtonDirective,
    ],
})
export class OrganizationManagementDetailComponent implements OnInit {
    private readonly organizationService = inject(OrganizationManagementService);
    private readonly alertService = inject(AlertService);
    private readonly route = inject(ActivatedRoute);

    /** The organization being viewed (metadata only) */
    readonly organization = signal<Organization>(new Organization());

    /** The numeric id of the current organization */
    readonly organizationId = signal<number | undefined>(undefined);

    /** Users table state */
    readonly users = signal<User[]>([]);
    readonly usersTotal = signal(0);
    readonly usersLoading = signal(false);

    /** Courses table state */
    readonly courses = signal<Course[]>([]);
    readonly coursesTotal = signal(0);
    readonly coursesLoading = signal(false);

    readonly ActionType = ActionType;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    protected readonly faUserSlash = faUserSlash;
    protected readonly faUserPlus = faUserPlus;

    // Row identity so the tables reuse row DOM across reloads instead of rebuilding every row.
    protected readonly trackByUserId: TrackByFunction<User> = (_, user) => user.id;
    protected readonly trackByCourseId: TrackByFunction<Course> = (_, course) => course.id;

    /** Template ref for custom user-id cell rendering */
    readonly userIdTemplate = viewChild<CellTemplateRef<User>>('userIdTemplate');

    /** Template ref for custom course-id cell rendering */
    readonly courseIdTemplate = viewChild<CellTemplateRef<Course>>('courseIdTemplate');

    readonly userRegistrationModal = viewChild.required(UserRegistrationModalComponent);

    readonly userColumns = computed<ColumnDef<User>[]>(() => [
        { field: 'id', headerKey: 'global.field.id', sort: true, width: '80px', templateRef: this.userIdTemplate() },
        { field: 'login', headerKey: 'artemisApp.userManagement.login', sort: true, width: '200px' },
        { field: 'name', headerKey: 'artemisApp.userManagement.name', sort: true, width: '250px' },
        { field: 'email', headerKey: 'artemisApp.userManagement.email', sort: true },
    ]);

    readonly courseColumns = computed<ColumnDef<Course>[]>(() => [
        { field: 'id', headerKey: 'global.field.id', sort: true, width: '80px', templateRef: this.courseIdTemplate() },
        { field: 'title', headerKey: 'artemisApp.course.title', sort: true, width: '300px' },
        { field: 'shortName', headerKey: 'artemisApp.course.shortName', sort: true, width: '150px' },
    ]);

    /** Last load event for users table, used to refresh after add/remove */
    private lastUsersLoadEvent: TumUiTableQueryEvent | undefined;

    private usersLoadId = 0;
    private coursesLoadId = 0;

    readonly searchFn = (searchTerm: string, page: number, size: number) => {
        const id = this.organizationId();
        if (!id) return of({ content: [], totalElements: 0 });
        return this.organizationService.searchUsersForOrganizationRegistration(id, searchTerm, page, size);
    };

    readonly registerFn = (users: UserForRegistration[]): Observable<void> => {
        const id = this.organizationId();
        if (!id || users.length === 0) return of(undefined);
        return this.organizationService
            .addUsersToOrganization(
                id,
                users.map((u) => u.login),
            )
            .pipe(map(() => undefined));
    };

    ngOnInit() {
        this.route.data.subscribe(({ organization }) => {
            if (organization) {
                const id: number | undefined = organization.body ? organization.body.id : organization.id;
                if (id === undefined || !Number.isFinite(id)) {
                    return;
                }
                this.organizationId.set(id);
                this.organizationService.getOrganizationById(id).subscribe({
                    next: (org) => {
                        this.organization.set(org);
                    },
                    error: (err: HttpErrorResponse) => {
                        onError(this.alertService, err);
                    },
                });
            }
        });
    }

    openAddUsersModal(): void {
        this.userRegistrationModal().open();
    }

    onUsersRegistered(): void {
        if (this.lastUsersLoadEvent) {
            this.loadUsers(this.lastUsersLoadEvent);
        }
    }

    loadUsers(event: TumUiTableQueryEvent): void {
        const id = this.organizationId();
        if (id === undefined) {
            return;
        }
        this.lastUsersLoadEvent = event;
        this.usersLoading.set(true);
        const requestId = ++this.usersLoadId;
        const query = buildDbQueryFromTableEvent(event);
        this.organizationService.getOrganizationUsers(id, query).subscribe({
            next: (res) => {
                if (requestId !== this.usersLoadId) return;
                this.users.set(res.content);
                this.usersTotal.set(res.totalElements);
                this.usersLoading.set(false);
            },
            error: (err: HttpErrorResponse) => {
                if (requestId !== this.usersLoadId) return;
                this.users.set([]);
                this.usersTotal.set(0);
                this.usersLoading.set(false);
                onError(this.alertService, err);
            },
        });
    }

    loadCourses(event: TumUiTableQueryEvent): void {
        const id = this.organizationId();
        if (id === undefined) {
            return;
        }
        this.coursesLoading.set(true);
        const requestId = ++this.coursesLoadId;
        const query = buildDbQueryFromTableEvent(event);
        this.organizationService.getOrganizationCourses(id, query).subscribe({
            next: (res) => {
                if (requestId !== this.coursesLoadId) return;
                this.courses.set(res.content);
                this.coursesTotal.set(res.totalElements);
                this.coursesLoading.set(false);
            },
            error: (err: HttpErrorResponse) => {
                if (requestId !== this.coursesLoadId) return;
                this.courses.set([]);
                this.coursesTotal.set(0);
                this.coursesLoading.set(false);
                onError(this.alertService, err);
            },
        });
    }

    /**
     * Remove user from organization and refresh the current users page
     *
     * @param user User that should be removed from the currently viewed organization
     */
    removeFromOrganization(user: User) {
        const id = this.organizationId();
        if (id !== undefined && user.login) {
            this.organizationService.removeUserFromOrganization(id, user.login).subscribe({
                next: () => {
                    this.dialogErrorSource.next('');
                    if (this.lastUsersLoadEvent) {
                        this.loadUsers(this.lastUsersLoadEvent);
                    }
                },
                error: (error: HttpErrorResponse) => {
                    this.dialogErrorSource.next(error.message);
                },
            });
        }
    }
}
