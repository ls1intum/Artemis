import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { SystemNotification, SystemNotificationType } from 'app/core/shared/entities/system-notification.model';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AdminSystemNotificationService } from 'app/core/notification/system-notification/admin-system-notification.service';

/**
 * Form structure for system notification editing.
 * Note: FormControl values can be null when initialized with undefined.
 */
interface SystemNotificationForm {
    id: FormControl<number | null | undefined>;
    title: FormControl<string | null | undefined>;
    text: FormControl<string | null | undefined>;
    type: FormControl<SystemNotificationType | null | undefined>;
    notificationDate: FormControl<dayjs.Dayjs | null | undefined>;
    expireDate: FormControl<dayjs.Dayjs | null | undefined>;
}

/**
 * Component for creating and updating system notifications.
 * Provides a form with validation for notification properties.
 */
@Component({
    selector: 'jhi-system-notification-management-update',
    templateUrl: './system-notification-management-update.component.html',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, FormDateTimePickerComponent, FaIconComponent, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SystemNotificationManagementUpdateComponent implements OnInit {
    private readonly systemNotificationService = inject(AdminSystemNotificationService);
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);

    /** The notification being edited */
    private notification = new SystemNotification();

    /** Whether the form is currently being submitted */
    readonly isSaving = signal(false);

    /** Available notification types for the dropdown */
    protected readonly systemNotificationTypes = [
        { name: 'INFO', value: SystemNotificationType.INFO },
        { name: 'WARNING', value: SystemNotificationType.WARNING },
    ] as const;

    /** The reactive form for editing notification properties */
    readonly form = new FormGroup<SystemNotificationForm>(
        {
            id: new FormControl<number | undefined>(undefined),
            title: new FormControl<string | undefined>(undefined, [Validators.required, Validators.minLength(1), Validators.maxLength(50)]),
            text: new FormControl<string | undefined>(undefined),
            type: new FormControl<SystemNotificationType | undefined>(undefined, [Validators.required]),
            notificationDate: new FormControl<dayjs.Dayjs | undefined>(undefined, [Validators.required]),
            expireDate: new FormControl<dayjs.Dayjs | undefined>(undefined, [Validators.required]),
        },
        { validators: this.validateDates.bind(this) },
    );

    /** Icons */
    protected readonly faSave = faSave;
    protected readonly faBan = faBan;

    /**
     * Loads notification from route data and initializes the form.
     */
    ngOnInit(): void {
        this.route.parent!.data.subscribe(({ notification }) => {
            if (notification) {
                this.notification = notification.body ?? notification;
                this.form.patchValue({
                    id: this.notification.id,
                    title: this.notification.title,
                    text: this.notification.text,
                    type: this.notification.type,
                    notificationDate: this.notification.notificationDate,
                    expireDate: this.notification.expireDate,
                });
            }
        });
    }

    /**
     * Validates that the expire date is after the notification date.
     * Sets errors on both date controls if validation fails.
     */
    private validateDates(): null {
        const notificationDateControl = this.form?.get('notificationDate');
        const expireDateControl = this.form?.get('expireDate');
        const notificationDate = notificationDateControl?.value;
        const expireDate = expireDateControl?.value;

        if (!notificationDate || !expireDate || expireDate.isAfter(notificationDate)) {
            // Valid: clear the custom error from both controls
            [notificationDateControl, expireDateControl].forEach((control) => {
                if (control?.errors?.['expireMustBeAfterNotification']) {
                    const errors = { ...control.errors };
                    delete errors['expireMustBeAfterNotification'];
                    const isEmpty = Object.keys(errors).length === 0;
                    control.setErrors(isEmpty ? null : errors);
                }
            });
        } else {
            // Invalid: set custom error on both controls
            [notificationDateControl, expireDateControl].forEach((control) => {
                const errors = { ...(control?.errors ?? {}), expireMustBeAfterNotification: true };
                control?.setErrors(errors);
            });
        }
        return null;
    }

    /**
     * Returns the expire date form control for template access.
     */
    get expireDate(): FormControl<dayjs.Dayjs | null | undefined> | null {
        return this.form.get('expireDate') as FormControl<dayjs.Dayjs | null | undefined> | null;
    }

    /**
     * Navigates back to the system notifications overview.
     */
    goToOverview(): void {
        this.router.navigate(['admin', 'system-notification-management']);
    }

    /**
     * Saves the notification (creates new or updates existing).
     * Navigates to overview on success.
     */
    save(): void {
        this.isSaving.set(true);
        const formValues = this.form.getRawValue();
        const toSave: SystemNotification = {
            ...this.notification,
            id: formValues.id ?? undefined,
            title: formValues.title ?? undefined,
            text: formValues.text ?? undefined,
            type: formValues.type ?? undefined,
            notificationDate: formValues.notificationDate ?? undefined,
            expireDate: formValues.expireDate ?? undefined,
        };

        const saveOperation = this.notification.id ? this.systemNotificationService.update(toSave) : this.systemNotificationService.create(toSave);

        saveOperation.subscribe({
            next: () => this.onSaveSuccess(),
            error: () => this.onSaveError(),
        });
    }

    /**
     * Handles successful save by resetting state and navigating to overview.
     */
    private onSaveSuccess(): void {
        this.isSaving.set(false);
        this.goToOverview();
    }

    /**
     * Handles save error by resetting the saving state.
     */
    private onSaveError(): void {
        this.isSaving.set(false);
    }
}
