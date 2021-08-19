import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { NotificationService } from 'app/shared/notification/notification.service';
import { defaultNotificationSettings } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { SettingsCategory, UserOption } from 'app/shared/user-settings/user-settings.component';

/*
export interface NotificationOption {
    id: number;
    type: string;
    app: boolean;
    email: boolean;
    user_id: number; // todo change to user type
}
 */

@Component({
    selector: 'jhi-notification-settings',
    templateUrl: './notification-settings.component.html',
    styleUrls: ['../user-settings.component.scss'],
})
export class NotificationSettingsComponent implements OnInit {
    notificationSettings: SettingsCategory;
    optionsChanged: boolean = false;
    page = 0;
    error?: string;
    notificationOptions: Array<UserOption>;

    constructor(private notificationService: NotificationService) {}

    ngOnInit(): void {
        this.notificationOptions = new Array<UserOption>();
        //TODO remove TESTING
        //this.notificationOptions.push({ type: 'QUIZ_EXERCISE_STARTED',               app: true , email: false, id: 42, user_id: 2 });
        //this.notificationOptions.push({ type: 'QUIZ_EXERCISE_RELEASED_FOR_PRACTICE', app: false, email: false, id: 27, user_id: 2 });
        //TESTING end
        debugger;
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
        const notificationType = event.currentTarget.id; //TODO
        //let foundOption = this.notificationOptions.find((option) => option.type === notificationType);
        let foundOption = this.notificationOptions.find((option) => option.name === notificationType); //TODO
        if (!foundOption) return;
        foundOption!.webapp = !foundOption!.webapp;
    }

    private loadNotificationOptions(): void {
        this.notificationService
            .queryUserOptions({
                page: this.page, //kp ob nötig
            })
            .subscribe(
                (res: HttpResponse<UserOption[]>) => this.loadNotificationOptionsSuccess(res.body!, res.headers),
                (res: HttpErrorResponse) => (this.error = res.message),
            );
    }

    private loadNotificationOptionsSuccess(notificationOptions: UserOption[], headers: HttpHeaders): void {
        debugger;

        // if no options were loaded -> user has not yet changed options -> use default options
        if (notificationOptions == undefined) {
            this.notificationSettings = defaultNotificationSettings;
            return;
        }

        // else create Option Groups and SettingsCategory from received User Options
        // TODO
        for (let notificationOption in notificationOptions) {
            this.notificationOptions.push(notificationOptions[notificationOption]);
        }
    }

    // Default notification settings

    //TODO
}
