import { AfterViewInit, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PasswordStrengthBarComponent } from 'app/core/account/password/password-strength-bar.component';

import { PasswordResetFinishService } from './password-reset-finish.service';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from 'app/app.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

interface PasswordResetForm {
    newPassword: FormControl<string>;
    confirmPassword: FormControl<string>;
}

@Component({
    selector: 'jhi-password-reset-finish',
    templateUrl: './password-reset-finish.component.html',
    imports: [TranslateDirective, RouterLink, FormsModule, ReactiveFormsModule, PasswordStrengthBarComponent, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordResetFinishComponent implements OnInit, AfterViewInit {
    private readonly passwordResetFinishService = inject(PasswordResetFinishService);
    private readonly route = inject(ActivatedRoute);
    private readonly destroyRef = inject(DestroyRef);

    readonly newPassword = viewChild<ElementRef>('newPassword');

    readonly PASSWORD_MIN_LENGTH = PASSWORD_MIN_LENGTH;
    readonly PASSWORD_MAX_LENGTH = PASSWORD_MAX_LENGTH;

    readonly initialized = signal(false);
    readonly doNotMatch = signal(false);
    readonly error = signal(false);
    readonly success = signal(false);
    readonly key = signal('');

    readonly passwordForm = new FormGroup<PasswordResetForm>({
        newPassword: new FormControl('', {
            nonNullable: true,
            validators: [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)],
        }),
        confirmPassword: new FormControl('', {
            nonNullable: true,
            validators: [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)],
        }),
    });

    ngOnInit() {
        this.route.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            if (params['key']) {
                this.key.set(params['key']);
            }
            this.initialized.set(true);
        });
    }

    ngAfterViewInit(): void {
        this.newPassword()?.nativeElement.focus();
    }

    finishReset(): void {
        this.doNotMatch.set(false);
        this.error.set(false);

        const { newPassword, confirmPassword } = this.passwordForm.controls;

        if (newPassword.value !== confirmPassword.value) {
            this.doNotMatch.set(true);
        } else {
            this.passwordResetFinishService.save(this.key(), newPassword.value).subscribe({
                next: () => this.success.set(true),
                error: () => this.error.set(true),
            });
        }
    }
}
