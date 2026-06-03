import { Component, input, linkedSignal, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-exercise-update-notification',
    templateUrl: './exercise-update-notification.component.html',
    imports: [FormsModule, ArtemisTranslatePipe],
})
export class ExerciseUpdateNotificationComponent {
    readonly isCreation = input(false);
    readonly isImport = input(false);
    readonly notificationText = input<string | undefined>();
    readonly currentNotificationText = linkedSignal(() => this.notificationText());
    readonly notificationTextChange = output<string>();

    onInputChanged() {
        this.notificationTextChange.emit(this.currentNotificationText()!);
    }
}
