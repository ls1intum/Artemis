import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-exercise-update-notification',
    templateUrl: './exercise-update-notification.component.html',
})
export class ExerciseUpdateNotificationComponent {
    @Input() exercise: Exercise;
    @Input() isImport: boolean;
    @Input() notificationText?: string;
    @Output() notificationTextChange: EventEmitter<string> = new EventEmitter<string>();

    onInputChanged() {
        this.notificationTextChange.emit(this.notificationText);
    }
}
