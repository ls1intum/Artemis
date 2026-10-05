import { ChangeDetectionStrategy, Component, booleanAttribute, computed, inject, input, output, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { faGraduationCap, faTrash } from '@fortawesome/free-solid-svg-icons';
import {
    TumAetUiButtonComponent,
    TumAetUiCardComponent,
    TumAetUiConfirmDialogComponent,
    TumAetUiConfirmationService,
    TumAetUiEmptyStateComponent,
    TumAetUiMessageComponent,
    TumAetUiProgressSpinnerComponent,
    TumAetUiTagComponent,
} from '@tumaet/ui-angular';
import { AdminUserService } from 'app/account/user/shared/admin-user.service';
import { COURSE_ROLES_DESCENDING, CourseRoleName, UserCourseRole, courseRolePluralTranslationKey, courseRoleTranslationKey } from 'app/account/user/shared/user-course-role.model';
import { UserCourseRoleAddComponent } from 'app/admin/user-management/course-roles/user-course-role-add.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** The courses a user holds one role in. */
interface CourseRoleGroup {
    role: CourseRoleName;
    courses: UserCourseRole[];
}

/**
 * Shows the courses in which a user holds a role, grouped by role, for administrators.
 * Every course links to its course management page. When {@link editable}, an administrator can also add a role in
 * another course and remove a role after confirming. These changes are saved at once and are independent of any form the component sits in.
 */
@Component({
    selector: 'jhi-user-course-roles',
    templateUrl: './user-course-roles.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    providers: [TumAetUiConfirmationService],
    imports: [
        RouterLink,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumAetUiButtonComponent,
        TumAetUiCardComponent,
        TumAetUiConfirmDialogComponent,
        TumAetUiEmptyStateComponent,
        TumAetUiMessageComponent,
        TumAetUiProgressSpinnerComponent,
        TumAetUiTagComponent,
        UserCourseRoleAddComponent,
    ],
})
export class UserCourseRolesComponent {
    private readonly adminUserService = inject(AdminUserService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);
    private readonly confirmationService = inject(TumAetUiConfirmationService);

    /** The login of the user whose course roles are displayed. */
    readonly login = input.required<string>();

    /** Whether roles can be added and removed. */
    readonly editable = input(false, { transform: booleanAttribute });

    /** Emits after a role was added or removed on the server, so the host can refresh what depends on it, such as the global authorities. */
    readonly courseRolesChanged = output<void>();

    protected readonly courseRolesResource = rxResource({
        params: () => this.login(),
        stream: ({ params: login }) => this.adminUserService.getCourseRoles(login),
    });

    private readonly courseRoles = computed<readonly UserCourseRole[]>(() => (this.courseRolesResource.hasValue() ? this.courseRolesResource.value() : []));

    /** The course roles of the user grouped by role, most privileged role first. Roles without a course are omitted. */
    protected readonly groups = computed<CourseRoleGroup[]>(() => {
        const courseRoles = this.courseRoles();
        return COURSE_ROLES_DESCENDING.map((role) => ({ role, courses: courseRoles.filter((courseRole) => courseRole.role === role) })).filter((group) => group.courses.length > 0);
    });

    /** Identifies the role being removed, so its button can show progress and nothing is removed twice. */
    protected readonly removing = signal<string | undefined>(undefined);

    protected readonly faGraduationCap = faGraduationCap;
    protected readonly faTrash = faTrash;

    protected translationKey(role: CourseRoleName): string {
        return courseRolePluralTranslationKey(role);
    }

    protected removeLabel(courseRole: UserCourseRole): string {
        return this.translateService.instant('artemisApp.userManagement.courseRoles.remove.label', this.removeParams(courseRole));
    }

    protected isRemoving(courseRole: UserCourseRole): boolean {
        return this.removing() === this.removalKey(courseRole);
    }

    /** Asks for confirmation, because losing a role takes the user's access to that course away. */
    protected confirmRemove(courseRole: UserCourseRole): void {
        const params = this.removeParams(courseRole);
        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.userManagement.courseRoles.remove.header'),
            message: this.translateService.instant('artemisApp.userManagement.courseRoles.remove.message', params),
            acceptLabel: this.translateService.instant('artemisApp.userManagement.courseRoles.remove.accept'),
            rejectLabel: this.translateService.instant('entity.action.cancel'),
            acceptSeverity: 'danger',
            accept: () => this.remove(courseRole, params),
        });
    }

    protected onRoleAdded(): void {
        this.courseRolesResource.reload();
        this.courseRolesChanged.emit();
    }

    private remove(courseRole: UserCourseRole, params: Record<string, unknown>): void {
        this.removing.set(this.removalKey(courseRole));
        this.adminUserService.removeCourseRole(this.login(), courseRole.courseId, courseRole.role).subscribe({
            next: () => {
                this.removing.set(undefined);
                this.alertService.success('artemisApp.userManagement.courseRoles.remove.success', params);
                this.courseRolesResource.reload();
                this.courseRolesChanged.emit();
            },
            error: () => this.removing.set(undefined),
        });
    }

    private removalKey(courseRole: UserCourseRole): string {
        return `${courseRole.courseId}-${courseRole.role}`;
    }

    private removeParams(courseRole: UserCourseRole): Record<string, unknown> {
        return {
            login: this.login(),
            course: courseRole.courseTitle ?? courseRole.courseShortName,
            role: this.translateService.instant(courseRoleTranslationKey(courseRole.role)),
        };
    }
}
