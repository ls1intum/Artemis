import { Component, Input, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-exercise-update-notification',
    templateUrl: './exercise-update-notification.component.html',
    imports: [FormsModule, ArtemisTranslatePipe],
})
export class ExerciseUpdateNotificationComponent {
    readonly isCreation = input(false);
    readonly isImport = input<boolean>(undefined!);
    // TODO: Skipped for migration because:
    //  Your application code writes to the input. This prevents migration.
    @Input() notificationText?: string;
    readonly notificationTextChange = output<string>();

    onInputChanged() {
        this.notificationTextChange.emit(this.notificationText);
    }
}
