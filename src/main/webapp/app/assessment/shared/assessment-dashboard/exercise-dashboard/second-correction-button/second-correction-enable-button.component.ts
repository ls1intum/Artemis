import { Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiToggleSwitchComponent } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-second-correction-enable-button',
    templateUrl: './second-correction-enable-button.component.html',
    host: { class: 'inline-flex items-center gap-2 align-middle' },
    imports: [ArtemisTranslatePipe, FaIconComponent, FormsModule, TumUiToggleSwitchComponent],
})
export class SecondCorrectionEnableButtonComponent {
    readonly secondCorrectionEnabled = input(false);
    readonly togglingSecondCorrectionButton = input(false);

    readonly ngModelChange = output<void>();

    faSpinner = faSpinner;

    triggerSecondCorrectionButton() {
        this.ngModelChange.emit();
    }
}
