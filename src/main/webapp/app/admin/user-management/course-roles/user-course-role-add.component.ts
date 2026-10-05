import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiAutoCompleteComponent, TumAetUiAutoCompleteSearchEvent, TumAetUiButtonComponent, TumAetUiFormFieldComponent, TumAetUiSelectComponent } from '@tumaet/ui-angular';
import { AdminUserService } from 'app/account/user/shared/admin-user.service';
import { COURSE_ROLES_DESCENDING, CourseForRoleAssignment, CourseRoleName, UserCourseRole, courseRoleTranslationKey } from 'app/account/user/shared/user-course-role.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** Number of courses suggested while an administrator types. */
const COURSE_SUGGESTION_COUNT = 10;

/**
 * Lets an administrator give a user a role in a course: pick a course by title or short name, pick a role and confirm.
 * A role the user already holds in the chosen course cannot be added again.
 */
@Component({
    selector: 'jhi-user-course-role-add',
    templateUrl: './user-course-role-add.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FormsModule, TranslateDirective, ArtemisTranslatePipe, TumAetUiAutoCompleteComponent, TumAetUiButtonComponent, TumAetUiFormFieldComponent, TumAetUiSelectComponent],
})
export class UserCourseRoleAddComponent {
    private readonly adminUserService = inject(AdminUserService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);

    /** The login of the user who receives the role. */
    readonly login = input.required<string>();

    /** The roles the user already holds, to tell the administrator that a chosen combination exists. */
    readonly existingRoles = input<readonly UserCourseRole[]>([]);

    /** Emits after the role was added on the server. */
    readonly added = output<void>();

    protected readonly faPlus = faPlus;

    protected readonly courseSuggestions = signal<CourseForRoleAssignment[]>([]);

    /** Either a chosen course or the text typed into the course field. */
    private readonly courseInput = signal<CourseForRoleAssignment | string | undefined>(undefined);

    protected readonly role = signal<CourseRoleName>('STUDENT');

    protected readonly isSaving = signal(false);

    /** Re-evaluates the translated role labels when the language changes. */
    private readonly language = toSignal(this.translateService.onLangChange);

    protected readonly roleOptions = computed(() => {
        this.language();
        return [...COURSE_ROLES_DESCENDING].reverse().map((role) => ({ value: role, label: this.translateService.instant(courseRoleTranslationKey(role)) }));
    });

    /** The chosen course. Typing into the field again discards it, because the text no longer names a course. */
    protected readonly course = computed(() => {
        const value = this.courseInput();
        return value && typeof value === 'object' ? value : undefined;
    });

    protected readonly alreadyHasRole = computed(() => {
        const course = this.course();
        return course !== undefined && this.existingRoles().some((existing) => existing.courseId === course.id && existing.role === this.role());
    });

    protected readonly canAdd = computed(() => this.course() !== undefined && !this.alreadyHasRole() && !this.isSaving());

    protected searchCourses(event: TumAetUiAutoCompleteSearchEvent): void {
        this.adminUserService.searchCoursesForRoleAssignment(event.query, COURSE_SUGGESTION_COUNT).subscribe((courses) => this.courseSuggestions.set(courses));
    }

    protected onCourseChange(value: unknown): void {
        this.courseInput.set(value as CourseForRoleAssignment | string | undefined);
    }

    protected onRoleChange(value: unknown): void {
        this.role.set(value as CourseRoleName);
    }

    protected add(): void {
        const course = this.course();
        if (!course || !this.canAdd()) {
            return;
        }
        this.isSaving.set(true);
        this.adminUserService.addCourseRole(this.login(), course.id, this.role()).subscribe({
            next: () => {
                this.isSaving.set(false);
                this.courseInput.set(undefined);
                this.alertService.success('artemisApp.userManagement.courseRoles.add.success', {
                    login: this.login(),
                    course: course.title ?? course.shortName,
                    role: this.translateService.instant(courseRoleTranslationKey(this.role())),
                });
                this.added.emit();
            },
            error: () => this.isSaving.set(false),
        });
    }
}
