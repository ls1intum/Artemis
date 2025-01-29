import { Component, OnInit, inject } from '@angular/core';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { SystemNotification, SystemNotificationType } from 'app/entities/system-notification.model';
import dayjs from 'dayjs/esm';
import { AdminSystemNotificationService } from 'app/shared/notification/system-notification/admin-system-notification.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-system-notification-management-update',
    templateUrl: './system-notification-management-update.component.html',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, FormDateTimePickerComponent, FaIconComponent, ArtemisTranslatePipe],
})
export class SystemNotificationManagementUpdateComponent implements OnInit {
    private systemNotificationService = inject(AdminSystemNotificationService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);

    notification: SystemNotification;
    isSaving: boolean;

    systemNotificationTypes = [
        { name: 'INFO', value: SystemNotificationType.INFO },
        { name: 'WARNING', value: SystemNotificationType.WARNING },
    ];

    form: FormGroup;

    // Icons
    faSave = faSave;
    faBan = faBan;

    /**
     * Loads notification from route data
     */
    ngOnInit() {
        this.isSaving = false;
        // create a new notification, and only overwrite it if we fetch a notification to edit
        this.notification = new SystemNotification();
        this.route.parent!.data.subscribe(({ notification }) => {
            if (notification) {
                this.notification = notification.body ? notification.body : notification;
            }
        });

        this.form = new FormGroup(
            {
                id: new FormControl(this.notification.id),
                title: new FormControl(this.notification.title, [Validators.required, Validators.minLength(1), Validators.maxLength(50)]),
                text: new FormControl(this.notification.text),
                type: new FormControl(this.notification.type, [Validators.required]),
                notificationDate: new FormControl(this.notification.notificationDate, [Validators.required]),
                expireDate: new FormControl(this.notification.expireDate, [Validators.required]),
            },
            { validators: this.validateDates.bind(this) },
        );
    }

    private validateDates() {
        const notificationDateControl = this.form?.get('notificationDate');
        const expireDateControl = this.form?.get('expireDate');
        const notificationDate = this.form?.get('notificationDate')?.value as dayjs.Dayjs | null;
        const expireDate = this.form?.get('expireDate')?.value as dayjs.Dayjs | null;

        if (!notificationDate || !expireDate || expireDate.isAfter(notificationDate)) {
            [notificationDateControl, expireDateControl].forEach((control) => {
                const errors = { ...(control?.errors ?? {}) };
                delete errors.expireMustBeAfterNotification;
                const isEmpty = Object.keys(errors).length === 0;
                control?.setErrors(isEmpty ? null : errors);
            });
        } else {
            [notificationDateControl, expireDateControl].forEach((control) => {
                const errors = { ...(control?.errors ?? {}), expireMustBeAfterNotification: true };
                control?.setErrors(errors);
            });
        }
    }

    get expireDate() {
        return this.form?.get('expireDate');
    }

    /**
     * Returns to the overview of system notifications
     */
    goToOverview() {
        this.router.navigate(['admin', 'system-notification-management']);
    }

    /**
     * Either creates or updates the notification, when the form is submitted
     */
    save() {
        this.isSaving = true;
        const toSave = {
            ...this.notification,
            ...this.form.getRawValue(),
        };
        if (this.notification.id) {
            this.systemNotificationService.update(toSave).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
        } else {
            this.systemNotificationService.create(toSave).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
        }
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.goToOverview();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
