import { Component, EventEmitter, OnInit, Output } from '@angular/core';

export interface NotificationOption {
    type: string;
    status: boolean; //TODO change to String or smth else to make it extendable for emails, etc
}

@Component({
    selector: 'jhi-notification-settings',
    templateUrl: './notification-settings.component.html',
    styleUrls: ['./notification-settings.scss'],
})
export class NotificationSettingsComponent implements OnInit {
    @Output()
    onClose: EventEmitter<boolean> = new EventEmitter();

    optionsChanged: boolean = false;

    constructor() {}

    notificationOptions: Array<NotificationOption>;

    ngOnInit(): void {
        this.notificationOptions = new Array<NotificationOption>(); //TODO look into Map again, maybe I did smth wrong in html
        //TODO this.notificationTypeMap = loadSettingsFromServer();

        //TODO remove TESTING
        this.notificationOptions.push({ type: 'QUIZ_EXERCISE_STARTED', status: true });
        this.notificationOptions.push({ type: 'QUIZ_EXERCISE_RELEASED_FOR_PRACTICE', status: false });
        //TESTING end
    }

    closeSettings() {
        console.log(this.notificationOptions);
        this.onClose.emit(true);
    }

    saveSettings() {
        //TODO Server REST-POST call
        //TODO refresh notifications in notification-sidebar (else outdated, ngOnitnit only called once, i.e. only calls loadnotifications once)
    }

    toggleOption(event: any) {
        this.optionsChanged = true;
        const notificationType = event.currentTarget.id;
        let foundOption = this.notificationOptions.find((option) => option.type === notificationType);
        if (!foundOption) return;
        foundOption!.status = !foundOption!.status;
    }
}
