import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
    selector: 'jhi-exercise-update-notification',
    templateUrl: './exercise-update-notification.component.html',
})
export class ExerciseUpdateNotificationComponent {
    @Input() isCreation: boolean = false;
    @Input() isImport: boolean;
    @Input() notificationText?: string;
    @Output() notificationTextChange: EventEmitter<string> = new EventEmitter<string>();

    onInputChanged() {
        this.notificationTextChange.emit(this.notificationText);
    }
}
