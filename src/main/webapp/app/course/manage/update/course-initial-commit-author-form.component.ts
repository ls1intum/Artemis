import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { TumAetUiFormFieldComponent, TumAetUiInputDirective, TumAetUiRadioButtonComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { InitialCommitAuthor, InitialCommitAuthorMode } from 'app/course/shared/entities/course-configuration.model';

export const INITIAL_COMMIT_AUTHOR_NAME_MAX_LENGTH = 100;
export const INITIAL_COMMIT_AUTHOR_EMAIL_MAX_LENGTH = 254;

/** A name without angle brackets or control characters, which could not be told apart from the email of a git commit header. */
const NAME_PATTERN = /^[^<>\p{Cc}\p{Zl}\p{Zp}]+$/u;
/** One address without whitespace, angle brackets or a second at sign, and with a dot in the domain. */
const EMAIL_PATTERN = /^[^\s<>@\p{Cc}]+@[^\s<>@\p{Cc}]+\.[^\s<>@\p{Cc}]+$/u;

/** Like the server, the surrounding whitespace is not part of the value, so a field of spaces counts as empty. */
function strippedPattern(pattern: RegExp, error: string): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
        const value = ((control.value as string | null) ?? '').trim();
        return value === '' || pattern.test(value) ? null : { [error]: true };
    };
}

function notBlank(control: AbstractControl): ValidationErrors | null {
    return ((control.value as string | null) ?? '').trim() === '' ? { required: true } : null;
}

export type InitialCommitAuthorFormGroup = FormGroup<{
    mode: FormControl<InitialCommitAuthorMode>;
    name: FormControl<string>;
    email: FormControl<string>;
}>;

/**
 * Builds the form group of the setting. The name and the email address belong to the custom mode only, so the other modes disable them: a disabled control is not validated
 * and the course can be saved without an identity it does not use.
 *
 * @param setting the stored setting of the course, if there is one
 */
export function createInitialCommitAuthorFormGroup(setting?: InitialCommitAuthor): InitialCommitAuthorFormGroup {
    const mode = new FormControl<InitialCommitAuthorMode>(setting?.mode ?? InitialCommitAuthorMode.EXERCISE_CREATOR, { nonNullable: true });
    const name = new FormControl(setting?.name ?? '', {
        nonNullable: true,
        validators: [notBlank, Validators.maxLength(INITIAL_COMMIT_AUTHOR_NAME_MAX_LENGTH), strippedPattern(NAME_PATTERN, 'pattern')],
    });
    const email = new FormControl(setting?.email ?? '', {
        nonNullable: true,
        validators: [notBlank, Validators.maxLength(INITIAL_COMMIT_AUTHOR_EMAIL_MAX_LENGTH), strippedPattern(EMAIL_PATTERN, 'email')],
    });
    const syncIdentityControls = (value: InitialCommitAuthorMode) => {
        const custom = value === InitialCommitAuthorMode.CUSTOM;
        for (const control of [name, email]) {
            if (custom) {
                control.enable({ emitEvent: false });
            } else {
                control.disable({ emitEvent: false });
            }
        }
    };
    syncIdentityControls(mode.value);
    mode.valueChanges.subscribe(syncIdentityControls);
    return new FormGroup({ mode, name, email });
}

/** The course setting that says who authors the first commit of the repositories of new programming exercises. */
@Component({
    selector: 'jhi-course-initial-commit-author-form',
    imports: [ReactiveFormsModule, TumAetUiFormFieldComponent, TumAetUiInputDirective, TumAetUiRadioButtonComponent, ArtemisTranslatePipe, TranslateDirective],
    templateUrl: './course-initial-commit-author-form.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseInitialCommitAuthorFormComponent {
    protected readonly InitialCommitAuthorMode = InitialCommitAuthorMode;
    protected readonly nameMaxLength = INITIAL_COMMIT_AUTHOR_NAME_MAX_LENGTH;
    protected readonly emailMaxLength = INITIAL_COMMIT_AUTHOR_EMAIL_MAX_LENGTH;

    readonly group = input.required<InitialCommitAuthorFormGroup>();

    /** Read from the control on every check: a click on a radio button marks this component for checking, as it does for the other fields of the form. */
    protected isCustom(): boolean {
        return this.group().controls.mode.value === InitialCommitAuthorMode.CUSTOM;
    }

    protected nameError(): 'required' | 'tooLong' | 'invalid' | undefined {
        const errors = this.group().controls.name.errors;
        return errors ? (errors['required'] ? 'required' : errors['maxlength'] ? 'tooLong' : 'invalid') : undefined;
    }

    protected emailError(): 'required' | 'tooLong' | 'invalid' | undefined {
        const errors = this.group().controls.email.errors;
        return errors ? (errors['required'] ? 'required' : errors['maxlength'] ? 'tooLong' : 'invalid') : undefined;
    }
}
