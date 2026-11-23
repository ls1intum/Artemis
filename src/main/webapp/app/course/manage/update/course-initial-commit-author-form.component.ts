import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { TumAetUiFormFieldComponent, TumAetUiInputDirective, TumAetUiRadioButtonComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { InitialCommitAuthor, InitialCommitAuthorMode } from 'app/course/shared/entities/course-configuration.model';

export const INITIAL_COMMIT_AUTHOR_NAME_MAX_LENGTH = 100;
export const INITIAL_COMMIT_AUTHOR_EMAIL_MAX_LENGTH = 254;

/**
 * A name without angle brackets, control characters (Cc), format characters such as the right-to-left override (Cf) and line or paragraph separators (Zl, Zp): none of them
 * can end the name or the email in a git commit header or change how it reads. The server uses the same character classes.
 */
const NAME_PATTERN = /^[^<>\p{Cc}\p{Cf}\p{Zl}\p{Zp}]+$/u;
/** A character that no part of an address may contain: an angle bracket, a control (Cc) or format (Cf) character, or any space or separator (Z). */
const FORBIDDEN_ADDRESS_CHARACTER = /[<>\p{Cc}\p{Cf}\p{Z}]/u;

/**
 * Whether the text is one address: a local part, exactly one at sign and a domain with a dot that is neither its first nor its last character, without a forbidden
 * character. It is checked by position, not by a pattern with a quantifier on each side of the dot, which backtracks heavily on a long text. The server checks the same.
 */
function isValidEmail(address: string): boolean {
    const at = address.indexOf('@');
    if (at <= 0 || at !== address.lastIndexOf('@') || FORBIDDEN_ADDRESS_CHARACTER.test(address)) {
        return false;
    }
    return address.indexOf('.', at + 2) !== -1 && address.indexOf('.', at + 2) < address.length - 1;
}

/** Like the server, the surrounding whitespace is not part of the value, so a field of spaces counts as empty. */
function strippedValidator(isValid: (value: string) => boolean, error: string): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
        const value = ((control.value as string | null) ?? '').trim();
        return value === '' || isValid(value) ? null : { [error]: true };
    };
}

function notBlank(control: AbstractControl): ValidationErrors | null {
    return ((control.value as string | null) ?? '').trim() === '' ? { required: true } : null;
}

type FieldError = 'required' | 'tooLong' | 'invalid';

function fieldErrorOf(control: AbstractControl): FieldError | undefined {
    const errors = control.errors;
    if (!errors) {
        return undefined;
    }
    if (errors['required']) {
        return 'required';
    }
    return errors['maxlength'] ? 'tooLong' : 'invalid';
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
        validators: [notBlank, Validators.maxLength(INITIAL_COMMIT_AUTHOR_NAME_MAX_LENGTH), strippedValidator((value) => NAME_PATTERN.test(value), 'pattern')],
    });
    const email = new FormControl(setting?.email ?? '', {
        nonNullable: true,
        validators: [notBlank, Validators.maxLength(INITIAL_COMMIT_AUTHOR_EMAIL_MAX_LENGTH), strippedValidator(isValidEmail, 'email')],
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

    protected nameError(): FieldError | undefined {
        return fieldErrorOf(this.group().controls.name);
    }

    protected emailError(): FieldError | undefined {
        return fieldErrorOf(this.group().controls.email);
    }
}
