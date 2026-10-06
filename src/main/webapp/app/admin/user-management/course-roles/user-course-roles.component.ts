import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { faGraduationCap } from '@fortawesome/free-solid-svg-icons';
import {
    TumAetUiButtonComponent,
    TumAetUiCardComponent,
    TumAetUiEmptyStateComponent,
    TumAetUiMessageComponent,
    TumAetUiProgressSpinnerComponent,
    TumAetUiTagComponent,
} from '@tumaet/ui-angular';
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
        TumAetUiButtonComponent,
        TumAetUiCardComponent,
        TumAetUiEmptyStateComponent,
        TumAetUiMessageComponent,
        TumAetUiProgressSpinnerComponent,
        TumAetUiTagComponent,
    ],
})
export class UserCourseRolesComponent {
    private readonly adminUserService = inject(AdminUserService);
    private readonly translateService = inject(TranslateService);

    /** The login of the user whose course roles are displayed. */
    readonly login = input.required<string>();

    protected readonly courseRolesResource = rxResource({
        params: () => this.login(),
        stream: ({ params: login }) => this.adminUserService.getCourseRoles(login),
    });

    /** Re-sorts the courses when the language changes, because the order follows the rules of the displayed language. */
    private readonly language = toSignal(this.translateService.onLangChange);

    /**
     * The course roles of the user grouped by role, most privileged role first, roles without a course omitted.
     * The server sends them in no particular order. Each group is sorted by the label that is displayed, according to the current language, then by id.
     */
    protected readonly groups = computed<CourseRoleGroup[]>(() => {
        this.language();
        const collator = new Intl.Collator(this.translateService.getCurrentLang() || undefined, { sensitivity: 'base', numeric: true });
        const courseRoles = this.courseRolesResource.hasValue() ? this.courseRolesResource.value() : [];
        return COURSE_ROLES_DESCENDING.map((role) => ({
            role,
            courses: courseRoles
                .filter((courseRole) => courseRole.role === role)
                .sort((a, b) => collator.compare(this.courseLabel(a), this.courseLabel(b)) || a.courseId - b.courseId),
        })).filter((group) => group.courses.length > 0);
    });

    protected readonly faGraduationCap = faGraduationCap;

    protected translationKey(role: CourseRoleName): string {
        return `artemisApp.userManagement.courseRoles.roles.${ROLE_TRANSLATION_KEYS[role]}`;
    }

    /** What is displayed as the name of the course: the title, else the short name, else the id. */
    protected courseLabel(courseRole: UserCourseRole): string {
        return courseRole.courseTitle || courseRole.courseShortName || String(courseRole.courseId);
    }

    /** The short name and semester shown next to the title to tell courses with the same title apart. */
    protected courseDetails(courseRole: UserCourseRole): string {
        return [courseRole.courseShortName, courseRole.courseSemester].filter((detail) => !!detail && detail !== this.courseLabel(courseRole)).join(' · ');
    }
}
