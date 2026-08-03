import { Component, computed, inject, input, signal, viewChild } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subject, of } from 'rxjs';
import { User } from 'app/account/user/user.model';
import { Course, CourseRoleSlug } from 'app/course/shared/entities/course.model';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { faDownload, faUserSlash } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { EMAIL_KEY, NAME_KEY, REGISTRATION_NUMBER_KEY, USERNAME_KEY } from 'app/shared-ui/export/export-constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink } from '@angular/router';
import { addPublicFilePrefix } from 'app/app.constants';
import { UsersImportButtonComponent } from 'app/shared-ui/user-import/button/users-import-button.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ExportUserInformationRow, exportUserInformationAsCsv } from 'app/shared-ui/user-import/util/write-users-to-csv';
import { CellTemplateRef, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared-ui/table-view/table-view';
import { TableLazyLoadEvent } from 'primeng/table';
import { buildDbQueryFromLazyEvent } from 'app/shared-ui/table-view/request-builder';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';

@Component({
    selector: 'jhi-course-group',
    templateUrl: './course-group.component.html',
    styleUrls: ['./course-group.component.scss'],
    imports: [UsersImportButtonComponent, FaIconComponent, TranslateDirective, TableViewComponent, RouterLink, ProfilePictureComponent, DeleteButtonDirective],
})
export class CourseGroupComponent {
    private readonly courseManagementService = inject(CourseManagementService);

    private readonly tableViewRef = viewChild(TableViewComponent);
    readonly profilePictureTemplate = viewChild<CellTemplateRef<User>>('profilePictureTemplate');
    readonly loginTemplate = viewChild<CellTemplateRef<User>>('loginTemplate');

    readonly isAdmin = input(false);
    readonly course = input.required<Course>();
    readonly tutorialGroup = input<TutorialGroup | undefined>(undefined);
    readonly courseRoleSlug = input.required<CourseRoleSlug>();
    readonly removeUserFromGroup = input<(login: string) => Observable<HttpResponse<void>>>(() => of(new HttpResponse<void>()));

    protected readonly ActionType = ActionType;

    private readonly dialogErrorSource = new Subject<string>();
    readonly dialogError$ = this.dialogErrorSource.asObservable();

    readonly rows = signal<User[]>([]);
    readonly totalRows = signal<number>(0);
    /** Unfiltered total — only updated when no search term is active, so the export button stays visible during searches. */
    readonly totalMembers = signal<number>(0);
    readonly isLoading = signal<boolean>(false);
    /** Export filename derived from the role slug and course title, e.g. "Students My Course". */
    readonly exportFileName = computed<string>(() => {
        const slug = this.courseRoleSlug();
        const courseTitle = this.course()?.title;
        if (!slug || !courseTitle) {
            return '';
        }
        return slug.charAt(0).toUpperCase() + slug.slice(1) + ' ' + courseTitle;
    });

    protected readonly faDownload = faDownload;
    protected readonly faUserSlash = faUserSlash;

    readonly tableOptions: TableViewOptions = {
        scrollable: true,
        scrollHeight: 'flex',
        searchPlaceholder: 'artemisApp.course.courseGroup.searchForUsers',
    };

    readonly columns = computed<ColumnDef<User>[]>(() => [
        {
            headerKey: 'artemisApp.course.courseGroup.profilePicture',
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
    ]);

    onLazyLoad(event: TableLazyLoadEvent): void {
        const courseId = this.course().id;
        const slug = this.courseRoleSlug();
        if (!courseId || !slug) {
            return;
        }
        const search = buildDbQueryFromLazyEvent(event);
        this.isLoading.set(true);
        this.courseManagementService.getPagedUsersInCourseRole(courseId, slug, search).subscribe({
            next: (result) => {
                this.rows.set(result.content);
                this.totalRows.set(result.totalElements);
                if (!search.searchTerm) {
                    this.totalMembers.set(result.totalElements);
                }
                this.isLoading.set(false);
            },
            error: () => {
                this.isLoading.set(false);
            },
        });
    }

    onImportDone(): void {
        this.tableViewRef()?.reload();
    }

    /**
     * Remove user from course group.
     *
     * @param user User that should be removed from the currently viewed course group
     */
    removeFromGroup(user: User): void {
        if (user.login) {
            this.removeUserFromGroup()(user.login).subscribe({
                next: () => {
                    this.dialogErrorSource.next('');
                    this.tableViewRef()?.reload();
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
        this.courseManagementService.getAllUsersInCourseRole(courseId, slug).subscribe({
            next: (res) => {
                const users = res.body ?? [];
                if (users.length === 0) {
                    return;
                }
                const exportRows: ExportUserInformationRow[] = users.map(
                    (user: User): ExportUserInformationRow => ({
                        [NAME_KEY]: user.name?.trim() ?? '',
                        [USERNAME_KEY]: user.login?.trim() ?? '',
                        [EMAIL_KEY]: user.email?.trim() ?? '',
                        [REGISTRATION_NUMBER_KEY]: user.visibleRegistrationNumber?.trim() ?? '',
                    }),
                );
                exportUserInformationAsCsv(exportRows, [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY], this.exportFileName());
            },
        });
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
