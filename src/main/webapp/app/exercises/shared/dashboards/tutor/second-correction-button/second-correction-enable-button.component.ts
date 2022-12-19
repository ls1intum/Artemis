import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faSpinner, faToggleOff, faToggleOn } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-second-correction-enable-button',
    templateUrl: './second-correction-enable-button.component.html',
    styles: ['div { cursor: pointer; }'],
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
