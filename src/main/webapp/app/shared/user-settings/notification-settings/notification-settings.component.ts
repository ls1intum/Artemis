import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { NotificationService } from 'app/shared/notification/notification.service';
import { defaultNotificationSettings } from 'app/shared/user-settings/notification-settings/notification-settings.default';

export interface NotificationOption {
    id: number;
    type: string;
    app: boolean;
    email: boolean;
    user_id: number; // todo change to user type
}

@Component({
    selector: 'jhi-notification-settings',
    templateUrl: './notification-settings.component.html',
    styleUrls: ['../user-settings.component.scss'],
})
export class NotificationSettingsComponent implements OnInit {
    notificationSettings = defaultNotificationSettings;

    optionsChanged: boolean = false;
    page = 0;
    error?: string;
    notificationOptions: Array<NotificationOption>;

    constructor(private notificationService: NotificationService) {}

    ngOnInit(): void {
        this.notificationOptions = new Array<NotificationOption>(); //TODO look into Map again, maybe I did smth wrong in html
        //TODO remove TESTING
        //this.notificationOptions.push({ type: 'QUIZ_EXERCISE_STARTED',               app: true , email: false, id: 42, user_id: 2 });
        //this.notificationOptions.push({ type: 'QUIZ_EXERCISE_RELEASED_FOR_PRACTICE', app: false, email: false, id: 27, user_id: 2 });
        //TESTING end
        this.loadNotificationOptions();
    }

    saveOptions() {
        //TODO Server REST-POST call
        //TODO refresh notifications in notification-sidebar (else outdated, ngOnitnit only called once, i.e. only calls loadnotifications once)
        /*
        this.notificationService
            .saveNotificationOptions({
                page: this.page, //kp ob nötig
            })
            .subscribe(
                (res: HttpResponse<NotificationOption[]>) => this.loadNotificationOptionsSuccess(res.body!, res.headers),
                (res: HttpErrorResponse) => (this.error = res.message),
            );
            */
    }

    toggleOption(event: any) {
        this.optionsChanged = true;
        const notificationType = event.currentTarget.id;
        let foundOption = this.notificationOptions.find((option) => option.type === notificationType);
        if (!foundOption) return;
        foundOption!.app = !foundOption!.app;
    }

    private loadNotificationOptions(): void {
        this.notificationService
            .queryNotificationOptions({
                page: this.page, //kp ob nötig
            })
            .subscribe(
                (res: HttpResponse<NotificationOption[]>) => this.loadNotificationOptionsSuccess(res.body!, res.headers),
                (res: HttpErrorResponse) => (this.error = res.message),
            );
    }

    private loadNotificationOptionsSuccess(notificationOptions: NotificationOption[], headers: HttpHeaders): void {
        debugger;
        for (let notificationOption in notificationOptions) {
            this.notificationOptions.push(notificationOptions[notificationOption]);
        }
    }

    // Default notification settings

    //TODO
}
