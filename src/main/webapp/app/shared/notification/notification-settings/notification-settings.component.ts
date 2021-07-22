import { Component, EventEmitter, OnInit, Output } from '@angular/core';

interface NotificationOption {
    type: string;
    status: boolean;
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
        this.notificationOptions = new Array<NotificationOption>();
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
    }

    toggleOption(event: any) {
        this.optionsChanged = true;
        const notificationType = event.currentTarget.id;
        let foundOption = this.notificationOptions.find((option) => option.type === notificationType);
        if (!foundOption) return;
        foundOption!.status = !foundOption!.status;
    }
}
