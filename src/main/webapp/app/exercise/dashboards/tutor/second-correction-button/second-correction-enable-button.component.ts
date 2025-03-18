import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faSpinner, faToggleOff, faToggleOn } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-second-correction-enable-button',
    templateUrl: './second-correction-enable-button.component.html',
    styles: ['div { cursor: pointer; }'],
    imports: [FaIconComponent, ArtemisTranslatePipe],
})
export class SecondCorrectionEnableButtonComponent {
    @Input() secondCorrectionEnabled: boolean;
    @Input() togglingSecondCorrectionButton: boolean;

    @Output() ngModelChange = new EventEmitter();

    // Icons
    faToggleOn = faToggleOn;
    faToggleOff = faToggleOff;
    faSpinner = faSpinner;

    triggerSecondCorrectionButton() {
        this.ngModelChange.emit();
    }
}
