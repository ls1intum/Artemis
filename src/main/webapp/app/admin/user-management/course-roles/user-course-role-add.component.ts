import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, booleanAttribute, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiAutoCompleteComponent, TumAetUiAutoCompleteSearchEvent, TumAetUiButtonComponent, TumAetUiFormFieldComponent, TumAetUiSelectComponent } from '@tumaet/ui-angular';
import { AdminUserService } from 'app/account/user/shared/admin-user.service';
import {
    COURSE_ROLES_DESCENDING,
    CourseForRoleAssignment,
    CourseRoleName,
    UserCourseRole,
    courseDisplayName,
    courseRoleTranslationKey,
} from 'app/account/user/shared/user-course-role.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** Number of courses suggested while an administrator types. */
const COURSE_SUGGESTION_COUNT = 10;

/** A course in the suggestions, labelled so that courses with the same title can be told apart. */
interface CourseOption {
    course: CourseForRoleAssignment;
    label: string;
}

/** Short name first, because it is what administrators know a course by: "ALGO25 · Algorithms (WS25)". */
const toCourseOption = (course: CourseForRoleAssignment): CourseOption => {
    const name = [course.shortName, course.title].filter((part) => !!part).join(' · ') || String(course.id);
    return { course, label: course.semester ? `${name} (${course.semester})` : name };
};

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
    private readonly destroyRef = inject(DestroyRef);
    private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

    /** The login of the user who receives the role. */
    readonly login = input.required<string>();

    /** The roles the user already holds, to tell the administrator that a chosen combination exists. */
    readonly existingRoles = input<readonly UserCourseRole[]>([]);

    /** Blocks adding, for example while another change of the course roles is running. */
    readonly disabled = input(false, { transform: booleanAttribute });

    /** Emits after the role was added on the server. */
    readonly added = output<void>();

    /** Emits when a request to add a role failed. The role may have been added although the response did not arrive, so the host has to look at the server again. */
    readonly failed = output<void>();

    /** Emits `true` when a request to add a role starts and `false` when it ends, so the host can hold back actions that must not overlap with it. */
    readonly changing = output<boolean>();

    protected readonly faPlus = faPlus;

    protected readonly courseSuggestions = signal<CourseOption[]>([]);

    /** Search texts in the order they were typed. Only the latest one is answered, an earlier search still in flight is cancelled. */
    private readonly searchTexts = new Subject<string>();

    /** Either a chosen course or the text typed into the course field. */
    protected readonly courseInput = signal<CourseOption | string | undefined>(undefined);

    private destroyed = false;

    protected readonly role = signal<CourseRoleName>('STUDENT');

    protected readonly isSaving = signal(false);

    /** Re-evaluates the translated role labels when the language changes. */
    private readonly language = toSignal(this.translateService.onLangChange);

    protected readonly roleOptions = computed(() => {
        this.language();
        return [...COURSE_ROLES_DESCENDING].reverse().map((role) => ({ value: role, label: this.translateService.instant(courseRoleTranslationKey(role)) }));
    });

    /** The chosen course. Typing into the field again discards it, because the text no longer names a course. */
    private readonly course = computed(() => {
        const value = this.courseInput();
        return value && typeof value === 'object' ? value.course : undefined;
    });

    protected readonly alreadyHasRole = computed(() => {
        const course = this.course();
        return course !== undefined && this.existingRoles().some((existing) => existing.courseId === course.id && existing.role === this.role());
    });

    protected readonly canAdd = computed(() => this.course() !== undefined && !this.alreadyHasRole() && !this.isSaving() && !this.disabled());

    constructor() {
        this.destroyRef.onDestroy(() => (this.destroyed = true));
        this.searchTexts
            .pipe(
                switchMap((searchText) => this.adminUserService.searchCoursesForRoleAssignment(searchText, COURSE_SUGGESTION_COUNT).pipe(catchError(() => of([])))),
                takeUntilDestroyed(),
            )
            .subscribe((courses) => this.courseSuggestions.set(courses.map(toCourseOption)));
    }

    /** Moves the focus to the course field, so the next role can be added at once. */
    focusCourse(): void {
        this.host.nativeElement.querySelector<HTMLInputElement>('[data-testid="user-course-role-add-course"] input')?.focus();
    }

    protected searchCourses(event: TumAetUiAutoCompleteSearchEvent): void {
        this.searchTexts.next(event.query);
    }

    protected onCourseChange(value: unknown): void {
        this.courseInput.set(value as CourseOption | string | undefined);
    }

    protected onRoleChange(value: unknown): void {
        this.role.set(value as CourseRoleName);
    }

    protected add(): void {
        const course = this.course();
        if (!course || !this.canAdd()) {
            return;
        }
        // The selection can change while the request is pending, so the request and its message use what was submitted.
        const role = this.role();
        this.isSaving.set(true);
        this.changing.emit(true);
        this.adminUserService.addCourseRole(this.login(), course.id, role).subscribe({
            next: () => {
                this.isSaving.set(false);
                this.courseInput.set(undefined);
                this.alertService.success('artemisApp.userManagement.courseRoles.add.success', {
                    login: this.login(),
                    course: courseDisplayName(course),
                    role: this.translateService.instant(courseRoleTranslationKey(role)),
                });
                this.emitIfAlive(() => this.added.emit());
                this.emitIfAlive(() => this.changing.emit(false));
            },
            error: () => {
                this.isSaving.set(false);
                this.emitIfAlive(() => this.failed.emit());
                this.emitIfAlive(() => this.changing.emit(false));
            },
        });
    }

    /** A response can arrive after the component was destroyed, and an output must not emit then. */
    private emitIfAlive(emit: () => void): void {
        if (!this.destroyed) {
            emit();
        }
    }
}
