import { AfterViewInit, Component, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PasswordStrengthBarComponent } from 'app/account/password/password-strength-bar.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { PasswordResetFinishService } from './password-reset-finish.service';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from 'app/app.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-password-reset-finish',
    templateUrl: './password-reset-finish.component.html',
    standalone: true,
    imports: [TranslateDirective, RouterLink, FormsModule, ReactiveFormsModule, PasswordStrengthBarComponent, ArtemisSharedCommonModule, ArtemisSharedModule],
})
export class PasswordResetFinishComponent implements OnInit, AfterViewInit {
    private passwordResetFinishService = inject(PasswordResetFinishService);
    private route = inject(ActivatedRoute);
    private fb = inject(FormBuilder);

    @ViewChild('newPassword', { static: false })
    newPassword?: ElementRef;

    readonly PASSWORD_MIN_LENGTH = PASSWORD_MIN_LENGTH;
    readonly PASSWORD_MAX_LENGTH = PASSWORD_MAX_LENGTH;

    initialized = false;
    doNotMatch = false;
    error = false;
    success = false;
    key = '';

    passwordForm: FormGroup;

    ngOnInit() {
        this.route.queryParams.subscribe((params) => {
            if (params['key']) {
                this.key = params['key'];
            }
            this.initialized = true;
        });
        this.initializeForm();
    }

    private initializeForm() {
        if (this.passwordForm) {
            return;
        }
        this.passwordForm = this.fb.nonNullable.group({
            newPassword: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)]],
            confirmPassword: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)]],
        });
    }

    ngAfterViewInit(): void {
        if (this.newPassword) {
            this.newPassword.nativeElement.focus();
        }
    }

    finishReset(): void {
        this.doNotMatch = false;
        this.error = false;

        const newPassword = this.passwordForm.get(['newPassword'])!.value;
        const confirmPassword = this.passwordForm.get(['confirmPassword'])!.value;

        if (newPassword !== confirmPassword) {
            this.doNotMatch = true;
        } else {
            this.passwordResetFinishService.save(this.key, newPassword).subscribe({
                next: () => (this.success = true),
                error: () => (this.error = true),
            });
        }
    }
}
