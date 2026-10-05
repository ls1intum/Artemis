import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { faGraduationCap } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiCardComponent, TumAetUiEmptyStateComponent, TumAetUiMessageComponent, TumAetUiProgressSpinnerComponent, TumAetUiTagComponent } from '@tumaet/ui-angular';
import { AdminUserService } from 'app/account/user/shared/admin-user.service';
import { COURSE_ROLES_DESCENDING, CourseRoleName, UserCourseRole } from 'app/account/user/shared/user-course-role.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** The courses a user holds one role in. */
interface CourseRoleGroup {
    role: CourseRoleName;
    courses: UserCourseRole[];
}

/** Translation key suffix of every role, below `artemisApp.userManagement.courseRoles.roles`. */
const ROLE_TRANSLATION_KEYS: Record<CourseRoleName, string> = {
    INSTRUCTOR: 'instructor',
    EDITOR: 'editor',
    TEACHING_ASSISTANT: 'tutor',
    STUDENT: 'student',
};

/**
 * Shows the courses in which a user holds a role, grouped by role, for administrators.
 * Every course links to its course management page.
 */
@Component({
    selector: 'jhi-user-course-roles',
    templateUrl: './user-course-roles.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        RouterLink,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumAetUiCardComponent,
        TumAetUiEmptyStateComponent,
        TumAetUiMessageComponent,
        TumAetUiProgressSpinnerComponent,
        TumAetUiTagComponent,
    ],
})
export class UserCourseRolesComponent {
    private readonly adminUserService = inject(AdminUserService);

    /** The login of the user whose course roles are displayed. */
    readonly login = input.required<string>();

    protected readonly courseRolesResource = rxResource({
        params: () => this.login(),
        stream: ({ params: login }) => this.adminUserService.getCourseRoles(login),
    });

    /** The course roles of the user grouped by role, most privileged role first. Roles without a course are omitted. */
    protected readonly groups = computed<CourseRoleGroup[]>(() => {
        const courseRoles = this.courseRolesResource.hasValue() ? this.courseRolesResource.value() : [];
        return COURSE_ROLES_DESCENDING.map((role) => ({ role, courses: courseRoles.filter((courseRole) => courseRole.role === role) })).filter((group) => group.courses.length > 0);
    });

    protected readonly faGraduationCap = faGraduationCap;

    protected translationKey(role: CourseRoleName): string {
        return `artemisApp.userManagement.courseRoles.roles.${ROLE_TRANSLATION_KEYS[role]}`;
    }
}
