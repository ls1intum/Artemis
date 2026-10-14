import {
    ChangeDetectionStrategy,
    Component,
    DestroyRef,
    ElementRef,
    Injector,
    afterNextRender,
    booleanAttribute,
    computed,
    effect,
    inject,
    input,
    output,
    signal,
    untracked,
    viewChild,
} from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
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
import {
    COURSE_ROLES_DESCENDING,
    CourseRoleName,
    UserCourseRole,
    courseDisplayName,
    courseRolePluralTranslationKey,
    courseRoleTranslationKey,
} from 'app/account/user/shared/user-course-role.model';
import { UserCourseRoleAddComponent } from 'app/admin/user-management/course-roles/user-course-role-add.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** The courses a user holds one role in. */
interface CourseRoleGroup {
    role: CourseRoleName;
    courses: UserCourseRole[];
}

/** Where the focus goes once a change of the course roles is reflected in the list. */
type FocusTarget = { kind: 'add' } | { kind: 'remove'; index: number };

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
    private readonly destroyRef = inject(DestroyRef);
    private readonly injector = inject(Injector);
    private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

    /** The login of the user whose course roles are displayed. */
    readonly login = input.required<string>();

    /** Whether roles can be added and removed. */
    readonly editable = input(false, { transform: booleanAttribute });

    /** Blocks every change from outside, for example while the form around the component is being saved. */
    readonly disabled = input(false, { transform: booleanAttribute });

    /** Emits after a role was added or removed, or a change failed, so the host can refresh what depends on it, such as the global authorities. */
    readonly courseRolesChanged = output<void>();

    /** Emits `true` when a request that adds or removes a role starts and `false` when it ends, so the host can hold back actions that must not overlap with it. */
    readonly changeInProgress = output<boolean>();

    protected readonly courseRolesResource = rxResource({
        params: () => this.login(),
        stream: ({ params: login }) => this.adminUserService.getCourseRoles(login),
    });

    protected readonly courseRoles = computed<readonly UserCourseRole[]>(() => (this.courseRolesResource.hasValue() ? this.courseRolesResource.value() : []));

    /** Re-sorts the courses when the language changes, because the order follows the rules of the displayed language. */
    private readonly language = toSignal(this.translateService.onLangChange);

    /**
     * The course roles of the user grouped by role, most privileged role first, roles without a course omitted.
     * The server sends them in no particular order. Each group is sorted by the label that is displayed, according to the current language, then by id.
     */
    protected readonly groups = computed<CourseRoleGroup[]>(() => {
        this.language();
        const collator = new Intl.Collator(this.translateService.getCurrentLang() || undefined, { sensitivity: 'base', numeric: true });
        const courseRoles = this.courseRoles();
        return COURSE_ROLES_DESCENDING.map((role) => ({
            role,
            courses: courseRoles
                .filter((courseRole) => courseRole.role === role)
                .sort((a, b) => collator.compare(this.courseLabel(a), this.courseLabel(b)) || a.courseId - b.courseId),
        })).filter((group) => group.courses.length > 0);
    });

    /** The spinner shows while the roles load for the first time, or again after a failure. A reload of a visible list keeps showing the list. */
    protected readonly showSpinner = computed(() => {
        const status = this.courseRolesResource.status();
        return status === 'loading' || (status === 'reloading' && !this.courseRolesResource.hasValue());
    });

    /** Identifies the role being removed, so its button can show progress. It stays until the list reflects the removal. */
    protected readonly removing = signal<string | undefined>(undefined);

    private readonly adding = signal(false);

    /** Whether the list was asked to load again after a change and has not answered yet. */
    private readonly awaitingReload = signal(false);

    private reloadStarted = false;

    private focusTarget?: FocusTarget;

    private destroyed = false;

    private readonly addForm = viewChild(UserCourseRoleAddComponent);

    /** Changes run one at a time, because each changes the global authorities of the user and the host has to refresh them in between. */
    protected readonly busy = computed(() => this.disabled() || this.removing() !== undefined || this.adding() || this.awaitingReload());

    protected readonly faGraduationCap = faGraduationCap;
    protected readonly faTrash = faTrash;

    constructor() {
        this.destroyRef.onDestroy(() => (this.destroyed = true));
        // Ends the wait for the reload that a change started, once the list answered, and then places the focus.
        effect(() => {
            const status = this.courseRolesResource.status();
            if (!this.awaitingReload()) {
                return;
            }
            if (status === 'loading' || status === 'reloading') {
                this.reloadStarted = true;
            } else if (this.reloadStarted) {
                untracked(() => this.finishReload());
            }
        });
    }

    protected translationKey(role: CourseRoleName): string {
        return courseRolePluralTranslationKey(role);
    }

    /** What is displayed as the name of the course: the title, else the short name, else the id. */
    protected courseLabel(courseRole: UserCourseRole): string {
        return courseRole.courseTitle || courseRole.courseShortName || String(courseRole.courseId);
    }

    /** The short name and semester shown next to the title to tell courses with the same title apart. */
    protected courseDetails(courseRole: UserCourseRole): string {
        return [courseRole.courseShortName, courseRole.courseSemester].filter((detail) => !!detail && detail !== this.courseLabel(courseRole)).join(' · ');
    }

    protected removeLabel(courseRole: UserCourseRole): string {
        return this.translateService.instant('artemisApp.userManagement.courseRoles.remove.label', this.removeParams(courseRole));
    }

    protected isRemoving(courseRole: UserCourseRole): boolean {
        return this.removing() === this.removalKey(courseRole);
    }

    /** Asks for confirmation, because losing a role takes the user's access to that course away. */
    protected confirmRemove(courseRole: UserCourseRole): void {
        if (this.busy()) {
            return;
        }
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

    protected onAddInProgress(inProgress: boolean): void {
        this.adding.set(inProgress);
        this.emitIfAlive(() => this.changeInProgress.emit(inProgress));
    }

    protected onRoleAdded(): void {
        this.focusTarget = { kind: 'add' };
        this.reloadAfterChange();
    }

    /** A failed request may still have changed the server, for example when only the response got lost, so the list and the host look at the server again. */
    protected onChangeFailed(): void {
        this.focusTarget = undefined;
        this.reloadAfterChange();
    }

    private remove(courseRole: UserCourseRole, params: Record<string, unknown>): void {
        if (this.busy()) {
            return;
        }
        // The remove buttons appear in the order of the groups, so this is the position of the button that is about to disappear.
        const index = this.groups()
            .flatMap((group) => group.courses)
            .findIndex((course) => this.removalKey(course) === this.removalKey(courseRole));
        this.removing.set(this.removalKey(courseRole));
        this.emitIfAlive(() => this.changeInProgress.emit(true));
        this.adminUserService.removeCourseRole(this.login(), courseRole.courseId, courseRole.role).subscribe({
            next: () => {
                this.alertService.success('artemisApp.userManagement.courseRoles.remove.success', params);
                this.focusTarget = { kind: 'remove', index: Math.max(index, 0) };
                this.reloadAfterChange();
                this.emitIfAlive(() => this.changeInProgress.emit(false));
            },
            error: () => {
                this.removing.set(undefined);
                this.onChangeFailed();
                this.emitIfAlive(() => this.changeInProgress.emit(false));
            },
        });
    }

    /** Loads the list again and tells the host. The entry that was just removed stays busy until the list answered. */
    private reloadAfterChange(): void {
        if (this.destroyed) {
            return;
        }
        this.reloadStarted = false;
        this.awaitingReload.set(true);
        if (!this.courseRolesResource.reload()) {
            this.finishReload();
        }
        this.emitIfAlive(() => this.courseRolesChanged.emit());
    }

    private finishReload(): void {
        if (this.destroyed) {
            return;
        }
        this.awaitingReload.set(false);
        this.removing.set(undefined);
        const target = this.focusTarget;
        this.focusTarget = undefined;
        if (target) {
            afterNextRender(() => this.moveFocus(target), { injector: this.injector });
        }
    }

    private moveFocus(target: FocusTarget): void {
        const buttons = this.removeButtons();
        if (target.kind === 'remove' && buttons.length > 0) {
            buttons[Math.min(target.index, buttons.length - 1)].focus();
        } else {
            this.addForm()?.focusCourse();
        }
    }

    private removeButtons(): HTMLButtonElement[] {
        return Array.from(this.host.nativeElement.querySelectorAll<HTMLButtonElement>('[data-testid="user-course-roles-remove"] button'));
    }

    /** A response can arrive after the component was destroyed, and an output must not emit then. */
    private emitIfAlive(emit: () => void): void {
        if (!this.destroyed) {
            emit();
        }
    }

    private removalKey(courseRole: UserCourseRole): string {
        return `${courseRole.courseId}-${courseRole.role}`;
    }

    private removeParams(courseRole: UserCourseRole): Record<string, unknown> {
        return {
            login: this.login(),
            course: courseDisplayName({ id: courseRole.courseId, title: courseRole.courseTitle, shortName: courseRole.courseShortName }),
            role: this.translateService.instant(courseRoleTranslationKey(courseRole.role)),
        };
    }
}
