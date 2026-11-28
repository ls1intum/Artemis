import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { map, startWith, switchMap } from 'rxjs';
import { Component, input } from '@angular/core';
import { FormControl } from '@angular/forms';
import { PASSWORD_MAX_BYTES, PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from 'app/app.constants';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

/** The same validation messages are used when setting and changing a password. */
@Component({
    selector: 'jhi-password-validation-errors',
    imports: [TranslateDirective],
    host: { class: 'contents' },
    template: `
        @if (errors()?.required) {
            <small [jhiTranslate]="'global.messages.validate.' + field() + '.required'"></small>
        }
        @if (errors()?.minlength) {
            <small [jhiTranslate]="'global.messages.validate.' + field() + '.minlength'" [translateValues]="{ min: minLength }"></small>
        }
        @if (errors()?.maxlength) {
            <small [jhiTranslate]="'global.messages.validate.' + field() + '.maxlength'" [translateValues]="{ max: maxLength }"></small>
        }
        @if (field() === 'newpassword' && errors()?.maxbytes) {
            <small jhiTranslate="global.messages.validate.newpassword.maxbytes" [translateValues]="{ max: maxBytes }"></small>
        }
    `,
})
export class PasswordValidationErrorsComponent {
    readonly control = input.required<FormControl<string>>();
    readonly field = input.required<'newpassword' | 'confirmpassword'>();
    protected readonly errors = toSignal(
        toObservable(this.control).pipe(
            switchMap((control) =>
                control.statusChanges.pipe(
                    startWith(control.status),
                    map(() => control.errors ?? undefined),
                ),
            ),
        ),
    );
    protected readonly minLength = PASSWORD_MIN_LENGTH;
    protected readonly maxLength = PASSWORD_MAX_LENGTH;
    protected readonly maxBytes = PASSWORD_MAX_BYTES;
}
