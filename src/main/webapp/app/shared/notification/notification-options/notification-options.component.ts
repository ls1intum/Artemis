import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { NotificationService } from 'app/shared/notification/notification.service';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';

export interface NotificationOption {
    id: number;
    type: string;
    app: boolean;
    email: boolean;
    user_id: number; // todo change to user type
}

@Component({
    selector: 'jhi-notification-options',
    templateUrl: './notification-options.component.html',
    styleUrls: ['./notification-options.scss'],
})
export class NotificationOptionsComponent implements OnInit {
    @Output()
    onClose: EventEmitter<boolean> = new EventEmitter();
    optionsChanged: boolean = false;
    page = 0;
    error?: string;

    constructor(private notificationService: NotificationService) {}

    notificationOptions: Array<NotificationOption>;

    ngOnInit(): void {
        this.notificationOptions = new Array<NotificationOption>(); //TODO look into Map again, maybe I did smth wrong in html
        //TODO remove TESTING
        //this.notificationOptions.push({ type: 'QUIZ_EXERCISE_STARTED',               app: true , email: false, id: 42, user_id: 2 });
        //this.notificationOptions.push({ type: 'QUIZ_EXERCISE_RELEASED_FOR_PRACTICE', app: false, email: false, id: 27, user_id: 2 });
        //TESTING end
        this.loadNotificationOptions();
    }

    closeOptions() {
        console.log(this.notificationOptions);
        this.onClose.emit(true);
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
        for (let notificationOption in notificationOptions) {
            this.notificationOptions.push(notificationOptions[notificationOption]);
        }
    }
}
